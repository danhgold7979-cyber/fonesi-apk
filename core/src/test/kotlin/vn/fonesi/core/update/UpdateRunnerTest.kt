package vn.fonesi.core.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.*
import java.nio.file.Files
import java.util.ConcurrentModificationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UpdateRunnerTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture() = QueueFixture(temporary.newFolder().toPath())
    private fun RunResult.task() = (this as RunResult.Processed).task

    @Test fun `retained applying recovery cannot claim success from matching legacy clone version`() {
        val f = fixture()
        val queued = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE, retainedSource = true).single()
        val preparing = queued.move(TaskState.PREPARING, 101L)
        assertTrue(f.store.replace(queued, preparing))
        val applying = preparing.move(TaskState.APPLYING, 102L,
            affectedIds = listOf("a"), fingerprint = "private-source-test")
        assertTrue(f.store.replace(preparing, applying))
        // Deliberately matching *legacy* guest metadata must not prove a private-source update.
        f.engine.clones[0] = f.engine.clones.single().copy(versionCode = 2L)
        assertTrue(f.runner().recover())
        val held = f.store.list().single()
        assertEquals(TaskState.REVIEW_REQUIRED, held.state)
        assertEquals(TaskIssue.INTERRUPTED, held.issue)
        assertNull(held.completion)
        assertTrue(held.retainedSource)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `older owner-only recovery policy denies matching legacy engine version`() {
        val f = fixture()
        val queued = f.enqueue()
        val preparing = queued.move(TaskState.PREPARING, 101L)
        assertTrue(f.store.replace(queued, preparing))
        val applying = preparing.move(TaskState.APPLYING, 102L,
            affectedIds = listOf("a"), fingerprint = "pre-marker-private")
        assertTrue(f.store.replace(preparing, applying))
        val verifying = applying.move(TaskState.VERIFYING, 103L)
        assertTrue(f.store.replace(applying, verifying))
        f.engine.clones[0] = f.engine.clones.single().copy(versionCode = 2L)
        val runner = UpdateRunner("test-engine", f.engine, f.store, f.catalog, f.snapshotter,
            FileQueueLock(f.root.resolve("owner-only-recovery.lock")),
            allowLegacySource = { false })
        assertTrue(runner.recover())
        val held = f.store.list().single()
        assertEquals(TaskState.REVIEW_REQUIRED, held.state)
        assertEquals(TaskIssue.INTERRUPTED, held.issue)
        assertNull(held.completion)
        assertFalse(held.retainedSource)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `recovery authority readback exception keeps uncertain task in review`() {
        val f = fixture()
        val queued = f.enqueue()
        val preparing = queued.move(TaskState.PREPARING, 201L)
        assertTrue(f.store.replace(queued, preparing))
        val applying = preparing.move(TaskState.APPLYING, 202L,
            affectedIds = listOf("a"), fingerprint = "unverified-private")
        assertTrue(f.store.replace(preparing, applying))
        f.engine.clones[0] = f.engine.clones.single().copy(versionCode = 2L)
        val runner = UpdateRunner("test-engine", f.engine, f.store, f.catalog, f.snapshotter,
            FileQueueLock(f.root.resolve("failed-owner-readback.lock")),
            allowLegacySource = { throw IllegalStateException("Owner registry unreadable") })
        assertTrue(runner.recover())
        val held = f.store.list().single()
        assertEquals(TaskState.REVIEW_REQUIRED, held.state)
        assertEquals(TaskIssue.INTERRUPTED, held.issue)
        assertNull(held.completion)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `retained preparing recovery does not discard task snapshot or rewrite task`() {
        val f = fixture()
        val queued = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE, retainedSource = true).single()
        val preparing = queued.move(TaskState.PREPARING, 301L)
        assertTrue(f.store.replace(queued, preparing))
        f.files.prepare(queued.id, checkNotNull(f.source))
        val snapshot = f.root.resolve("snapshots").resolve(queued.id)
        assertTrue(Files.exists(snapshot))
        assertTrue(f.runner().recover())
        assertEquals(preparing, f.store.list().single())
        assertTrue(Files.exists(snapshot))
        assertEquals(0, f.engine.calls)
    }

    @Test fun `retained review recovery never converts old metadata into success`() {
        val f = fixture()
        val queued = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE, retainedSource = true).single()
        val preparing = queued.move(TaskState.PREPARING, 401L)
        assertTrue(f.store.replace(queued, preparing))
        val applying = preparing.move(TaskState.APPLYING, 402L,
            affectedIds = listOf("a"), fingerprint = "old-private-source")
        assertTrue(f.store.replace(preparing, applying))
        val review = applying.move(TaskState.REVIEW_REQUIRED, 403L, TaskIssue.INTERRUPTED)
        assertTrue(f.store.replace(applying, review))
        f.engine.clones[0] = f.engine.clones.single().copy(versionCode = 2L)
        assertTrue(f.runner().recover())
        assertEquals(review, f.store.list().single())
        assertNull(f.store.list().single().completion)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `retained private task cannot use original catalog or run engine`() {
        val f = fixture()
        val retained = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE, retainedSource = true).single()
        assertTrue(retained.retainedSource)
        assertEquals(RunResult.PolicyBlocked, f.runner().runNext())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertEquals(0, f.engine.calls)
        assertFalse(Files.exists(f.root.resolve("snapshots").resolve(retained.id)))
    }

    @Test fun `host source authority exception fails closed before catalog and engine`() {
        val f = fixture()
        f.enqueue()
        val runner = UpdateRunner("test-engine", f.engine, f.store,
            SourceCatalog { error("Original source catalog must not be touched") },
            f.snapshotter, FileQueueLock(f.root.resolve("guarded-queue.lock")),
            allowLegacySource = { throw IllegalStateException("Corrupt Room owner state") })
        assertEquals(RunResult.PolicyBlocked, runner.runNext())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `applies prepared files and verifies readback rather than trusting a receipt`() {
        val f = fixture(); val job = f.enqueue()
        f.engine.beforeApply = {
            assertEquals(TaskState.APPLYING, f.store.list().single().state)
            assertTrue(Files.exists(f.root.resolve("snapshots").resolve(job.id).resolve("base.apk")))
        }
        val result = f.runner().runNext().task()
        assertEquals(TaskState.SUCCEEDED, result.state)
        assertEquals(CompletionKind.APPLIED_AND_VERIFIED, result.completion)
        assertEquals(1, f.engine.calls)
        assertEquals(2L, f.engine.clones.single().versionCode)
        assertFalse(Files.exists(f.root.resolve("snapshots").resolve(job.id)))
        assertEquals(RunResult.Idle, f.runner().runNext())
    }

    @Test fun `a success-shaped engine receipt without installed version is not success`() {
        val f = fixture(); f.enqueue(); f.engine.applyVersion = false
        val result = f.runner().runNext().task()
        assertEquals(TaskState.REVIEW_REQUIRED, result.state)
        assertEquals(TaskIssue.VERSION_NOT_VERIFIED, result.issue)
        assertEquals(1, f.engine.calls)
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(1, f.engine.calls)
    }

    @Test fun `duplicate requests coalesce and terminal records do not erase history`() {
        val f = fixture(); val a = f.enqueue(); val duplicate = f.enqueue()
        assertEquals(a.id, duplicate.id)
        assertEquals(1, f.store.list().size)
        f.runner().runNext()
        val again = f.enqueue()
        assertNotEquals(a.id, again.id)
        assertEquals(CompletionKind.ALREADY_CURRENT, f.runner().runNext().task().completion)
        assertEquals(1, f.engine.calls)
    }

    @Test fun `paused queue persists work without calling engine`() {
        val f = fixture(); f.queue.pause(); f.enqueue()
        assertEquals(RunResult.Paused, f.runner().runNext())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertEquals(0, f.engine.calls)
        f.queue.resume()
        assertEquals(TaskState.SUCCEEDED, f.runner().runNext().task().state)
    }

    @Test fun `pause during copying is checked atomically before the apply marker`() {
        val f = fixture(); val job = f.enqueue()
        f.afterPrepare = { f.queue.pause() }
        assertEquals(TaskIssue.PAUSED, f.runner().runNext().task().issue)
        assertEquals(0, f.engine.calls)
        f.afterPrepare = null; f.queue.resume()
        assertTrue(f.queue.retryDeferred(job.id))
        assertEquals(TaskState.SUCCEEDED, f.runner().runNext().task().state)
    }

    @Test fun `cancel is allowed before execution but not while preparing or applying`() {
        val f = fixture(); val cancelled = f.enqueue()
        assertTrue(f.queue.cancel(cancelled.id))
        assertEquals(RunResult.Idle, f.runner().runNext())
        val active = f.enqueue()
        f.afterPrepare = { assertFalse(f.queue.cancel(active.id)) }
        f.engine.beforeApply = { assertFalse(f.queue.cancel(active.id)) }
        assertEquals(TaskState.SUCCEEDED, f.runner().runNext().task().state)
    }

    @Test fun `unavailable engine leaves queued work pending`() {
        val f = fixture(); f.enqueue()
        f.engine.availability = EngineAvailability.Unavailable("not integrated")
        assertEquals(RunResult.EngineUnavailable, f.runner().runNext())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `foreign only queued work remains untouched instead of ENGINE_CHANGED`() {
        val f = fixture(); val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("different-engine", listOf(target), UpdateScope.PER_CLONE).single()
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(listOf(foreign), f.store.list())
        assertEquals(0, f.engine.calls)
        assertFalse(Files.exists(f.root.resolve("snapshots").resolve(foreign.id)))
    }

    @Test fun `foreign queued same-package work ahead of local task cannot steal this runner`() {
        val f = fixture(); val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("different-engine", listOf(target), UpdateScope.PER_CLONE).single()
        val local = f.enqueue()
        val result = f.runner().runNext().task()
        assertEquals(local.id, result.id)
        assertEquals(TaskState.SUCCEEDED, result.state)
        assertEquals(foreign, f.store.list().first { it.id == foreign.id })
        assertEquals(1, f.engine.calls)
    }

    @Test fun `foreign preparing journal and snapshot survive recovery and local execution`() {
        val f = fixture(); val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("different-engine", listOf(target), UpdateScope.PER_CLONE).single()
        val preparing = foreign.move(TaskState.PREPARING, 501L)
        assertTrue(f.store.replace(foreign, preparing))
        f.files.prepare(foreign.id, checkNotNull(f.source))
        val foreignSnapshot = f.root.resolve("snapshots").resolve(foreign.id)
        assertTrue(Files.exists(foreignSnapshot))
        assertTrue(f.runner().recover())
        assertEquals(preparing, f.store.list().single())
        assertTrue(Files.exists(foreignSnapshot))
        val local = f.enqueue()
        assertEquals(local.id, f.runner().runNext().task().id)
        assertEquals(preparing, f.store.list().first { it.id == foreign.id })
        assertTrue(Files.exists(foreignSnapshot))
        assertEquals(1, f.engine.calls)
    }

    @Test fun `foreign applying task cannot be recovered from matching local engine metadata`() {
        val f = fixture(); val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("different-engine", listOf(target), UpdateScope.PER_CLONE).single()
        val preparing = foreign.move(TaskState.PREPARING, 601L)
        assertTrue(f.store.replace(foreign, preparing))
        f.files.prepare(foreign.id, checkNotNull(f.source))
        val applying = preparing.move(TaskState.APPLYING, 602L,
            affectedIds = listOf("a"), fingerprint = "foreign-unknown-outcome")
        assertTrue(f.store.replace(preparing, applying))
        f.engine.clones[0] = f.engine.clones.single().copy(versionCode = 2L)
        assertTrue(f.runner().recover())
        assertEquals(listOf(applying), f.store.list())
        assertTrue(Files.exists(f.root.resolve("snapshots").resolve(foreign.id)))
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(0, f.engine.calls)
    }

    @Test fun `foreign review-required same-package task does not block local queue`() {
        val f = fixture(); val target = UpdateTarget(checkNotNull(f.source), listOf("a"))
        val foreign = f.queue.enqueue("different-engine", listOf(target), UpdateScope.PER_CLONE).single()
        val preparing = foreign.move(TaskState.PREPARING, 701L)
        assertTrue(f.store.replace(foreign, preparing))
        val applying = preparing.move(TaskState.APPLYING, 702L,
            affectedIds = listOf("a"), fingerprint = "foreign-unverified")
        assertTrue(f.store.replace(preparing, applying))
        val review = applying.move(TaskState.REVIEW_REQUIRED, 703L, TaskIssue.INTERRUPTED)
        assertTrue(f.store.replace(applying, review))
        val local = f.enqueue()
        val result = f.runner().runNext().task()
        assertEquals(local.id, result.id)
        assertEquals(TaskState.SUCCEEDED, result.state)
        assertEquals(review, f.store.list().first { it.id == foreign.id })
        assertEquals(1, f.engine.calls)
    }

    @Test fun `retained private head does not starve unrelated package work`() {
        val f = fixture()
        val retained = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE, retainedSource = true).single()
        val otherSource = checkNotNull(f.source).copy(packageName = "org.example.other")
        val unrelated = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(otherSource, listOf("other-clone"))),
            UpdateScope.PER_CLONE).single()
        val result = f.runner().runNext()
        assertTrue(result is RunResult.Processed)
        assertEquals(unrelated.id, (result as RunResult.Processed).task.id)
        assertEquals(TaskState.QUEUED, f.store.list().single { it.id == retained.id }.state)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `owner only private head does not starve unrelated package work`() {
        val f = fixture()
        val privateTask = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE).single()
        val otherSource = checkNotNull(f.source).copy(packageName = "org.example.other")
        val unrelated = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(otherSource, listOf("other-clone"))),
            UpdateScope.PER_CLONE).single()
        val runner = UpdateRunner("test-engine", f.engine, f.store, f.catalog, f.snapshotter,
            FileQueueLock(f.root.resolve("e54-owner-only-selection.lock")),
            allowLegacySource = { it.packageName != privateTask.packageName })
        val result = runner.runNext()
        assertTrue(result is RunResult.Processed)
        assertEquals(unrelated.id, (result as RunResult.Processed).task.id)
        assertEquals(TaskState.QUEUED, f.store.list().single { it.id == privateTask.id }.state)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `same package original work cannot bypass protected predecessor`() {
        val f = fixture()
        val retained = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(checkNotNull(f.source), listOf("a"))),
            UpdateScope.PER_CLONE, retainedSource = true).single()
        val nextVersion = checkNotNull(f.source).copy(versionCode = checkNotNull(f.source).versionCode + 1)
        val original = f.queue.enqueue("test-engine",
            listOf(UpdateTarget(nextVersion, listOf("a"))),
            UpdateScope.PER_CLONE).single()
        assertNotEquals(retained.id, original.id)
        assertEquals(RunResult.PolicyBlocked, f.runner().runNext())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `scope change is rejected before import`() {
        val f = fixture(); f.enqueue()
        f.engine.availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
        assertEquals(TaskIssue.SCOPE_CHANGED, f.runner().runNext().task().issue)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `scope changed during preparation is rechecked before the external write`() {
        val f = fixture(); f.enqueue()
        f.afterPrepare = { f.engine.availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE) }
        assertEquals(TaskIssue.SCOPE_CHANGED, f.runner().runNext().task().issue)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `removal or newer original after enqueue cannot apply stale files`() {
        for (missing in listOf(true, false)) {
            val f = fixture(); f.enqueue()
            f.source = if (missing) null else f.source!!.copy(versionCode = 3)
            assertEquals(if (missing) TaskIssue.SOURCE_MISSING else TaskIssue.SOURCE_CHANGED,
                f.runner().runNext().task().issue)
            assertEquals(0, f.engine.calls)
        }
    }

    @Test fun `opening an account during source preparation defers the update`() {
        val f = fixture(); f.enqueue()
        f.afterPrepare = { f.engine.clones[0] = f.engine.clones[0].copy(running = true) }
        assertEquals(TaskIssue.CLONE_BUSY, f.runner().runNext().task().issue)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `source changes after preparation prevent apply`() {
        val f = fixture(); f.enqueue()
        f.afterPrepare = { f.source = f.source!!.copy(versionCode = 3) }
        assertEquals(TaskIssue.SOURCE_CHANGED, f.runner().runNext().task().issue)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `process death after apply is reconciled without repeating external operation`() {
        val f = fixture(); val job = f.enqueue()
        f.engine.afterApply = { throw SimulatedProcessDeath() }
        assertThrows(SimulatedProcessDeath::class.java) { f.runner().runNext() }
        assertEquals(TaskState.APPLYING, f.store.list().single().state)
        assertTrue(Files.exists(f.root.resolve("snapshots").resolve(job.id)))
        f.engine.afterApply = null
        assertTrue(f.runner().recover())
        assertEquals(CompletionKind.RECOVERED_VERSION_VERIFIED, f.store.list().single().completion)
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(1, f.engine.calls)
    }

    @Test fun `unknown interrupted outcome is held for review rather than retried`() {
        val f = fixture(); val job = f.enqueue()
        f.engine.beforeApply = { throw SimulatedProcessDeath() }
        assertThrows(SimulatedProcessDeath::class.java) { f.runner().runNext() }
        f.engine.beforeApply = null; f.runner().recover()
        assertEquals(TaskState.REVIEW_REQUIRED, f.store.list().single().state)
        assertFalse(f.queue.cancel(job.id))
        assertFalse(f.queue.retryDeferred(job.id))
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(1, f.engine.calls)
    }

    @Test fun `engine exception retains snapshot and does not automatically retry`() {
        val f = fixture(); val job = f.enqueue()
        f.engine.beforeApply = { throw IllegalStateException("test failure") }
        assertEquals(TaskIssue.ENGINE_OUTCOME_UNKNOWN, f.runner().runNext().task().issue)
        assertTrue(Files.exists(f.root.resolve("snapshots").resolve(job.id)))
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(1, f.engine.calls)
    }

    @Test fun `source exception from inside the external call is still an uncertain outcome`() {
        val f = fixture(); f.enqueue()
        f.engine.beforeApply = { throw SourceChangedException() }
        assertEquals(TaskState.REVIEW_REQUIRED, f.runner().runNext().task().state)
        assertEquals(RunResult.Idle, f.runner().runNext())
        assertEquals(1, f.engine.calls)
    }

    @Test fun `preparing interruption may safely prepare again because apply marker was never written`() {
        val f = fixture(); val job = f.enqueue()
        assertTrue(f.store.replace(job, job.move(TaskState.PREPARING, 100)))
        f.files.prepare(job.id, f.source!!)
        assertTrue(f.runner().recover())
        assertEquals(TaskState.QUEUED, f.store.list().single().state)
        assertFalse(Files.exists(f.root.resolve("snapshots").resolve(job.id)))
        assertEquals(TaskState.SUCCEEDED, f.runner().runNext().task().state)
        assertEquals(1, f.engine.calls)
    }

    @Test fun `failed journal write before apply cannot call the engine`() {
        val f = fixture(); f.enqueue()
        f.store.beforeReplace = { if (it.state == TaskState.APPLYING) throw java.io.IOException("test disk failure") }
        assertEquals(TaskState.FAILED, f.runner().runNext().task().state)
        assertEquals(0, f.engine.calls)
    }

    @Test fun `failure to persist verify after an external write still cannot duplicate it`() {
        val f = fixture(); f.enqueue()
        f.store.beforeReplace = { if (it.state == TaskState.VERIFYING) throw SimulatedProcessDeath() }
        assertThrows(SimulatedProcessDeath::class.java) { f.runner().runNext() }
        f.store.beforeReplace = null
        f.runner().recover()
        assertEquals(TaskState.SUCCEEDED, f.store.list().single().state)
        assertEquals(1, f.engine.calls)
    }

    @Test fun `OS lock prevents two runner instances from applying concurrently`() {
        val f = fixture(); f.enqueue()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.engine.beforeApply = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<RunResult> { f.runner().runNext() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(RunResult.Busy, f.runner().runNext())
            release.countDown()
            assertEquals(TaskState.SUCCEEDED, first.get(5, TimeUnit.SECONDS).task().state)
            assertEquals(1, f.engine.calls)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `shared update accounts for an unselected active sibling`() {
        val f = fixture()
        f.engine.availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
        f.engine.clones += CloneRecord("b", "test.social", 1, setOf("signer"), true)
        f.enqueue(listOf("a"), UpdateScope.SHARED_PACKAGE)
        assertEquals(TaskIssue.CLONE_BUSY, f.runner().runNext().task().issue)
        assertEquals(0, f.engine.calls)
    }
}
