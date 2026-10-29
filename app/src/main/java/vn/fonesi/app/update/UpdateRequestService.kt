package vn.fonesi.app.update

import vn.fonesi.app.storage.CloneMetadataRow
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.app.storage.RoomLegacyUpdateSourcePolicy
import vn.fonesi.core.*
import vn.fonesi.core.update.*
import java.util.UUID
import java.util.concurrent.Callable

data class UpdatePreview(
    val id: String,
    val engineKey: String,
    val availability: EngineAvailability.Ready,
    val requests: List<CloneMetadataRow>,
    val sources: Map<String, InstalledSource>,
    val plan: UpdatePlan,
    val labels: Map<String, String>,
) {
    internal fun equivalent(other: UpdatePreview): Boolean = engineKey == other.engineKey && availability == other.availability &&
        requests.associate { it.launchToken to Triple(it.engineKey, it.cloneId, it.packageName) } ==
            other.requests.associate { it.launchToken to Triple(it.engineKey, it.cloneId, it.packageName) } && sources == other.sources &&
        plan.targets.map { it.source to it.affectedCloneIds.toSet() }.toSet() == other.plan.targets.map { it.source to it.affectedCloneIds.toSet() }.toSet() &&
        plan.alreadyCurrent.toSet() == other.plan.alreadyCurrent.toSet() &&
        plan.blocked.associate { it.requestedCloneId to it.reason } == other.plan.blocked.associate { it.requestedCloneId to it.reason }
}
class UpdatePreviewChangedException : IllegalStateException("Update preview no longer matches current state")

/** Preview is not a command. Confirm re-reads under the same lock and enqueues atomically. */
class UpdateRequestService(
    private val db: FonesiDatabase,
    private val engineKey: String,
    private val engine: CloneEngine,
    private val catalog: SourceCatalog,
    private val tasks: UpdateTaskStore,
    private val lock: QueueLock,
    private val authorized: () -> Boolean,
) {
    private val originalSourcePolicy = RoomLegacyUpdateSourcePolicy(db)

    /**
     * Only original-app queue work is created by this service. Any active retained private
     * task (including E41-owned pre-marker tasks) makes the entire original-source consent
     * conflict unsafe. Called inside the same Room transaction as all selected inserts.
     */
    private fun rejectPrivateSourceConflicts(targets: List<UpdateTarget>) {
        val packages = targets.map { it.source.packageName }.toSet()
        check(tasks.list().none { task ->
            task.engineKey == engineKey && task.packageName in packages &&
                !task.state.terminal && !originalSourcePolicy.allows(task)
        }) { "Active private-source update cannot be replaced by original-app queue consent" }
    }

    fun preview(tokens: List<String>): UpdatePreview {
        val lease = checkNotNull(lock.acquire())
        return lease.use { capture(tokens) }
    }
    fun confirm(preview: UpdatePreview): List<UpdateTask> {
        val lease = checkNotNull(lock.acquire())
        return lease.use {
            val current = capture(preview.requests.map { it.launchToken })
            if (!preview.equivalent(current)) throw UpdatePreviewChangedException()
            check(authorized())
            db.runInTransaction(Callable {
                rejectPrivateSourceConflicts(current.plan.targets)
                val jobs = UpdateQueue(tasks).enqueue(engineKey, current.plan.targets, current.availability.updateScope)
                check(authorized())
                jobs
            })
        }
    }
    private fun capture(tokens: List<String>): UpdatePreview {
        check(authorized()); require(tokens.isNotEmpty())
        val available = engine.availability as? EngineAvailability.Ready ?: error("Engine unavailable")
        val rows = tokens.distinct().map { token -> checkNotNull(db.clones().findByLaunchToken(token)).also { check(it.engineKey == engineKey) } }
        val live = engine.listClones()
        val byId = live.associateBy { it.id }
        check(byId.size == live.size)
        rows.forEach { row -> check(byId[row.cloneId]?.packageName?.let { it == row.packageName } != false) }
        val sources = rows.map { it.packageName }.distinct().mapNotNull { pkg -> catalog.current(pkg)?.let { pkg to it } }.toMap()
        val originalPlan = UpdatePlanner.plan(rows.map { it.cloneId }, live, sources, available)
        val metadata = db.clones().list().filter { it.engineKey == engineKey }
        val incomplete = if (available.updateScope == UpdateScope.SHARED_PACKAGE) metadata.flatMap { saved ->
            val observed = byId[saved.cloneId]
            if (observed?.packageName != saved.packageName) listOfNotNull(saved.packageName, observed?.packageName) else emptyList()
        }.toSet() else emptySet()
        val held = rows.filter { it.packageName in incomplete }.map { it.cloneId }.toSet()
        val plan = originalPlan.copy(targets = originalPlan.targets.filterNot { it.source.packageName in incomplete },
            alreadyCurrent = originalPlan.alreadyCurrent.filterNot { it in held },
            blocked = originalPlan.blocked + (held - originalPlan.blocked.map { it.requestedCloneId }.toSet()).map { UpdateBlock(it, UpdateBlockReason.INCOMPLETE_GROUP) })
        check(authorized() && engine.availability == available)
        return UpdatePreview(UUID.randomUUID().toString(), engineKey, available, rows.toList(), sources.toMap(), plan,
            metadata.filter { byId[it.cloneId]?.packageName == it.packageName }.associate { it.cloneId to it.label })
    }
}
