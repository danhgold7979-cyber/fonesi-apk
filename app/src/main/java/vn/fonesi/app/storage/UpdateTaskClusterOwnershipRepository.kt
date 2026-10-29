package vn.fonesi.app.storage

import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.update.TaskState
import vn.fonesi.core.update.UpdateTask
import java.util.UUID
import java.util.concurrent.Callable

/**
 * Room-only retention of a previously verified private source image for one durable update task.
 * Never installs, activates, upgrades, garbage-collects or deletes package bytes.
 *
 * UPDATE_TASK protects the source while queued/applying; REVIEW_REQUIRED protects an unresolved
 * engine outcome. Other owner types must never be modified by task transitions.
 */
class UpdateTaskClusterOwnershipRepository(
    private val db: FonesiDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Read back the complete signed cluster before creating any app-level retention reference. */
    fun associate(
        engineKey: String,
        taskId: String,
        store: PrivateApkStore,
        clusterId: String,
        platformApi: Int,
    ): Boolean {
        requireOwner(engineKey, taskId)
        requireCanonicalUuid(clusterId)
        val verified = store.open(clusterId, platformApi)
        check(verified.id == clusterId) { "Private store returned an unexpected cluster ID" }
        return associateReadBackVerified(engineKey, taskId, verified)
    }

    /** Test-only seam: production callers must use associate() for cryptographic store readback. */
    internal fun associateReadBackVerified(
        engineKey: String,
        taskId: String,
        cluster: PrivateApkCluster,
    ): Boolean {
        requireOwner(engineKey, taskId)
        validateCluster(cluster)
        return db.runInTransaction(Callable {
            val task = readTask(engineKey, taskId)
            check(task.state == TaskState.QUEUED) { "Only a queued task may acquire a new source owner" }
            val base = cluster.apks.base
            check(task.packageName == base.packageName &&
                task.versionCode == base.version &&
                task.signerDigests == base.signers) {
                "Private cluster identity does not match immutable update task source"
            }
            val type = ClusterOwnerType.UPDATE_TASK.name
            val refs = db.clusterReferences().forOwner(type, taskId, engineKey)
            check(refs.size <= 1) { "Ambiguous UPDATE_TASK ownership; manual reconciliation required" }
            if (refs.isNotEmpty()) {
                check(refs.single().clusterId == cluster.id) {
                    "Task already owns a different cluster; refusing silent generation replacement"
                }
                return@Callable false
            }
            val now = clock()
            require(now >= 0) { "Invalid ownership timestamp" }
            check(db.clusterReferences().insert(ClusterReferenceRow(
                cluster.id, type, taskId, engineKey, now,
            )) != -1L) { "Duplicate UPDATE_TASK owner unexpectedly inserted" }
            true
        })
    }

    /** Review state must be durably committed before promoting the source to a review hold. */
    fun promoteAfterReview(engineKey: String, taskId: String): Boolean {
        requireOwner(engineKey, taskId)
        return db.runInTransaction(Callable {
            val task = readTask(engineKey, taskId)
            check(task.state == TaskState.REVIEW_REQUIRED) { "No durable review marker" }
            val taskType = ClusterOwnerType.UPDATE_TASK.name
            val reviewType = ClusterOwnerType.REVIEW_REQUIRED.name
            val owned = db.clusterReferences().forOwner(taskType, taskId, engineKey)
            val review = db.clusterReferences().forOwner(reviewType, taskId, engineKey)
            check(owned.size <= 1 && review.size <= 1) {
                "Ambiguous update or review reference; preserving all owners"
            }
            val source = owned.singleOrNull() ?: return@Callable false
            if (review.isNotEmpty()) {
                check(review.single().clusterId == source.clusterId) {
                    "Conflicting review generation; refusing to drop task reference"
                }
            } else {
                val now = clock()
                require(now >= 0) { "Invalid ownership timestamp" }
                check(db.clusterReferences().insert(ClusterReferenceRow(
                    source.clusterId, reviewType, taskId, engineKey, now,
                )) != -1L) { "Review reference already exists unexpectedly" }
            }
            check(db.clusterReferences().delete(source.clusterId, taskType, taskId, engineKey) == 1) {
                "Source task reference disappeared during review promotion"
            }
            true
        })
    }

    /** Only after the durable journal is terminal may the UPDATE_TASK owner be released. */
    fun releaseAfterTerminal(engineKey: String, taskId: String): Boolean {
        requireOwner(engineKey, taskId)
        return db.runInTransaction(Callable {
            val task = readTask(engineKey, taskId)
            check(task.state.terminal) { "Nonterminal or unknown update result must retain its source" }
            val type = ClusterOwnerType.UPDATE_TASK.name
            val refs = db.clusterReferences().forOwner(type, taskId, engineKey)
            check(refs.size <= 1) { "Ambiguous UPDATE_TASK references; preserving data" }
            val ref = refs.singleOrNull() ?: return@Callable false
            check(db.clusterReferences().delete(ref.clusterId, type, taskId, engineKey) == 1) {
                "UPDATE_TASK owner disappeared during release"
            }
            true
        })
    }

    private fun readTask(engineKey: String, taskId: String): UpdateTask {
        val row = checkNotNull(db.tasks().find(taskId)) { "Update task not found" }
        val task = TaskCodec.decode(row)
        check(task.engineKey == engineKey && task.id == taskId) {
            "Update task belongs to a different engine or has corrupt identity"
        }
        return task
    }

    private fun validateCluster(cluster: PrivateApkCluster) {
        requireCanonicalUuid(cluster.id)
        val base = cluster.apks.base
        require(base.splitName == null && !base.feature && base.configFor == null)
        require(base.packageName.isNotBlank() && base.version >= 0 && base.signers.isNotEmpty())
        require((listOf(base) + cluster.apks.splits).all {
            it.packageName == base.packageName && it.version == base.version &&
                it.signers == base.signers && HASH.matches(it.sha256)
        }) { "Private cluster member identity is inconsistent" }
        require(cluster.apks.splits.all { !it.splitName.isNullOrBlank() })
        require(cluster.clusterDigest.matches(HASH))
    }

    private fun requireOwner(engineKey: String, taskId: String) {
        require(engineKey.isNotBlank() && engineKey.length <= 512)
        require(taskId.isNotBlank() && taskId.length <= 512)
    }

    private fun requireCanonicalUuid(value: String) {
        require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            "Invalid private cluster UUID"
        }
    }

    private companion object { val HASH = Regex("[0-9a-f]{64}") }
}
