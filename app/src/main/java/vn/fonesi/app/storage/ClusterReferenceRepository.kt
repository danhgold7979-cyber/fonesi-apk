package vn.fonesi.app.storage

import androidx.room.withTransaction
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.ClusterReference
import java.util.UUID

data class ClusterReferenceReconciliation(
    val referencedButMissing: Set<String>,
    val publishedWithoutReferences: Set<String>,
)

/**
 * Room is the authoritative app-level owner registry. This repository never deletes files; callers
 * must validate a published cluster before acquire and must durably complete owner transitions
 * before release. Cross-store crashes can leave an orphan, which is safer than a dangling live owner.
 */
class ClusterReferenceRepository(
    private val db: FonesiDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun acquire(reference: ClusterReference): Boolean {
        validate(reference)
        return db.withTransaction {
            db.clusterReferences().insert(reference.toRow()) != -1L
        }
    }

    suspend fun release(reference: ClusterReference): Boolean {
        validate(reference)
        return db.withTransaction {
            db.clusterReferences().delete(
                reference.clusterId,
                reference.ownerType.name,
                reference.ownerId,
                reference.engineKey,
            ) > 0
        }
    }

    suspend fun references(clusterId: String): Set<ClusterReference> {
        validateClusterId(clusterId)
        return db.withTransaction {
            db.clusterReferences().forCluster(clusterId).mapTo(linkedSetOf()) { it.toReference() }
        }
    }

    suspend fun referencesForOwner(
        ownerType: ClusterOwnerType,
        ownerId: String,
        engineKey: String,
    ): Set<ClusterReference> {
        validateOwner(ownerId, engineKey)
        return db.withTransaction {
            db.clusterReferences().forOwner(ownerType.name, ownerId, engineKey)
                .mapTo(linkedSetOf(), ClusterReferenceRow::toReference)
        }
    }

    suspend fun referencedClusterIds(): Set<String> = db.withTransaction {
        db.clusterReferences().referencedClusterIds().toSet()
    }

    suspend fun all(): Set<ClusterReference> = db.withTransaction {
        db.clusterReferences().list().mapTo(linkedSetOf(), ClusterReferenceRow::toReference)
    }

    /** Reconciliation is report-only. It never repairs or deletes ambiguous ownership automatically. */
    suspend fun reconcile(publishedClusterIds: Set<String>): ClusterReferenceReconciliation {
        publishedClusterIds.forEach(::validateClusterId)
        val references = all()
        val referenced = references.mapTo(mutableSetOf()) { it.clusterId }
        return ClusterReferenceReconciliation(
            referencedButMissing = referenced - publishedClusterIds,
            publishedWithoutReferences = publishedClusterIds - referenced,
        )
    }

    private fun validate(reference: ClusterReference) {
        validateClusterId(reference.clusterId)
        validateOwner(reference.ownerId, reference.engineKey)
        require(reference.createdAtEpochMs >= 0)
    }

    private fun validateOwner(ownerId: String, engineKey: String) {
        require(ownerId.isNotBlank() && ownerId.length <= MAX_FIELD)
        require(engineKey.isNotBlank() && engineKey.length <= MAX_FIELD)
    }

    private fun validateClusterId(id: String) {
        require(UUID.fromString(id).toString() == id) { "Invalid cluster ID" }
    }

    private fun ClusterReference.toRow() = ClusterReferenceRow(
        clusterId = clusterId,
        ownerType = ownerType.name,
        ownerId = ownerId,
        engineKey = engineKey,
        createdAtEpochMs = createdAtEpochMs.takeIf { it > 0 } ?: clock(),
    )

    private fun ClusterReferenceRow.toReference() = ClusterReference(
        clusterId = clusterId,
        ownerType = runCatching { ClusterOwnerType.valueOf(ownerType) }
            .getOrElse { throw IllegalStateException("Unknown persisted cluster owner type", it) },
        ownerId = ownerId,
        engineKey = engineKey,
        createdAtEpochMs = createdAtEpochMs,
    )

    private companion object {
        const val MAX_FIELD = 512
    }
}
