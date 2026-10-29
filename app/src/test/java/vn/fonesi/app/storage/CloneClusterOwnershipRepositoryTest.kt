package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.apk.AuditedApk
import vn.fonesi.apk.AuditedApkSet
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.ClusterReference
import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CloneClusterOwnershipRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private val engine = "engine-a"
    private val cloneId = "clone-1"
    private val packageName = "test.social"
    private val token = "launch-token-1"
    private val temporaryFiles = mutableListOf<File>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        name = "clone-cluster-" + UUID.randomUUID() + ".db"
        db = FonesiDatabase.open(context, name)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(name)
        temporaryFiles.forEach { it.delete() }
    }

    private fun cloneRow() = CloneMetadataRow(
        engineKey = engine,
        cloneId = cloneId,
        packageName = packageName,
        label = "Test clone",
        groupName = null,
        createdAt = 1L,
        changedAt = 1L,
        launchToken = token,
    )

    private fun cluster(version: Long = 1L): PrivateApkCluster {
        val file = File.createTempFile("fonesi-cluster-test-", ".apk")
        temporaryFiles += file
        val bytes = "test-only cluster fixture $version".toByteArray()
        file.writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val signer = "a".repeat(64)
        val base = AuditedApk(
            file = file,
            packageName = packageName,
            version = version,
            splitName = null,
            feature = false,
            configFor = null,
            dependencies = emptySet(),
            splitRequired = false,
            requiredTypes = emptySet(),
            providedTypes = emptySet(),
            signers = setOf(signer),
            sha256 = digest,
        )
        return PrivateApkCluster(UUID.randomUUID().toString(), AuditedApkSet(base, emptyList()))
    }

    private fun reference(clusterId: String, ownerType: ClusterOwnerType, ownerId: String, time: Long = 1L) =
        ClusterReferenceRow(clusterId, ownerType.name, ownerId, engine, time)

    @Test fun associationIsIdempotentAndGenerationSwitchRetainsOtherOwners() {
        val old = cluster()
        val next = cluster(version = 2L)
        withContextBlocking { db.clones().insert(cloneRow()) }
        val taskRef = reference(old.id, ClusterOwnerType.UPDATE_TASK, "task-1", 10L)
        withContextBlocking { db.clusterReferences().insert(taskRef) }
        val repository = CloneClusterOwnershipRepository(db) { 100L }

        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, cloneId, old) })
        assertFalse(withContextBlocking { repository.associateReadBackVerified(engine, cloneId, old) })
        assertEquals(old.id, withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, cloneId, next) })

        assertEquals(next.id, withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        val refs = withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.CLONE.name, cloneId, engine)
        }
        assertEquals(listOf(next.id), refs.map { it.clusterId })
        assertEquals(listOf(ClusterOwnerType.UPDATE_TASK.name), withContextBlocking {
            db.clusterReferences().forCluster(old.id).map { it.ownerType }
        })
    }

    @Test fun providerConfirmedDeleteAtomicallyRemovesMetadataAndOnlyCloneOwner() {
        val cluster = cluster()
        withContextBlocking { db.clones().insert(cloneRow()) }
        val repository = CloneClusterOwnershipRepository(db) { 200L }
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, cloneId, cluster) })
        withContextBlocking {
            db.clusterReferences().insert(reference(cluster.id, ClusterOwnerType.ROLLBACK, "rollback-1", 201L))
        }

        withContextBlocking { repository.deleteMetadataAfterProviderConfirmed(engine, cloneId, token) }

        assertNull(withContextBlocking { db.clones().find(engine, cloneId) })
        assertEquals(
            listOf(ClusterOwnerType.ROLLBACK.name),
            withContextBlocking { db.clusterReferences().forCluster(cluster.id).map { it.ownerType } },
        )
    }

    @Test fun staleDeleteTokenPreservesMetadataAndReference() {
        val cluster = cluster()
        withContextBlocking { db.clones().insert(cloneRow()) }
        val repository = CloneClusterOwnershipRepository(db)
        withContextBlocking { repository.associateReadBackVerified(engine, cloneId, cluster) }

        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.deleteMetadataAfterProviderConfirmed(engine, cloneId, "stale-token") }
        }

        assertEquals(cluster.id, withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        assertEquals(1, withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.CLONE.name, cloneId, engine).size
        })
    }

    @Test fun missingCloneOrPackageMismatchCannotAcquireReference() {
        val cluster = cluster()
        val repository = CloneClusterOwnershipRepository(db)
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, cloneId, cluster) }
        }
        withContextBlocking { db.clones().insert(cloneRow()) }
        val wrongPackageBase = cluster.apks.base.copy(packageName = "another.package")
        val wrongPackage = PrivateApkCluster(cluster.id, AuditedApkSet(wrongPackageBase, emptyList()))
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, cloneId, wrongPackage) }
        }
        assertTrue(withContextBlocking { db.clusterReferences().list().isEmpty() })
        assertNull(withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
    }

    @Test fun inconsistentNullMetadataWithCloneReferenceFailsClosed() {
        val cluster = cluster()
        withContextBlocking {
            db.clones().insert(cloneRow())
            db.clusterReferences().insert(reference(cluster.id, ClusterOwnerType.CLONE, cloneId))
        }
        val repository = CloneClusterOwnershipRepository(db)
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, cloneId, cluster) }
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.deleteMetadataAfterProviderConfirmed(engine, cloneId, token) }
        }
        assertNull(withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        assertEquals(1, withContextBlocking { db.clusterReferences().forCluster(cluster.id).size })
    }

    @Test fun inconsistentMultipleCloneOwnersRejectSwitchAndDeleteWithoutLosingData() {
        val old = cluster()
        val conflict = cluster(version = 2L)
        val replacement = cluster(version = 3L)
        withContextBlocking { db.clones().insert(cloneRow()) }
        val repository = CloneClusterOwnershipRepository(db)
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, cloneId, old) })
        withContextBlocking {
            db.clusterReferences().insert(reference(conflict.id, ClusterOwnerType.CLONE, cloneId))
            db.clusterReferences().insert(reference(conflict.id, ClusterOwnerType.REVIEW_REQUIRED, "task-review"))
        }

        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, cloneId, replacement) }
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.deleteMetadataAfterProviderConfirmed(engine, cloneId, token) }
        }

        assertEquals(old.id, withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        assertEquals(setOf(old.id, conflict.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.CLONE.name, cloneId, engine)
                .map { it.clusterId }.toSet()
        })
        assertTrue(withContextBlocking {
            db.clusterReferences().forCluster(replacement.id).isEmpty()
        })
        assertEquals(1, withContextBlocking {
            db.clusterReferences().forCluster(conflict.id).count {
                it.ownerType == ClusterOwnerType.REVIEW_REQUIRED.name
            }
        })
    }

    @Test fun committedAssociationSurvivesDatabaseReopenAndKeepsLaunchToken() {
        val verified = cluster()
        withContextBlocking { db.clones().insert(cloneRow()) }
        val repository = CloneClusterOwnershipRepository(db)
        assertTrue(withContextBlocking { repository.associateReadBackVerified(engine, cloneId, verified) })
        withContextBlocking {
            db.close()
            db = FonesiDatabase.open(context, name)
        }
        val saved = withContextBlocking { db.clones().find(engine, cloneId) }
        assertEquals(verified.id, saved?.clusterId)
        assertEquals(token, saved?.launchToken)
        assertEquals(listOf(verified.id), withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.CLONE.name, cloneId, engine)
                .map { it.clusterId }
        })
        assertFalse(withContextBlocking {
            CloneClusterOwnershipRepository(db).associateReadBackVerified(engine, cloneId, verified)
        })
    }

    @Test fun unpublishedOrMalformedPrivateClusterIsRejectedBeforeRoomMutation() {
        withContextBlocking { db.clones().insert(cloneRow()) }
        val root = java.nio.file.Files.createTempDirectory("fonesi-e40-unpublished-")
        try {
            val store = PrivateApkStore(root)
            val repository = CloneClusterOwnershipRepository(db)
            assertThrows(IllegalArgumentException::class.java) {
                withContextBlocking {
                    repository.associate(engine, cloneId, store, UUID.randomUUID().toString(), 34)
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                withContextBlocking { repository.associate(engine, cloneId, store, "../escape", 34) }
            }
            assertNull(withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
            assertTrue(withContextBlocking { db.clusterReferences().list().isEmpty() })
        } finally {
            java.nio.file.Files.deleteIfExists(root)
        }
    }

    @Test fun wrongEngineOrCloneNamespaceCannotAcquirePrivateCluster() {
        withContextBlocking { db.clones().insert(cloneRow()) }
        val verified = cluster()
        val repository = CloneClusterOwnershipRepository(db)
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified("engine-b", cloneId, verified) }
        }
        assertThrows(IllegalStateException::class.java) {
            withContextBlocking { repository.associateReadBackVerified(engine, "missing-clone", verified) }
        }
        assertNull(withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        assertTrue(withContextBlocking { db.clusterReferences().list().isEmpty() })
    }

    @Test fun sqliteAbortDuringAssociationRollsBackNewOwnerReference() {
        withContextBlocking { db.clones().insert(cloneRow()) }
        val verified = cluster()
        val repository = CloneClusterOwnershipRepository(db)
        withContextBlocking {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER e40_abort_cluster_update BEFORE UPDATE OF clusterId ON clone_metadata " +
                    "BEGIN SELECT RAISE(ABORT, 'synthetic SQLite failure'); END",
            )
        }
        try {
            assertThrows(Exception::class.java) {
                withContextBlocking { repository.associateReadBackVerified(engine, cloneId, verified) }
            }
        } finally {
            withContextBlocking { db.openHelper.writableDatabase.execSQL("DROP TRIGGER e40_abort_cluster_update") }
        }
        assertNull(withContextBlocking { db.clones().find(engine, cloneId)?.clusterId })
        assertTrue(withContextBlocking {
            db.clusterReferences().forOwner(ClusterOwnerType.CLONE.name, cloneId, engine).isEmpty()
        })
    }

    private fun <T> withContextBlocking(block: () -> T): T =
        kotlinx.coroutines.runBlocking { withContext(Dispatchers.IO) { block() } }
}
