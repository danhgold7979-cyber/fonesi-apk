package vn.fonesi.core.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget

/** The Room journal is shared; a task UUID alone never grants control over another engine. */
class UpdateQueueNamespaceTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture() = QueueFixture(temporary.newFolder().toPath())

    @Test fun foreignQueuedCancellationIsRejectedWhileLocalCancellationSucceeds() {
        val f = fixture()
        val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("foreign-engine", listOf(target), UpdateScope.PER_CLONE,
            retainedSource = true).single()
        val local = f.queue.enqueue("test-engine", listOf(target), UpdateScope.PER_CLONE).single()

        assertFalse(f.queue.cancel("test-engine", foreign.id))
        assertEquals(foreign, f.store.list().first { it.id == foreign.id })
        assertTrue(f.queue.cancel("test-engine", local.id))
        assertEquals(TaskState.CANCELLED, f.store.list().first { it.id == local.id }.state)
        assertEquals(foreign, f.store.list().first { it.id == foreign.id })
    }

    @Test fun foreignDeferredRetryCannotChangeOwnerTaskButOwningEngineCanRetry() {
        val f = fixture()
        val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("foreign-engine", listOf(target), UpdateScope.PER_CLONE).single()
        val preparing = foreign.move(TaskState.PREPARING, 100L)
        assertTrue(f.store.replace(foreign, preparing))
        val deferred = preparing.move(TaskState.DEFERRED, 101L, TaskIssue.PAUSED)
        assertTrue(f.store.replace(preparing, deferred))

        assertFalse(f.queue.retryDeferred("test-engine", foreign.id))
        assertFalse(f.queue.cancel("test-engine", foreign.id))
        assertEquals(deferred, f.store.list().single())
        assertTrue(f.queue.retryDeferred("foreign-engine", foreign.id))
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertEquals("foreign-engine", f.store.list().single().engineKey)
    }

    @Test fun activeAndUnknownOutcomeCannotBeControlledEvenByOwner() {
        val f = fixture()
        val queued = f.queue.enqueue("foreign-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))), UpdateScope.PER_CLONE).single()
        val preparing = queued.move(TaskState.PREPARING, 110L)
        assertTrue(f.store.replace(queued, preparing))
        val applying = preparing.move(TaskState.APPLYING, 120L,
            affectedIds = listOf("a"), fingerprint = "unknown-private-snapshot")
        assertTrue(f.store.replace(preparing, applying))
        for (key in listOf("foreign-engine", "test-engine")) {
            assertFalse(f.queue.cancel(key, applying.id))
            assertFalse(f.queue.retryDeferred(key, applying.id))
        }
        assertEquals(applying, f.store.list().single())
        val review = applying.move(TaskState.REVIEW_REQUIRED, 130L, TaskIssue.INTERRUPTED)
        assertTrue(f.store.replace(applying, review))
        assertFalse(f.queue.cancel("foreign-engine", review.id))
        assertFalse(f.queue.retryDeferred("foreign-engine", review.id))
        assertEquals(review, f.store.list().single())
    }

    @Test fun blankEngineIdentityIsNotAnAuthorizedQueueController() {
        val f = fixture()
        val task = f.enqueue()
        assertTrue(runCatching { f.queue.cancel("", task.id) }.exceptionOrNull()
            is IllegalArgumentException)
        assertTrue(runCatching { f.queue.retryDeferred(" ", task.id) }.exceptionOrNull()
            is IllegalArgumentException)
        assertEquals(task, f.store.list().single())
    }

    @Test fun missingTaskDoesNotMutateAnotherEngineJournal() {
        val f = fixture()
        val foreign = f.queue.enqueue("foreign-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))), UpdateScope.PER_CLONE).single()
        assertFalse(f.queue.cancel("test-engine", "missing-task-id"))
        assertFalse(f.queue.retryDeferred("test-engine", "missing-task-id"))
        assertEquals(foreign, f.store.list().single())
    }
}
