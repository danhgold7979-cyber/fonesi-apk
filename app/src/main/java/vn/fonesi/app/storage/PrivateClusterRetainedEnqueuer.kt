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

/** One explicitly selected, already published private source for a queued update target. */
data class RetainedClusterTarget(val target: UpdateTarget, val clusterId: String)

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

    /**
     * Atomically retain a selected batch of previously published, signed private APK sources.
     * Every cluster is read back before the first Room write. This API is opt-in and is NOT
     * wired to the normal update UI or provider runner.
     */
    fun enqueueBatch(
        engineKey: String,
        targets: List<RetainedClusterTarget>,
        scope: UpdateScope,
        store: PrivateApkStore,
        platformApi: Int,
    ): List<UpdateTask> {
        requireOwner(engineKey)
        require(targets.isNotEmpty()) { "Cannot enqueue an empty private-cluster batch" }
        targets.forEach { requireCanonicalUuid(it.clusterId) }
        val verified = targets.map { request ->
            val cluster = store.open(request.clusterId, platformApi)
            check(cluster.id == request.clusterId) { "Private store returned a different batch cluster ID" }
            request.target to cluster
        }
        return enqueueBatchReadBackVerified(engineKey, verified, scope)
    }

    /**
     * Internal authenticated-source seam for tests; external callers MUST use enqueueBatch()
     * to cryptographically read back each previously published private cluster.
     */
    internal fun enqueueBatchReadBackVerified(
        engineKey: String,
        targets: List<Pair<UpdateTarget, PrivateApkCluster>>,
        scope: UpdateScope,
    ): List<UpdateTask> {
        requireOwner(engineKey)
        require(targets.isNotEmpty()) { "Cannot enqueue an empty private-cluster batch" }
        // Validate and copy the entire request BEFORE starting the transaction. Do not retain
        // original-app paths, accept a different signer/version, or enqueue duplicate dedup keys.
        val prepared = targets.map { (target, cluster) ->
            requireCanonicalUuid(cluster.id)
            val source = cluster.source()
            require(target.source.packageName == source.packageName &&
                target.source.versionCode == source.versionCode &&
                target.source.signerDigests == source.signerDigests) {
                "Requested batch source does not match the verified private cluster"
            }
            UpdateTarget(source, target.affectedCloneIds.toList()) to cluster
        }
        val dedupKeys = prepared.map { (target, _) ->
            UpdateTask.create(engineKey, target, scope, 0L).dedupKey
        }
        require(dedupKeys.distinct().size == dedupKeys.size) {
            "Duplicate update identity within one retained batch"
        }

        // Existing E43 single-target operations nest inside one outer Room transaction.
        // If any later task or owner fails, *all new task and reference rows* roll back.
        return db.runInTransaction(Callable {
            prepared.map { (target, cluster) ->
                enqueueReadBackVerified(engineKey, target, scope, cluster)
            }
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
