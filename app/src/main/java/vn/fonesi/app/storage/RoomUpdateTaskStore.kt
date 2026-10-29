package vn.fonesi.app.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.core.update.*
import java.util.concurrent.Callable

/**
 * Room journal with atomic retention of previously acquired private-source owner references.
 * Unassociated legacy tasks still use the identical revision/pause CAS path. This store never
 * publishes, activates, installs, opens or deletes APKs; only the E41 readback API may acquire
 * a new private-cluster UPDATE_TASK reference.
 */
class RoomUpdateTaskStore(
    private val db: FonesiDatabase,
    private val atomicOwnerTransitions: Boolean = true,
) : UpdateTaskStore {
    fun observe(): Flow<List<UpdateTask>> = db.tasks().observe().map { rows -> rows.map(TaskCodec::decode) }
    fun observePaused(): Flow<Boolean> = db.tasks().observePaused().map { it ?: false }
    override fun list(): List<UpdateTask> = db.tasks().list().map(TaskCodec::decode)

    override fun insertOrGetActive(task: UpdateTask): UpdateTask = db.runInTransaction(Callable {
        require(task.state == TaskState.QUEUED && task.revision == 0L)
        db.tasks().findActive(task.dedupKey)?.let { return@Callable TaskCodec.decode(it) }
        val inserted = db.tasks().insert(TaskCodec.row(task))
        if (inserted == -1L) {
            return@Callable TaskCodec.decode(checkNotNull(db.tasks().findActive(task.dedupKey)))
        }
        task
    })

    override fun replace(expected: UpdateTask, replacement: UpdateTask, requireUnpaused: Boolean): Boolean =
        db.runInTransaction(Callable {
            replacement.requireSuccessorOf(expected)
            val current = db.tasks().find(expected.id)?.let(TaskCodec::decode) ?: return@Callable false
            if (current != expected || (requireUnpaused && isPaused())) return@Callable false
            val row = TaskCodec.row(replacement)
            if (db.tasks().replace(expected.id, expected.revision, expected.state.name, row.state,
                    row.revision, row.activeKey, row.payload) != 1) return@Callable false
            // Both journal and owner-reference changes must commit or roll back as one Room unit.
            if (atomicOwnerTransitions) transitionExistingOwners(replacement)
            true
        })

    /**
     * Called only after successful task CAS, while still inside the same transaction.
     * No reference is created for tasks without an E41-verified UPDATE_TASK owner.
     */
    private fun transitionExistingOwners(next: UpdateTask) {
        if (next.state != TaskState.REVIEW_REQUIRED && !next.state.terminal) return

        val taskType = ClusterOwnerType.UPDATE_TASK.name
        val reviewType = ClusterOwnerType.REVIEW_REQUIRED.name
        val taskRefs = db.clusterReferences().forOwner(taskType, next.id, next.engineKey)
        val reviewRefs = db.clusterReferences().forOwner(reviewType, next.id, next.engineKey)
        check(taskRefs.size <= 1 && reviewRefs.size <= 1) {
            "Ambiguous private-source owner references; journal transition not committed"
        }
        val source = taskRefs.singleOrNull()
        if (source == null) {
            // A review owner with no task owner is valid after previous promotion. A new
            // REVIEW_REQUIRED marker cannot safely assume an unrelated pre-existing review.
            check(next.state != TaskState.REVIEW_REQUIRED || reviewRefs.isEmpty()) {
                "Review source predates journal marker; manual reconciliation required"
            }
            return
        }
        check(reviewRefs.isEmpty() || reviewRefs.single().clusterId == source.clusterId) {
            "Conflicting private-cluster generations; preserving both journal and references"
        }

        if (next.state == TaskState.REVIEW_REQUIRED && reviewRefs.isEmpty()) {
            require(next.changedAt >= 0) { "Invalid durable review timestamp" }
            check(db.clusterReferences().insert(ClusterReferenceRow(
                clusterId = source.clusterId,
                ownerType = reviewType,
                ownerId = next.id,
                engineKey = next.engineKey,
                createdAtEpochMs = next.changedAt,
            )) != -1L) { "Could not acquire review owner before task owner release" }
        }

        // A terminal marker releases only UPDATE_TASK; REVIEW_REQUIRED, CLONE and ROLLBACK
        // owners are retained, and no private APK file can be removed here.
        check(db.clusterReferences().delete(source.clusterId, taskType, next.id, next.engineKey) == 1) {
            "Could not release private-source owner atomically with journal"
        }
    }

    override fun isPaused() = db.tasks().paused() ?: false
    override fun setPaused(paused: Boolean) = db.tasks().setControl(QueueControlRow(paused = paused))
}
