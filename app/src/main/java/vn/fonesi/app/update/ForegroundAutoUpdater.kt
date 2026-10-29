package vn.fonesi.app.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.app.storage.RoomLegacyUpdateSourcePolicy
import vn.fonesi.core.update.*

data class AutoUpdateState(val running: Boolean = false, val mode: AutoUpdateMode = AutoUpdateMode.OFF,
    val summary: String? = null, val preview: UpdatePreview? = null)

/** No worker/boot/guest UI startup. All import decisions still go through durable queue/runner. */
class ForegroundAutoUpdater(
    private val db: FonesiDatabase,
    private val engineKey: String,
    private val policy: AutoUpdatePolicyStore,
    private val requests: UpdateRequestService,
    private val tasks: UpdateTaskStore,
    private val autoLock: QueueLock,
    private val operationLock: QueueLock,
    private val foregroundAccess: () -> Boolean,
    private val runner: () -> UpdateExecution,
) {
    private val originalSourcePolicy = RoomLegacyUpdateSourcePolicy(db)
    private val mutable = MutableStateFlow(AutoUpdateState())
    val state = mutable.asStateFlow()
    fun check() {
        val chosen = policy.read()
        if (chosen.mode == AutoUpdateMode.OFF || !foregroundAccess()) {
            if (!mutable.value.running) mutable.value = AutoUpdateState(mode = chosen.mode,
                summary = if (chosen.unreadable) "Chưa đọc được lựa chọn tự động; đang tắt để bạn kiểm tra lại." else null)
            return
        }
        val lease = autoLock.acquire() ?: return
        lease.use {
            mutable.value = AutoUpdateState(running = true, mode = chosen.mode)
            try {
                val execution = runner()
                if (!execution.recover()) { mutable.value = mutable.value.copy(summary = "Đang có thao tác khác; chưa tự cập nhật."); return }
                val tokens = db.clones().list().filter { it.engineKey == engineKey }.map { it.launchToken }
                if (tokens.isEmpty()) { mutable.value = mutable.value.copy(summary = "Chưa có bản clone thuộc engine hiện tại để tự kiểm tra."); return }
                val preview = requests.preview(tokens)
                mutable.value = mutable.value.copy(preview = preview,
                    summary = "Có ${preview.plan.targets.size} nhóm đủ điều kiện; ${preview.plan.blocked.size} yêu cầu bị chặn, ${preview.plan.alreadyCurrent.size} bản đã đúng phiên bản.")
                if (chosen.mode != AutoUpdateMode.APPLY_WHILE_OPEN || !allowed()) return
                if (tasks.isPaused()) { mutable.value = mutable.value.copy(summary = "Hàng đợi đang tạm dừng; không tự tiếp tục."); return }
                reconcileSafeQueuedWork(preview)
                if (!allowed() || tasks.isPaused()) return
                val jobs = requests.confirm(preview)
                retrySafeDeferred(preview)
                var processed = 0
                while (allowed() && !tasks.isPaused()) {
                    when (execution.runNext()) { is RunResult.Processed -> processed++; else -> break }
                }
                mutable.value = mutable.value.copy(summary = "Đã xếp/đối chiếu ${jobs.size} nhóm và xử lý $processed tác vụ. Xem trạng thái từng tác vụ để biết kết quả; không phải mọi tác vụ đều thành công.")
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(summary = "Chưa hoàn tất tự kiểm tra/cập nhật. Giữ journal; không tự chạy lại kết quả chưa rõ.")
            } catch (_: LinkageError) {
                mutable.value = mutable.value.copy(summary = "Engine chưa tương thích đường chạy này. Đã dừng tự động; cần kiểm tra runtime.")
            } finally { mutable.value = mutable.value.copy(running = false, mode = policy.read().mode) }
        }
    }
    private fun allowed() = foregroundAccess() && policy.read().mode == AutoUpdateMode.APPLY_WHILE_OPEN

    /** Original-app previews must never mutate a task with private retained source authority.
     *  Old pre-marker E41 owner references and unreadable Room state also fail closed.
     */
    private fun mayReconcileOriginalSource(task: UpdateTask): Boolean =
        runCatching { originalSourcePolicy.allows(task) }.getOrDefault(false)
    private fun reconcileSafeQueuedWork(preview: UpdatePreview) {
        val lease = checkNotNull(operationLock.acquire())
        lease.use {
            db.runInTransaction {
                check(allowed() && !tasks.isPaused())
                for (job in tasks.list()) {
                    if (job.engineKey != engineKey || job.state !in setOf(TaskState.QUEUED, TaskState.DEFERRED)) continue
                    if (!mayReconcileOriginalSource(job)) continue
                    val source = preview.sources[job.packageName] ?: continue
                    if (job.versionCode != source.versionCode || job.signerDigests != source.signerDigests || job.scope != preview.availability.updateScope) {
                        check(tasks.replace(job, job.move(TaskState.CANCELLED, System.currentTimeMillis(), TaskIssue.SUPERSEDED_SOURCE), requireUnpaused = true))
                    }
                }
                check(allowed() && !tasks.isPaused())
            }
        }
    }
    private fun retrySafeDeferred(preview: UpdatePreview) {
        val lease = checkNotNull(operationLock.acquire())
        lease.use {
            db.runInTransaction {
                check(allowed() && !tasks.isPaused())
                for (job in tasks.list()) {
                    if (job.engineKey != engineKey || job.state != TaskState.DEFERRED || job.issue !in setOf(
                            TaskIssue.EXECUTION_POLICY, TaskIssue.CLONE_BUSY, TaskIssue.PAUSED, TaskIssue.ENGINE_UNAVAILABLE)) continue
                    if (!mayReconcileOriginalSource(job)) continue
                    preview.plan.targets.singleOrNull { it.source.packageName == job.packageName &&
                        it.source.versionCode == job.versionCode && it.source.signerDigests == job.signerDigests &&
                        it.affectedCloneIds.containsAll(job.cloneIds) } ?: continue
                    check(tasks.replace(job, job.move(TaskState.QUEUED, System.currentTimeMillis()), requireUnpaused = true))
                }
                check(allowed() && !tasks.isPaused())
            }
        }
    }
}
