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

    private fun <T> withContextBlocking(block: () -> T): T =
        kotlinx.coroutines.runBlocking { withContext(Dispatchers.IO) { block() } }
}
