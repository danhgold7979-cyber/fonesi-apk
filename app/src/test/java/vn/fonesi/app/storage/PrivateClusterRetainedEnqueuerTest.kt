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

/** Synthetic metadata test seam, not a real signed APK, social-app import or engine execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PrivateClusterRetainedEnqueuerTest {
    private lateinit var db: FonesiDatabase
    private lateinit var context: Context
    private lateinit var dbName: String
    private val createdFiles = mutableListOf<File>()
    private val engineKey = "engine-a"
    private val packageName = "test.social"
    private val signer = "a".repeat(64)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "e43-enqueue-" + UUID.randomUUID() + ".db"
        db = FonesiDatabase.open(context, dbName)
    }

    @After fun tearDown() {
        io { db.close() }
        context.deleteDatabase(dbName)
        createdFiles.forEach { it.delete() }
    }

    private fun target(pkg: String = packageName, version: Long = 4L,
        signers: Set<String> = setOf(signer), ids: List<String> = listOf("clone-1")) =
        UpdateTarget(InstalledSource(pkg, version, signers, listOf("/untrusted-source.apk")), ids)

    private fun cluster(version: Long = 4L, pkg: String = packageName,
        signers: Set<String> = setOf(signer)): PrivateApkCluster {
        val file = File.createTempFile("e43-private-source-", ".apk")
        createdFiles += file
        val bytes = "synthetic E43 snapshot $pkg $version".toByteArray()
        file.writeBytes(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val base = AuditedApk(file, pkg, version, null, false, null, emptySet(), false,
            emptySet(), emptySet(), signers, sha)
        return PrivateApkCluster(UUID.randomUUID().toString(), AuditedApkSet(base, emptyList()))
    }

    private fun refs(task: UpdateTask): List<String> = io {
        db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name, task.id, task.engineKey)
            .map { it.clusterId }
    }

    @Test fun queuedTaskAndOwnerAreAtomicIdempotentAndDurableAcrossReopen() {
        val verified = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db) { 100L }
        val first = io {
            enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified)
        }
        val repeated = io {
            enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified)
        }
        assertEquals(first.id, repeated.id)
        assertEquals(TaskState.QUEUED, first.state)
        assertEquals(1, io { db.tasks().list().size })
        assertEquals(listOf(verified.id), refs(first))
        assertEquals(listOf("/untrusted-source.apk"), target().source.apkPaths)
        assertEquals(verified.source().packageName, first.packageName)
        io { db.close(); db = FonesiDatabase.open(context, dbName) }
        assertEquals(first, io { RoomUpdateTaskStore(db).list().single() })
        assertEquals(listOf(verified.id), refs(first))
    }

    @Test fun mismatchedRequestedSourceIdentityCannotCreateTaskOrOwner() {
        val verified = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db)
        for (bad in listOf(target(pkg = "wrong.package"), target(version = 5L),
            target(signers = setOf("b".repeat(64))))) {
            assertThrows(IllegalArgumentException::class.java) {
                io { enqueuer.enqueueReadBackVerified(engineKey, bad, UpdateScope.PER_CLONE, verified) }
            }
        }
        assertTrue(io { db.tasks().list().isEmpty() && db.clusterReferences().list().isEmpty() })
    }

    @Test fun publicEnqueueRejectsUnpublishedOrMalformedClusterBeforeDatabaseMutation() {
        val root = Files.createTempDirectory("fonesi-e43-store-")
        try {
            val store = PrivateApkStore(root)
            val enqueuer = PrivateClusterRetainedEnqueuer(db)
            assertThrows(IllegalArgumentException::class.java) {
                io { enqueuer.enqueue(engineKey, target(), UpdateScope.PER_CLONE,
                    store, "../missing", 34) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                io { enqueuer.enqueue(engineKey, target(), UpdateScope.PER_CLONE,
                    store, UUID.randomUUID().toString(), 34) }
            }
            assertTrue(io { db.tasks().list().isEmpty() && db.clusterReferences().list().isEmpty() })
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test fun conflictingClusterGenerationCannotReplaceExistingQueuedSource() {
        val firstCluster = cluster()
        val secondCluster = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db)
        val existing = io {
            enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, firstCluster)
        }
        assertThrows(IllegalStateException::class.java) {
            io { enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, secondCluster) }
        }
        assertEquals(1, io { db.tasks().list().size })
        assertEquals(listOf(firstCluster.id), refs(existing))
        assertTrue(io { db.clusterReferences().forCluster(secondCluster.id).isEmpty() })
    }

    @Test fun existingInProgressTaskCannotAcquireOrSwitchOwner() {
        val verified = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db)
        val existing = io {
            enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified)
        }
        val preparing = existing.move(TaskState.PREPARING, 101L)
        assertTrue(io { RoomUpdateTaskStore(db).replace(existing, preparing) })
        assertThrows(IllegalStateException::class.java) {
            io { enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified) }
        }
        assertEquals(preparing, io { RoomUpdateTaskStore(db).list().single() })
        assertEquals(listOf(verified.id), refs(existing))
    }

    @Test fun sqliteReferenceInsertionFailureRollsBackNewQueueRowAndRetriesCleanly() {
        val verified = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db)
        io {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER e43_ref_insert_abort BEFORE INSERT ON cluster_references " +
                    "WHEN NEW.ownerType = 'UPDATE_TASK' BEGIN SELECT RAISE(ABORT, 'test refusal'); END")
        }
        try {
            assertThrows(Exception::class.java) {
                io { enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified) }
            }
        } finally {
            io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER e43_ref_insert_abort") }
        }
        assertTrue(io { db.tasks().list().isEmpty() })
        assertTrue(io { db.clusterReferences().list().isEmpty() })
        val created = io {
            enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified)
        }
        assertEquals(listOf(verified.id), refs(created))
    }

    @Test fun queuedTaskWithUnexpectedReviewOwnerFailsClosed() {
        val verified = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db)
        val raw = UpdateTask.create(engineKey, target(), UpdateScope.PER_CLONE, 100L)
        io {
            db.tasks().insert(TaskCodec.row(raw))
            db.clusterReferences().insert(ClusterReferenceRow(
                UUID.randomUUID().toString(), ClusterOwnerType.REVIEW_REQUIRED.name,
                raw.id, engineKey, 10L))
        }
        assertThrows(IllegalStateException::class.java) {
            io { enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified) }
        }
        assertEquals(raw, io { RoomUpdateTaskStore(db).list().single() })
        assertTrue(refs(raw).isEmpty())
    }

    @Test fun scopeAndEngineNamespaceAreVerifiedWithoutCreatingPartialOwner() {
        val verified = cluster()
        val enqueuer = PrivateClusterRetainedEnqueuer(db)
        assertThrows(IllegalArgumentException::class.java) {
            io { enqueuer.enqueueReadBackVerified(" ", target(), UpdateScope.PER_CLONE, verified) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            io { enqueuer.enqueueReadBackVerified(engineKey,
                target(ids = listOf("clone-a", "clone-b")), UpdateScope.PER_CLONE, verified) }
        }
        assertTrue(io { db.tasks().list().isEmpty() && db.clusterReferences().list().isEmpty() })

        val own = io { enqueuer.enqueueReadBackVerified(engineKey, target(), UpdateScope.PER_CLONE, verified) }
        val other = io { enqueuer.enqueueReadBackVerified("engine-b", target(), UpdateScope.PER_CLONE, verified) }
        assertNotEquals(own.id, other.id)
        assertEquals(2, io { db.tasks().list().size })
        assertEquals(listOf(verified.id), refs(own))
        assertEquals(listOf(verified.id), refs(other))
    }

    private fun <T> io(block: () -> T): T = runBlocking {
        withContext(Dispatchers.IO) { block() }
    }
}
