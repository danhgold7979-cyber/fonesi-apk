package vn.fonesi.core.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.*
import java.nio.file.Files

class ExecutionPolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture() = QueueFixture(temporary.newFolder().toPath())
    private fun runner(f: QueueFixture, allowed: () -> Boolean) = UpdateRunner("test-engine", f.engine, f.store, f.catalog,
        f.snapshotter, FileQueueLock(f.root.resolve("queue.lock")), mayApply = allowed)
    @Test fun policyBlocksBeforeTakingAQueuedTask() {
        val f = fixture(); f.enqueue()
        assertEquals(RunResult.PolicyBlocked, runner(f) { false }.runNext())
        assertEquals(TaskState.QUEUED, f.store.list().single().state); assertEquals(0, f.engine.calls)
    }
    @Test fun losingForegroundAfterSnapshotDefersWithoutEnteringSdkAndCanResumeSafely() {
        val f = fixture(); val job = f.enqueue(); var allowed = true
        f.afterPrepare = { allowed = false }
        val result = runner(f) { allowed }.runNext() as RunResult.Processed
        assertEquals(TaskState.DEFERRED, result.task.state); assertEquals(TaskIssue.EXECUTION_POLICY, result.task.issue)
        assertEquals(0, f.engine.calls); assertFalse(Files.exists(f.root.resolve("snapshots").resolve(job.id)))
        f.afterPrepare = null; allowed = true; assertTrue(f.queue.retryDeferred(job.id))
        assertEquals(TaskState.SUCCEEDED, (runner(f) { allowed }.runNext() as RunResult.Processed).task.state)
    }
    @Test fun policyLostAfterSdkEntryStillVerifiesInsteadOfPretendingCancellation() {
        val f = fixture(); f.enqueue(); var allowed = true
        f.engine.beforeApply = { allowed = false }
        val result = runner(f) { allowed }.runNext() as RunResult.Processed
        assertEquals(TaskState.SUCCEEDED, result.task.state); assertEquals(1, f.engine.calls)
        assertEquals(RunResult.PolicyBlocked, runner(f) { allowed }.runNext())
    }
    @Test fun changedSplitCapabilityAfterPrepareIsRecheckedBeforeApply() {
        val f = fixture(); f.enqueue()
        f.afterPrepare = { f.engine.availability = EngineAvailability.Ready(UpdateScope.PER_CLONE, importsSplitApks = true) }
        val result = runner(f) { true }.runNext() as RunResult.Processed
        assertEquals(TaskIssue.SCOPE_CHANGED, result.task.issue); assertEquals(0, f.engine.calls)
    }
}
