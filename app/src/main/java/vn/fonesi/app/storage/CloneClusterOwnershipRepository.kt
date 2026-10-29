package vn.fonesi.app.storage

import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import java.util.UUID
import java.util.concurrent.Callable

/**
 * Keeps clone metadata and its CLONE owner reference consistent inside one Room transaction.
 *
 * This repository never publishes, activates, deletes, or garbage-collects APK clusters. The public
 * association API reopens the cluster through PrivateApkStore so APK signatures, bytes and split
 * relationships are checked before a database reference can be created.
 */
class CloneClusterOwnershipRepository(
    private val db: FonesiDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Associate a clone with an existing immutable cluster. The cluster must already be published;
     * this method only verifies it and records ownership. It does not install or launch the APK.
     */
    fun associate(
        engineKey: String,
        cloneId: String,
        store: PrivateApkStore,
        clusterId: String,
        platformApi: Int,
    ): Boolean {
        requireCanonicalUuid(clusterId)
        val verified = store.open(clusterId, platformApi)
        check(verified.id == clusterId) { "Private store returned a different cluster ID" }
        return associateReadBackVerified(engineKey, cloneId, verified)
    }

    /** Package-visible test seam; production callers should use associate() to force store readback. */
    internal fun associateReadBackVerified(
        engineKey: String,
        cloneId: String,
        cluster: PrivateApkCluster,
    ): Boolean {
        requireOwnerIdentity(engineKey, cloneId)
        validateCluster(cluster)
        return db.runInTransaction(Callable {
            val clone = checkNotNull(db.clones().find(engineKey, cloneId)) {
                "Clone metadata does not exist"
            }
            check(clone.packageName == cluster.apks.base.packageName) {
                "Private cluster package does not match clone metadata"
            }

            val ownerType = ClusterOwnerType.CLONE.name
            val existing = db.clusterReferences().forOwner(ownerType, cloneId, engineKey)
            when (val currentId = clone.clusterId) {
                null -> check(existing.isEmpty()) {
                    "Clone has CLONE references but no cluster ID; reconciliation is required"
                }
                else -> check(existing.size == 1 && existing.single().clusterId == currentId) {
                    "Clone metadata and CLONE references disagree; refusing to mutate ownership"
                }
            }

            if (clone.clusterId == cluster.id) return@Callable false

            val now = clock()
            require(now >= 0) { "Invalid ownership timestamp" }
            val inserted = db.clusterReferences().insert(
                ClusterReferenceRow(
                    clusterId = cluster.id,
                    ownerType = ownerType,
                    ownerId = cloneId,
                    engineKey = engineKey,
                    createdAtEpochMs = now,
                )
            )
            check(inserted != -1L) { "New cluster owner reference already exists unexpectedly" }

            check(db.clones().associateCluster(engineKey, cloneId, clone.launchToken, cluster.id, now) == 1) {
                "Clone metadata changed while associating private cluster"
            }

            clone.clusterId?.let { oldId ->
                check(db.clusterReferences().delete(oldId, ownerType, cloneId, engineKey) == 1) {
                    "Old cluster reference disappeared during generation switch"
                }
            }
            true
        })
    }

    /**
     * Under the caller's engine-operation lock, reject inconsistent persisted ownership BEFORE
     * attempting any provider-side deletion. The final transaction checks the same invariant again.
     */
    fun preflightDeletion(
        engineKey: String,
        cloneId: String,
        expectedLaunchToken: String,
    ) {
        requireOwnerIdentity(engineKey, cloneId)
        require(expectedLaunchToken.isNotBlank())
        db.runInTransaction(Callable {
            val clone = checkNotNull(db.clones().find(engineKey, cloneId)) {
                "Clone metadata disappeared before deletion preflight"
            }
            check(clone.launchToken == expectedLaunchToken) { "Stale delete request" }
            checkCloneReferences(clone)
            Unit
        })
    }

    /**
     * Call only after the provider has confirmed the clone is absent. Metadata and its CLONE
     * reference are removed atomically; other owners and the private APK bytes are never touched.
     */
    fun deleteMetadataAfterProviderConfirmed(
        engineKey: String,
        cloneId: String,
        expectedLaunchToken: String,
    ) {
        requireOwnerIdentity(engineKey, cloneId)
        require(expectedLaunchToken.isNotBlank())
        db.runInTransaction(Callable {
            val clone = checkNotNull(db.clones().find(engineKey, cloneId)) {
                "Clone metadata disappeared before ownership reconciliation"
            }
            check(clone.launchToken == expectedLaunchToken) { "Stale delete request" }

            val ownerType = checkCloneReferences(clone)

            check(db.clones().delete(engineKey, cloneId) == 1) {
                "Clone metadata could not be deleted atomically"
            }
            clone.clusterId?.let { clusterId ->
                check(db.clusterReferences().delete(clusterId, ownerType, cloneId, engineKey) == 1) {
                    "CLONE reference could not be released atomically"
                }
            }
            Unit
        })
    }

    private fun checkCloneReferences(clone: CloneMetadataRow): String {
        val ownerType = ClusterOwnerType.CLONE.name
        val existing = db.clusterReferences().forOwner(ownerType, clone.cloneId, clone.engineKey)
        when (val currentId = clone.clusterId) {
            null -> check(existing.isEmpty()) {
                "Clone without cluster ID still has CLONE references; preserving metadata"
            }
            else -> check(existing.size == 1 && existing.single().clusterId == currentId) {
                "Clone metadata and CLONE references disagree; preserving metadata"
            }
        }
        return ownerType
    }

    private fun validateCluster(cluster: PrivateApkCluster) {
        requireCanonicalUuid(cluster.id)
        val base = cluster.apks.base
        require(base.splitName == null && !base.feature && base.configFor == null) {
            "Cluster base APK metadata is invalid"
        }
        require(base.packageName.isNotBlank() && base.version >= 0 && base.signers.isNotEmpty())
        val members = listOf(base) + cluster.apks.splits
        require(members.all {
            it.packageName == base.packageName &&
                it.version == base.version &&
                it.signers == base.signers &&
                it.sha256.matches(SHA256)
        }) { "Cluster members do not share the audited package, version and signing identity" }
        val splitNames = cluster.apks.splits.mapNotNull { it.splitName }
        require(splitNames.size == cluster.apks.splits.size && splitNames.distinct().size == splitNames.size) {
            "Cluster split identities are missing or duplicated"
        }
        require(cluster.clusterDigest.matches(SHA256)) { "Invalid deterministic cluster digest" }
    }

    private fun requireCanonicalUuid(value: String) {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            "Invalid private cluster UUID"
        }
    }

    private fun requireOwnerIdentity(engineKey: String, cloneId: String) {
        require(engineKey.isNotBlank() && engineKey.length <= MAX_FIELD)
        require(cloneId.isNotBlank() && cloneId.length <= MAX_FIELD)
    }

    private companion object {
        const val MAX_FIELD = 512
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
