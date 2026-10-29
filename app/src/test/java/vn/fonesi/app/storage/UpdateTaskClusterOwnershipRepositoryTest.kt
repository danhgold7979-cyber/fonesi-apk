package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.apk.AuditedApk
import vn.fonesi.apk.AuditedApkSet
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.InstalledSource
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget
import vn.fonesi.core.update.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

/** Synthetic native-SQLite ownership tests; never execute or install third-party guest APKs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class UpdateTaskClusterOwnershipRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var dbName: String
    private val tempFiles = mutableListOf<File>()
    private val engine = "engine-a"
    private val signer = "a".repeat(64)
    private val source = InstalledSource("test.social", 4L, setOf(signer), listOf("/synthetic.apk"))

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "e41-journal-" + UUID.randomUUID() + ".db"
        db = FonesiDatabase.open(context, dbName)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
        tempFiles.forEach { it.delete() }
    }

    private fun task(): UpdateTask = UpdateTask.create(
        engine, UpdateTarget(source, listOf("clone-1")), UpdateScope.PER_CLONE, 10L,
    ).also { withContextBlocking { db.tasks().insert(TaskCodec.row(it)) } }

    private fun cluster(version: Long = 4L, pkg: String = "test.social", signers: Set<String> = setOf(signer)): PrivateApkCluster {
        val file = File.createTempFile("fonesi-e41-test-", ".apk")
        tempFiles += file
        val bytes = "synthetic E41 private source $version".toByteArray()
        file.writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val base = AuditedApk(file, pkg, version, null, false, null, emptySet(), false,
            emptySet(), emptySet(), signers, digest)
        return PrivateApkCluster(UUID.randomUUID().toString(), AuditedApkSet(base, emptyList()))
    }

    private fun ref(clusterId: String, owner: ClusterOwnerType, taskId: String, idEngine: String = engine) =
        ClusterReferenceRow(clusterId, owner.name, taskId, idEngine, 20L)

    private fun advanceToReview(task: UpdateTask) {
        val store = RoomUpdateTaskStore(db)
        withContextBlocking {
            val preparing = task.move(TaskState.PREPARING, 11L)
            assertTrue(store.replace(task, preparing))
            val applying = preparing.move(TaskState.APPLYING, 12L,
                affectedIds = listOf("clone-1"), fingerprint = "f".repeat(64))
            assertTrue(store.replace(preparing, applying))
            assertTrue(store.replace(applying, applying.move(TaskState.REVIEW_REQUIRED, 13L, TaskIssue.INTERRUPTED)))
        }
    }

    @Test fun queuedTaskAcquiresOneVerifiedClusterIdempotentlyAndSurvivesReopen() {
        val job = task()
        val verified = cluster()
        val repository = UpdateTaskClusterOwnershipRepository(db) { 50L }
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, job.id, verified) })
        assertFalse(withContextBlocking { repository.associateReadBackVerified(engine, job.id, verified) })
        assertEquals(listOf(verified.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name, job.id, engine).map { it.clusterId }
        })
        withContextBlocking {
            db.close()
            db = FonesiDatabase.open(context, dbName)
        }
        assertEquals(job, withContextBlocking { RoomUpdateTaskStore(db).list().single() })
        assertEquals(listOf(verified.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name, job.id, engine).map { it.clusterId }
        })
    }

    @Test fun unpublishedPrivateClusterAndMalformedUuidCannotAcquireReference() {
        val job = task()
        val root = Files.createTempDirectory("fonesi-e41-store-")
        try {
            val repository = UpdateTaskClusterOwnershipRepository(db)
            val store = PrivateApkStore(root)
            assertThrows(IllegalArgumentException::class.java) {
                withContextBlocking { repository.associate(engine, job.id, store, UUID.randomUUID().toString(), 34) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                withContextBlocking { repository.associate(engine, job.id, store, "../invalid", 34) }
            }
            assertTrue(withContextBlocking { db.clusterReferences().list().isEmpty() })
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test fun taskIdentityVersionSignerAndQueuedStateCannotBeBypassed() {
        val job = task()
        val repository = UpdateTaskClusterOwnershipRepository(db)
        for (bad in listOf(cluster(version = 5L), cluster(pkg = "another.package"),
            cluster(signers = setOf("b".repeat(64))))) {
            assertThrows(IllegalStateException::class.java) {
                withContextBlocking { repository.associateReadBackVerified(engine, job.id, bad) }
            }
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified("wrong-engine", job.id, cluster()) }
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, "missing-task", cluster()) }
        }
        withContextBlocking {
            assertTrue(RoomUpdateTaskStore(db).replace(job, job.move(TaskState.PREPARING, 11L)))
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, job.id, cluster()) }
        }
        assertTrue(withContextBlocking { db.clusterReferences().list().isEmpty() })
    }

    @Test fun ambiguousOrDifferentSourceOwnersFailClosedWithoutReplacement() {
        val job = task()
        val a = cluster()
        val b = cluster()
        val repository = UpdateTaskClusterOwnershipRepository(db)
        withContextBlocking { db.clusterReferences().insert(ref(a.id, ClusterOwnerType.UPDATE_TASK, job.id)) }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, job.id, b) }
        }
        withContextBlocking { db.clusterReferences().insert(ref(b.id, ClusterOwnerType.UPDATE_TASK, job.id)) }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, job.id, a) }
        }
        assertEquals(setOf(a.id, b.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name, job.id, engine)
                .map { it.clusterId }.toSet()
        })
    }

    @Test fun reviewMarkerPromotesOwnerAtomicallyAndRetainsUnrelatedReferences() {
        val job = task()
        val verified = cluster()
        val repository = UpdateTaskClusterOwnershipRepository(db)
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, job.id, verified) })
        withContextBlocking {
            db.clusterReferences().insert(ref(verified.id, ClusterOwnerType.CLONE, "clone-1"))
            db.clusterReferences().insert(ref(verified.id, ClusterOwnerType.ROLLBACK, "rollback-1"))
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.promoteAfterReview(engine, job.id) }
        }
        advanceToReview(job)
        assertTrue(withContextBlocking { repository.promoteAfterReview(engine, job.id) })
        assertFalse(withContextBlocking { repository.promoteAfterReview(engine, job.id) })
        assertEquals(setOf(ClusterOwnerType.CLONE.name, ClusterOwnerType.REVIEW_REQUIRED.name,
            ClusterOwnerType.ROLLBACK.name), withContextBlocking {
            db.clusterReferences().forCluster(verified.id).map { it.ownerType }.toSet()
        })
        withContextBlocking { db.close(); db = FonesiDatabase.open(context, dbName) }
        assertEquals(listOf(verified.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.REVIEW_REQUIRED.name, job.id, engine)
                .map { it.clusterId }
        })
    }

    @Test fun reviewPromotionSQLiteAbortRollsBackWithoutDroppingTaskOwner() {
        val job = task()
        val verified = cluster()
        val repository = UpdateTaskClusterOwnershipRepository(db)
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, job.id, verified) })
        advanceToReview(job)
        withContextBlocking {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER e41_fail_review BEFORE INSERT ON cluster_references " +
                    "WHEN NEW.ownerType = 'REVIEW_REQUIRED' BEGIN SELECT RAISE(ABORT, 'synthetic SQLite failure'); END",
            )
        }
        try {
            assertThrows(Exception::class.java) {
                withContextBlocking { repository.promoteAfterReview(engine, job.id) }
            }
        } finally {
            withContextBlocking { db.openHelper.writableDatabase.execSQL("DROP TRIGGER e41_fail_review") }
        }
        assertEquals(listOf(verified.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name, job.id, engine).map { it.clusterId }
        })
        assertTrue(withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.REVIEW_REQUIRED.name, job.id, engine).isEmpty()
        })
    }

    @Test fun onlyTerminalJournalMayReleaseTaskWithoutTouchingReviewOrRollback() {
        val job = task()
        val verified = cluster()
        val repository = UpdateTaskClusterOwnershipRepository(db)
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, job.id, verified) })
        withContextBlocking {
            db.clusterReferences().insert(ref(verified.id, ClusterOwnerType.REVIEW_REQUIRED, "review-1"))
            db.clusterReferences().insert(ref(verified.id, ClusterOwnerType.ROLLBACK, "rollback-1"))
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.releaseAfterTerminal(engine, job.id) }
        }
        withContextBlocking {
            assertTrue(RoomUpdateTaskStore(db).replace(job, job.move(TaskState.CANCELLED, 20L)))
        }
        assertTrue(withContextBlocking { repository.releaseAfterTerminal(engine, job.id) })
        assertFalse(withContextBlocking { repository.releaseAfterTerminal(engine, job.id) })
        assertEquals(setOf(ClusterOwnerType.REVIEW_REQUIRED.name, ClusterOwnerType.ROLLBACK.name),
            withContextBlocking { db.clusterReferences().forCluster(verified.id).map { it.ownerType }.toSet() })
    }

    private fun <T> withContextBlocking(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }
}
