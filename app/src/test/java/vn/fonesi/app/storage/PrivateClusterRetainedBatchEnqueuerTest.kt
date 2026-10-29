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
import vn.fonesi.core.update.TaskState
import vn.fonesi.core.update.UpdateTask
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

/**
 * E44 synthetic metadata/SQLite test seam. Not signed public APK import, a real guest engine,
 * social-account isolation or phone runtime verification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PrivateClusterRetainedBatchEnqueuerTest {
    private lateinit var db: FonesiDatabase
    private lateinit var context: Context
    private lateinit var dbName: String
    private val createdFiles = mutableListOf<File>()
    private val engineKey = "engine-a"
    private val signer = "a".repeat(64)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "e44-batch-" + UUID.randomUUID() + ".db"
        db = FonesiDatabase.open(context, dbName)
    }

    @After fun tearDown() {
        io { db.close() }
        context.deleteDatabase(dbName)
        createdFiles.forEach { it.delete() }
    }

    private fun target(pkg: String, clone: String = "clone-a",
        version: Long = 4L, signers: Set<String> = setOf(signer)) =
        UpdateTarget(InstalledSource(pkg, version, signers, listOf("/untrusted/$pkg.apk")), listOf(clone))

    private fun cluster(pkg: String, version: Long = 4L): PrivateApkCluster {
        val file = File.createTempFile("e44-private-source-", ".apk")
        createdFiles += file
        val bytes = "synthetic E44 snapshot $pkg $version".toByteArray()
        file.writeBytes(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val base = AuditedApk(file, pkg, version, null, false, null, emptySet(), false,
            emptySet(), emptySet(), setOf(signer), sha)
        return PrivateApkCluster(UUID.randomUUID().toString(), AuditedApkSet(base, emptyList()))
    }

    private fun queued(): List<UpdateTask> = io { RoomUpdateTaskStore(db).list() }
    private fun references(task: UpdateTask): List<String> = io {
        db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name, task.id, task.engineKey)
            .map { it.clusterId }
    }

    @Test fun twoSourceBatchIsIdempotentAndSurvivesDatabaseReopen() {
        val alpha = cluster("test.alpha")
        val beta = cluster("test.beta")
        val batch = listOf(target("test.alpha") to alpha, target("test.beta") to beta)
        val writer = PrivateClusterRetainedEnqueuer(db) { 100L }
        val first = io { writer.enqueueBatchReadBackVerified(engineKey, batch, UpdateScope.SHARED_PACKAGE) }
        val again = io { writer.enqueueBatchReadBackVerified(engineKey, batch, UpdateScope.SHARED_PACKAGE) }
        assertEquals(first, again)
        assertEquals(2, queued().size)
        assertEquals(listOf(alpha.id), references(first[0]))
        assertEquals(listOf(beta.id), references(first[1]))
        assertEquals(2, io { db.clusterReferences().list().size })
        io { db.close(); db = FonesiDatabase.open(context, dbName) }
        assertEquals(first, queued())
        assertEquals(listOf(alpha.id), references(first[0]))
        assertEquals(listOf(beta.id), references(first[1]))
    }

    @Test fun secondOwnerSqlAbortRollsBackAllNewTasksAndOwners() {
        val alpha = cluster("test.alpha")
        val beta = cluster("test.beta")
        val batch = listOf(target("test.alpha") to alpha, target("test.beta") to beta)
        val writer = PrivateClusterRetainedEnqueuer(db)
        io {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER e44_batch_ref_abort BEFORE INSERT ON cluster_references " +
                    "WHEN NEW.ownerType = 'UPDATE_TASK' AND NEW.clusterId = '${beta.id}' " +
                    "BEGIN SELECT RAISE(ABORT, 'test refusal'); END")
        }
        try {
            assertThrows(Exception::class.java) {
                io { writer.enqueueBatchReadBackVerified(engineKey, batch, UpdateScope.SHARED_PACKAGE) }
            }
        } finally {
            io { db.openHelper.writableDatabase.execSQL("DROP TRIGGER e44_batch_ref_abort") }
        }
        assertTrue(queued().isEmpty())
        assertTrue(io { db.clusterReferences().list().isEmpty() })
        val success = io { writer.enqueueBatchReadBackVerified(engineKey, batch, UpdateScope.SHARED_PACKAGE) }
        assertEquals(2, success.size)
        assertEquals(2, io { db.clusterReferences().list().size })
    }

    @Test fun secondGenerationConflictRollsBackEarlierNewTarget() {
        val oldBeta = cluster("test.beta")
        val newBeta = cluster("test.beta")
        val alpha = cluster("test.alpha")
        val writer = PrivateClusterRetainedEnqueuer(db)
        val existing = io {
            writer.enqueueReadBackVerified(engineKey, target("test.beta"), UpdateScope.SHARED_PACKAGE, oldBeta)
        }
        assertThrows(IllegalStateException::class.java) {
            io {
                writer.enqueueBatchReadBackVerified(engineKey,
                    listOf(target("test.alpha") to alpha, target("test.beta") to newBeta),
                    UpdateScope.SHARED_PACKAGE)
            }
        }
        assertEquals(listOf(existing), queued())
        assertEquals(listOf(oldBeta.id), references(existing))
        assertTrue(io { db.clusterReferences().forCluster(alpha.id).isEmpty() })
        assertTrue(io { db.clusterReferences().forCluster(newBeta.id).isEmpty() })
    }

    @Test fun secondSourceIdentityMismatchRejectsWholeBatchBeforeMutation() {
        val alpha = cluster("test.alpha")
        val beta = cluster("test.beta")
        val writer = PrivateClusterRetainedEnqueuer(db)
        for (bad in listOf(target("wrong.package"), target("test.beta", version = 5L),
            target("test.beta", signers = setOf("b".repeat(64))))) {
            assertThrows(IllegalArgumentException::class.java) {
                io {
                    writer.enqueueBatchReadBackVerified(engineKey,
                        listOf(target("test.alpha") to alpha, bad to beta), UpdateScope.SHARED_PACKAGE)
                }
            }
        }
        assertTrue(queued().isEmpty())
        assertTrue(io { db.clusterReferences().list().isEmpty() })
    }

    @Test fun duplicateDedupIdentityWithinBatchIsRejectedWithoutWrites() {
        val alpha = cluster("test.alpha")
        val anotherGeneration = cluster("test.alpha")
        val writer = PrivateClusterRetainedEnqueuer(db)
        assertThrows(IllegalArgumentException::class.java) {
            io {
                writer.enqueueBatchReadBackVerified(engineKey,
                    listOf(target("test.alpha") to alpha, target("test.alpha") to anotherGeneration),
                    UpdateScope.SHARED_PACKAGE)
            }
        }
        assertTrue(queued().isEmpty())
        assertTrue(io { db.clusterReferences().list().isEmpty() })
    }

    @Test fun separatePerCloneTasksMaySafelyRetainSameVerifiedCluster() {
        val alpha = cluster("test.alpha")
        val writer = PrivateClusterRetainedEnqueuer(db)
        val tasks = io {
            writer.enqueueBatchReadBackVerified(engineKey,
                listOf(target("test.alpha", "clone-a") to alpha, target("test.alpha", "clone-b") to alpha),
                UpdateScope.PER_CLONE)
        }
        assertEquals(2, tasks.size)
        assertNotEquals(tasks[0].id, tasks[1].id)
        assertEquals(listOf(alpha.id), references(tasks[0]))
        assertEquals(listOf(alpha.id), references(tasks[1]))
        assertEquals(2, io { db.clusterReferences().forCluster(alpha.id).size })
    }

    @Test fun publicBatchCannotUseUnpublishedOrMalformedPrivateCluster() {
        val root = Files.createTempDirectory("fonesi-e44-store-")
        try {
            val store = PrivateApkStore(root)
            val writer = PrivateClusterRetainedEnqueuer(db)
            val valid = UUID.randomUUID().toString()
            val invalid = "../not-a-cluster"
            assertThrows(IllegalArgumentException::class.java) {
                io {
                    writer.enqueueBatch(engineKey, listOf(
                        RetainedClusterTarget(target("test.alpha"), valid),
                        RetainedClusterTarget(target("test.beta"), invalid)),
                        UpdateScope.SHARED_PACKAGE, store, 34)
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                io {
                    writer.enqueueBatch(engineKey, listOf(
                        RetainedClusterTarget(target("test.alpha"), valid)),
                        UpdateScope.SHARED_PACKAGE, store, 34)
                }
            }
            assertTrue(queued().isEmpty())
            assertTrue(io { db.clusterReferences().list().isEmpty() })
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test fun inProgressSecondTaskCannotPartiallyAddFirstTask() {
        val alpha = cluster("test.alpha")
        val beta = cluster("test.beta")
        val writer = PrivateClusterRetainedEnqueuer(db)
        val queuedBeta = io {
            writer.enqueueReadBackVerified(engineKey, target("test.beta"), UpdateScope.SHARED_PACKAGE, beta)
        }
        val preparing = queuedBeta.move(TaskState.PREPARING, 101L)
        assertTrue(io { RoomUpdateTaskStore(db).replace(queuedBeta, preparing) })
        assertThrows(IllegalStateException::class.java) {
            io {
                writer.enqueueBatchReadBackVerified(engineKey,
                    listOf(target("test.alpha") to alpha, target("test.beta") to beta),
                    UpdateScope.SHARED_PACKAGE)
            }
        }
        assertEquals(listOf(preparing), queued())
        assertEquals(listOf(beta.id), references(queuedBeta))
        assertTrue(io { db.clusterReferences().forCluster(alpha.id).isEmpty() })
    }

    private fun <T> io(block: () -> T): T = runBlocking {
        withContext(Dispatchers.IO) { block() }
    }
}
