package vn.fonesi.core.update

import vn.fonesi.core.CloneEngine
import vn.fonesi.core.CloneRecord
import vn.fonesi.core.EngineAvailability
import vn.fonesi.core.UpdateBlockReason
import vn.fonesi.core.UpdatePlanner
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget
import java.util.ConcurrentModificationException

sealed interface RunResult {
    data object PolicyBlocked : RunResult
    data object Busy : RunResult
    data object Idle : RunResult
    data object Paused : RunResult
    data object EngineUnavailable : RunResult
    data class Processed(val task: UpdateTask, val cleanupPending: Boolean = false) : RunResult
}

interface UpdateExecution { fun runNext(): RunResult; fun recover(): Boolean }

/**
 * Durable orchestration, not a virtualization engine. The adapter must synchronously finish
 * importing the prepared APK set and coordinate against launches while applying it.
 * There is no blind retry after an uncertain external side effect.
 */
class UpdateRunner(
    private val engineKey: String,
    private val engine: CloneEngine,
    private val store: UpdateTaskStore,
    private val catalog: SourceCatalog,
    private val snapshots: SourceSnapshotter,
    private val lock: QueueLock,
    private val mayApply: () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
    private val allowLegacySource: (UpdateTask) -> Boolean = { true },
) : UpdateExecution {
    override fun runNext(): RunResult {
        if (!mayApply()) return RunResult.PolicyBlocked
        val lease = lock.acquire() ?: return RunResult.Busy
        return lease.use {
            recoverLocked()
            if (store.isPaused()) return@use RunResult.Paused
            if (engine.availability !is EngineAvailability.Ready) return@use RunResult.EngineUnavailable
            // The journal/OS lock is shared, but only this engine owns its execution.
            // Foreign same-package tasks must neither be scheduled nor veto local work.
            val jobs = store.list().filter { it.engineKey == engineKey }
            val next = jobs.firstOrNull { candidate ->
                candidate.state == TaskState.QUEUED && jobs.takeWhile { it.id != candidate.id }.none {
                    it.packageName == candidate.packageName && !it.state.terminal
                } && jobs.none {
                    it.packageName == candidate.packageName && it.state == TaskState.REVIEW_REQUIRED
                }
            } ?: return@use RunResult.Idle
            // A private retained cluster has no validated product update adapter.
            // Block it before the original installed-app SourceCatalog can be consulted.
            if (!mayUseLegacySource(next)) return@use RunResult.PolicyBlocked
            process(next)
        }
    }

    /** Recovery is safe only while owning the OS-wide queue lock. */
    override fun recover(): Boolean {
        val lease = lock.acquire() ?: return false
        lease.use { recoverLocked() }
        return true
    }

    private fun recoverLocked() {
        // Never rewrite a foreign engine's journal or discard its private snapshot.
        // Filter before any legacy-source policy, readback or recovery side effect.
        for (old in store.list().filter { it.engineKey == engineKey }) {
            // E48: retained private images have no accepted legacy-engine recovery/readback.
            // Fail closed before discarding task snapshots or consulting the installed original.
            if (old.state in setOf(TaskState.PREPARING, TaskState.APPLYING,
                    TaskState.VERIFYING, TaskState.REVIEW_REQUIRED) &&
                !mayUseLegacySource(old)) {
                if (old.state == TaskState.APPLYING || old.state == TaskState.VERIFYING) {
                    // A crash during an unverified private-source mutation is unknown, not success.
                    // Room commits its matching source owner promotion atomically with this marker.
                    persist(old, old.move(TaskState.REVIEW_REQUIRED, clock(), TaskIssue.INTERRUPTED))
                }
                // PREPARING/REVIEW_REQUIRED remain held, including their snapshots/references.
                continue
            }
            when (old.state) {
                TaskState.PREPARING -> {
                    snapshots.discard(old.id)
                    persist(old, old.move(TaskState.QUEUED, clock(), affectedIds = emptyList(), fingerprint = null))
                }
                TaskState.APPLYING, TaskState.VERIFYING, TaskState.REVIEW_REQUIRED -> {
                    var current = old
                    if (current.state != TaskState.REVIEW_REQUIRED) {
                        current = persist(current, current.move(TaskState.REVIEW_REQUIRED, clock(), TaskIssue.INTERRUPTED))
                    }
                    val available = engine.availability as? EngineAvailability.Ready
                    if (current.engineKey != engineKey || available?.updateScope != current.scope) continue
                    val observed = try { engine.listClones() } catch (_: Exception) { continue }
                    if (verified(current, observed)) {
                        persist(current, current.move(TaskState.SUCCEEDED, clock(),
                            completion = CompletionKind.RECOVERED_VERSION_VERIFIED))
                        // A cleanup error does not undo a verified installed version.
                        runCatching { snapshots.discard(current.id) }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun process(queued: UpdateTask): RunResult {
        var current = queued.move(TaskState.PREPARING, clock())
        if (!store.replace(queued, current, requireUnpaused = true)) {
            return if (store.isPaused()) RunResult.Paused else RunResult.Idle
        }
        var applying = false
        var cleanup = true
        try {
            fun finish(state: TaskState, issue: TaskIssue? = null, completion: CompletionKind? = null): RunResult.Processed {
                current = persist(current, current.move(state, clock(), issue, completion))
                cleanup = state != TaskState.REVIEW_REQUIRED
                val clean = if (cleanup) runCatching { snapshots.discard(current.id) }.isSuccess else true
                cleanup = false // Already cleaned or intentionally retained for review.
                return RunResult.Processed(current, cleanupPending = !clean)
            }
            if (!mayApply() || !mayUseLegacySource(current)) {
                return finish(TaskState.DEFERRED, TaskIssue.EXECUTION_POLICY)
            }
            if (current.engineKey != engineKey) return finish(TaskState.DEFERRED, TaskIssue.ENGINE_CHANGED)
            val available = engine.availability as? EngineAvailability.Ready
                ?: return finish(TaskState.DEFERRED, TaskIssue.ENGINE_UNAVAILABLE)
            if (available.updateScope != current.scope) return finish(TaskState.FAILED, TaskIssue.SCOPE_CHANGED)
            val source = catalog.current(current.packageName)
                ?: return finish(TaskState.FAILED, TaskIssue.SOURCE_MISSING)
            if (source.versionCode != current.versionCode || source.signerDigests != current.signerDigests) {
                return finish(TaskState.FAILED, TaskIssue.SOURCE_CHANGED)
            }
            fun plan() = UpdatePlanner.plan(current.cloneIds, engine.listClones(), mapOf(current.packageName to source), available)
            val first = plan()
            if (first.blocked.isNotEmpty()) {
                return finish(if (first.blocked.all { it.reason == UpdateBlockReason.IN_USE_OR_UNKNOWN }) TaskState.DEFERRED else TaskState.FAILED,
                    if (first.blocked.all { it.reason == UpdateBlockReason.IN_USE_OR_UNKNOWN }) TaskIssue.CLONE_BUSY else TaskIssue.UNSAFE_TARGET)
            }
            if (first.targets.isEmpty()) {
                return finish(TaskState.SUCCEEDED, completion = CompletionKind.ALREADY_CURRENT)
            }
            if (first.targets.size != 1) return finish(TaskState.FAILED, TaskIssue.SCOPE_CHANGED)
            val snapshot = snapshots.prepare(current.id, source)
            if (snapshot.source.packageName != source.packageName || snapshot.source.versionCode != source.versionCode ||
                snapshot.source.signerDigests != source.signerDigests || !snapshots.verify(snapshot)) {
                return finish(TaskState.FAILED, TaskIssue.SOURCE_PREPARATION_FAILED)
            }
            if (catalog.current(current.packageName) != source) return finish(TaskState.FAILED, TaskIssue.SOURCE_CHANGED)
            val latestAvailability = engine.availability as? EngineAvailability.Ready
                ?: return finish(TaskState.DEFERRED, TaskIssue.ENGINE_UNAVAILABLE)
            if (latestAvailability.updateScope != current.scope || latestAvailability.importsSplitApks != available.importsSplitApks) {
                return finish(TaskState.FAILED, TaskIssue.SCOPE_CHANGED)
            }
            // Re-read after preparation: accounts may have been opened/deleted/added in the meantime.
            val last = plan()
            if (last.blocked.isNotEmpty()) {
                return finish(if (last.blocked.all { it.reason == UpdateBlockReason.IN_USE_OR_UNKNOWN }) TaskState.DEFERRED else TaskState.FAILED,
                    if (last.blocked.all { it.reason == UpdateBlockReason.IN_USE_OR_UNKNOWN }) TaskIssue.CLONE_BUSY else TaskIssue.UNSAFE_TARGET)
            }
            if (last.targets.isEmpty()) return finish(TaskState.SUCCEEDED, completion = CompletionKind.ALREADY_CURRENT)
            if (last.targets.size != 1) return finish(TaskState.FAILED, TaskIssue.SCOPE_CHANGED)
            val target = last.targets.single()
            if (!mayApply() || !mayUseLegacySource(current)) {
                return finish(TaskState.DEFERRED, TaskIssue.EXECUTION_POLICY)
            }
            val beforeApply = current.move(TaskState.APPLYING, clock(), affectedIds = target.affectedCloneIds,
                fingerprint = snapshot.fingerprint)
            if (!store.replace(current, beforeApply, requireUnpaused = true)) {
                if (store.isPaused()) return finish(TaskState.DEFERRED, TaskIssue.PAUSED)
                throw ConcurrentModificationException("Update task changed before apply")
            }
            current = beforeApply // The write-ahead marker is durable BEFORE calling the external engine.
            applying = true
            cleanup = false
            engine.update(UpdateTarget(snapshot.source, target.affectedCloneIds))
            current = persist(current, current.move(TaskState.VERIFYING, clock()))
            if (!verified(current, engine.listClones())) return finish(TaskState.REVIEW_REQUIRED, TaskIssue.VERSION_NOT_VERIFIED)
            return finish(TaskState.SUCCEEDED, completion = CompletionKind.APPLIED_AND_VERIFIED)
        } catch (error: ConcurrentModificationException) {
            // Never overwrite an unexpected concurrent state. Recovery will reconcile a durable marker.
            throw error
        } catch (_: SourceChangedException) {
            current = persist(current, current.move(if (applying) TaskState.REVIEW_REQUIRED else TaskState.FAILED,
                clock(), if (applying) TaskIssue.ENGINE_OUTCOME_UNKNOWN else TaskIssue.SOURCE_CHANGED))
            cleanup = !applying
            return RunResult.Processed(current)
        } catch (_: Exception) {
            val next = current.move(if (applying) TaskState.REVIEW_REQUIRED else TaskState.FAILED, clock(),
                if (applying) TaskIssue.ENGINE_OUTCOME_UNKNOWN else TaskIssue.SOURCE_PREPARATION_FAILED)
            current = persist(current, next)
            cleanup = !applying
            return RunResult.Processed(current)
        } finally {
            if (cleanup) runCatching { snapshots.discard(current.id) }
        }
    }

    /** Fail closed for marked/private retained sources and every uncertain host readback. */
    private fun mayUseLegacySource(task: UpdateTask): Boolean =
        !task.retainedSource && runCatching { allowLegacySource(task) }.getOrDefault(false)

    private fun verified(task: UpdateTask, clones: List<CloneRecord>): Boolean {
        if (task.affectedIds.isEmpty() || task.snapshotFingerprint == null) return false
        if (clones.map { it.id }.distinct().size != clones.size) return false
        val byId = clones.associateBy { it.id }
        if (task.scope == UpdateScope.SHARED_PACKAGE &&
            clones.filter { it.packageName == task.packageName }.map { it.id }.toSet() != task.affectedIds.toSet()) return false
        return task.affectedIds.all { id ->
            byId[id]?.let {
                it.packageName == task.packageName && it.versionCode == task.versionCode && it.signerDigests == task.signerDigests
            } == true
        }
    }

    private fun persist(expected: UpdateTask, next: UpdateTask): UpdateTask {
        check(store.replace(expected, next)) { "Cannot persist update journal transition" }
        return next
    }
}
