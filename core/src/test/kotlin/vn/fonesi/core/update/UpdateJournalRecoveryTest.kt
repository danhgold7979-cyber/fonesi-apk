package vn.fonesi.core.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class UpdateJournalRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture() = QueueFixture(temporary.newFolder().toPath())
    private fun recovery(f: QueueFixture, snapshots: SourceSnapshotter = f.snapshotter,
        lock: QueueLock = FileQueueLock(f.root.resolve("queue.lock"))) =
        UpdateJournalRecovery(f.store, snapshots, lock) { 500L }

    private fun preparingWithSnapshot(f: QueueFixture): UpdateTask {
        val queued = f.enqueue()
        val preparing = queued.move(TaskState.PREPARING, 100L)
        assertTrue(f.store.replace(queued, preparing))
        f.files.prepare(preparing.id, checkNotNull(f.source))
        assertTrue(Files.exists(f.root.resolve("snapshots").resolve(preparing.id)))
        return preparing
    }

    private fun applyingWithSnapshot(f: QueueFixture): UpdateTask {
        val preparing = preparingWithSnapshot(f)
        val snapshot = f.files.prepare(preparing.id, checkNotNull(f.source))
        val applying = preparing.move(TaskState.APPLYING, 200L,
            affectedIds = preparing.cloneIds, fingerprint = snapshot.fingerprint)
        assertTrue(f.store.replace(preparing, applying))
        return applying
    }

    @Test fun preparingRecoveryDeletesIncompleteSnapshotThenRequeuesWithClearedMarkers() {
        val f = fixture()
        val old = preparingWithSnapshot(f)
        val result = recovery(f).recover()
        val actual = f.store.list().single()
        assertTrue(result.lockAcquired)
        assertFalse(result.retryable)
        assertEquals(1, result.preparingRequeued)
        assertEquals(TaskState.QUEUED, actual.state)
        assertNull(actual.snapshotFingerprint)
        assertTrue(actual.affectedIds.isEmpty())
        assertFalse(Files.exists(f.root.resolve("snapshots").resolve(old.id)))
    }

    @Test fun cleanupFailureLeavesPreparingStateAndRequestsRetry() {
        val f = fixture()
        val old = preparingWithSnapshot(f)
        val failing = object : SourceSnapshotter {
            override fun prepare(taskId: String, expected: vn.fonesi.core.InstalledSource) =
                f.snapshotter.prepare(taskId, expected)
            override fun verify(snapshot: PreparedSource) = f.snapshotter.verify(snapshot)
            override fun discard(taskId: String): Unit = throw java.io.IOException("synthetic cleanup failure")
        }
        val result = recovery(f, failing).recover()
        assertTrue(result.lockAcquired)
        assertTrue(result.retryable)
        assertEquals(TaskState.PREPARING, f.store.list().single().state)
        assertTrue(Files.exists(f.root.resolve("snapshots").resolve(old.id)))
    }

    @Test fun interruptedApplyingMovesToReviewAndRetainsSnapshotWithoutEngineRetry() {
        val f = fixture()
        val old = applyingWithSnapshot(f)
        val snapshot = f.root.resolve("snapshots").resolve(old.id)
        val result = recovery(f).recover()
        val actual = f.store.list().single()
        assertEquals(1, result.interruptedForReview)
        assertEquals(TaskState.REVIEW_REQUIRED, actual.state)
        assertEquals(TaskIssue.INTERRUPTED, actual.issue)
        assertEquals(old.snapshotFingerprint, actual.snapshotFingerprint)
        assertTrue(Files.exists(snapshot))
    }

    @Test fun interruptedVerifyingMovesToReviewAndRetainsSnapshot() {
        val f = fixture()
        val applying = applyingWithSnapshot(f)
        val verifying = applying.move(TaskState.VERIFYING, 300L)
        assertTrue(f.store.replace(applying, verifying))
        val result = recovery(f).recover()
        assertEquals(1, result.interruptedForReview)
        assertEquals(TaskState.REVIEW_REQUIRED, f.store.list().single().state)
        assertTrue(Files.exists(f.root.resolve("snapshots").resolve(verifying.id)))
    }

    @Test fun reviewRequiredIsNotPromotedOrCleanedByBackgroundRecovery() {
        val f = fixture()
        val applying = applyingWithSnapshot(f)
        val review = applying.move(TaskState.REVIEW_REQUIRED, 300L, TaskIssue.INTERRUPTED)
        assertTrue(f.store.replace(applying, review))
        val snapshot = f.root.resolve("snapshots").resolve(review.id)
        val result = recovery(f).recover()
        assertEquals(0, result.interruptedForReview)
        assertEquals(review, f.store.list().single())
        assertTrue(Files.exists(snapshot))
    }

    @Test fun lockContentionReturnsRetryableWithoutChangingJournalOrSnapshot() {
        val f = fixture()
        val old = preparingWithSnapshot(f)
        val lock = FileQueueLock(f.root.resolve("queue.lock"))
        val held = checkNotNull(lock.acquire())
        try {
            val result = recovery(f, lock = FileQueueLock(f.root.resolve("queue.lock"))).recover()
            assertFalse(result.lockAcquired)
            assertTrue(result.retryable)
            assertEquals(old, f.store.list().single())
            assertTrue(Files.exists(f.root.resolve("snapshots").resolve(old.id)))
        } finally {
            held.close()
        }
    }

    @Test fun recoveryPreservesQueuePauseAndIsIdempotent() {
        val f = fixture()
        preparingWithSnapshot(f)
        f.queue.pause()
        val first = recovery(f).recover()
        val second = recovery(f).recover()
        assertEquals(1, first.preparingRequeued)
        assertEquals(0, second.preparingRequeued)
        assertTrue(f.store.isPaused())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
    }

    @Test fun failedCompareAndSetRequestsRetryInsteadOfOverwritingConcurrentTask() {
        val f = fixture()
        val old = preparingWithSnapshot(f)
        f.store.beforeReplace = { next ->
            if (next.state == TaskState.QUEUED) {
                val current = f.store.list().single()
                f.store.beforeReplace = null
                f.store.replace(current, current.copy(revision = current.revision + 1,
                    changedAt = current.changedAt + 1))
            }
        }
        val result = recovery(f).recover()
        assertTrue(result.retryable)
        assertEquals(TaskState.PREPARING, f.store.list().single().state)
        assertFalse(Files.exists(f.root.resolve("snapshots").resolve(old.id)))
    }
}
