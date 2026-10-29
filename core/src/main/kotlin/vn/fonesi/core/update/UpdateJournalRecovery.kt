package vn.fonesi.core.update

/**
 * Background-safe journal recovery. This class has no CloneEngine dependency and must never
 * decide that an external APK import succeeded. A foreground runner owns that decision.
 */
data class JournalRecoveryResult(
    val lockAcquired: Boolean,
    val preparingRequeued: Int = 0,
    val interruptedForReview: Int = 0,
    val retryable: Boolean = false,
)

/**
 * Recovers only durable task metadata under the same OS queue lock used by foreground runners.
 *
 * PREPARING: clean its incomplete snapshot first, then CAS back to QUEUED.
 * APPLYING/VERIFYING: move to REVIEW_REQUIRED and retain the snapshot; never retry import.
 * REVIEW_REQUIRED and every other state are left unchanged.
 */
class UpdateJournalRecovery(
    private val store: UpdateTaskStore,
    private val snapshots: SourceSnapshotter,
    private val lock: QueueLock,
    /** Only an original-app source with no retained Room ownership may lose its snapshot. */
    private val allowPreparingCleanup: (UpdateTask) -> Boolean = { !it.retainedSource },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun recover(): JournalRecoveryResult {
        val lease = try {
            lock.acquire()
        } catch (_: Exception) {
            return JournalRecoveryResult(lockAcquired = false, retryable = true)
        } ?: return JournalRecoveryResult(lockAcquired = false, retryable = true)

        lease.use {
            var requeued = 0
            var review = 0
            var retryable = false
            for (old in store.list()) {
                when (old.state) {
                    TaskState.PREPARING -> {
                        // Background work has no guest/provider readback. A private retained source
                        // (including older owner-only Room references) must remain immutable.
                        // Never delete its snapshot or silently reset its durable journal.
                        if (old.retainedSource) continue
                        val allowCleanup = try {
                            allowPreparingCleanup(old)
                        } catch (_: Exception) {
                            retryable = true // Owner registry unavailable: fail closed and recheck later.
                            false
                        }
                        if (!allowCleanup) continue
                        // Only a verified legacy original-source task can safely discard/requeue.
                        try {
                            snapshots.discard(old.id)
                        } catch (_: Exception) {
                            retryable = true
                            continue
                        }
                        val queued = old.move(
                            TaskState.QUEUED,
                            clock(),
                            issue = null,
                            affectedIds = emptyList(),
                            fingerprint = null,
                        )
                        if (store.replace(old, queued)) {
                            requeued++
                        } else {
                            // A non-cooperating writer changed the row. Re-read next attempt.
                            retryable = true
                        }
                    }
                    TaskState.APPLYING, TaskState.VERIFYING -> {
                        val uncertain = old.move(TaskState.REVIEW_REQUIRED, clock(),
                            issue = TaskIssue.INTERRUPTED)
                        if (store.replace(old, uncertain)) {
                            review++
                        } else {
                            retryable = true
                        }
                        // Deliberately retain the prepared APK snapshot. Never invoke the engine.
                    }
                    else -> Unit
                }
            }
            return JournalRecoveryResult(
                lockAcquired = true,
                preparingRequeued = requeued,
                interruptedForReview = review,
                retryable = retryable,
            )
        }
    }
}
