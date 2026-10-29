package vn.fonesi.app.storage

import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget
import vn.fonesi.core.update.TaskState
import vn.fonesi.core.update.UpdateQueue
import vn.fonesi.core.update.UpdateTask
import java.util.UUID
import java.util.concurrent.Callable

/**
 * Offline-only atomic task enqueue + source ownership, for a previously published private APK set.
 *
 * The public path always reads back signed private bytes through PrivateApkStore.open before the
 * Room write. No APK is installed, activated, imported, launched or deleted here. This is not yet
 * connected to the production UpdateRequestService or the virtualization provider.
 */
class PrivateClusterRetainedEnqueuer(
    private val db: FonesiDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun enqueue(
        engineKey: String,
        target: UpdateTarget,
        scope: UpdateScope,
        store: PrivateApkStore,
        clusterId: String,
        platformApi: Int,
    ): UpdateTask {
        requireOwner(engineKey)
        requireCanonicalUuid(clusterId)
        val verified = store.open(clusterId, platformApi)
        check(verified.id == clusterId) { "Private store returned a different cluster ID" }
        return enqueueReadBackVerified(engineKey, target, scope, verified)
    }

    /** Test-only seam; real callers must use enqueue() for cryptographic private-store readback. */
    internal fun enqueueReadBackVerified(
        engineKey: String,
        target: UpdateTarget,
        scope: UpdateScope,
        cluster: PrivateApkCluster,
    ): UpdateTask {
        requireOwner(engineKey)
        requireCanonicalUuid(cluster.id)
        val verifiedSource = cluster.source()
        require(target.source.packageName == verifiedSource.packageName &&
            target.source.versionCode == verifiedSource.versionCode &&
            target.source.signerDigests == verifiedSource.signerDigests) {
            "Requested update source does not match a readback-verified private cluster"
        }
        // Always use the private verified source, never the caller's source APK file paths.
        val verifiedTarget = UpdateTarget(verifiedSource, target.affectedCloneIds.toList())
        return db.runInTransaction(Callable {
            val task = UpdateQueue(RoomUpdateTaskStore(db), clock)
                .enqueue(engineKey, listOf(verifiedTarget), scope).single()
            check(task.state == TaskState.QUEUED &&
                task.engineKey == engineKey &&
                task.packageName == verifiedSource.packageName &&
                task.versionCode == verifiedSource.versionCode &&
                task.signerDigests == verifiedSource.signerDigests) {
                "Active task is no longer safely queued or has changed identity"
            }
            check(db.clusterReferences().forOwner(
                ClusterOwnerType.REVIEW_REQUIRED.name, task.id, engineKey,
            ).isEmpty()) { "Queued task already has a review owner; do not silently reconcile" }

            // Nested Room transaction composes with outer enqueue transaction. If the owner insert
            // fails or conflicts, the *new queue task* is rolled back with the reference operation.
            UpdateTaskClusterOwnershipRepository(db, clock)
                .associateReadBackVerified(engineKey, task.id, cluster)
            task
        })
    }

    private fun requireOwner(engineKey: String) {
        require(engineKey.isNotBlank() && engineKey.length <= 512) {
            "Invalid update engine namespace"
        }
    }

    private fun requireCanonicalUuid(value: String) {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            "Invalid private cluster UUID"
        }
    }
}
