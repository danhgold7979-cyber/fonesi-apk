package vn.fonesi.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UpdateJournalRecoverySchedulerTest {
    @Test fun repeatedUnlockedSchedulingKeepsUniqueWorkAndLaterEntryCanReconcileAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        val workManager = WorkManager.getInstance(context)
        try {
            val scheduler = UpdateJournalRecoveryScheduler
            scheduler.schedule(workManager)
            scheduler.schedule(workManager)

            val startupBeforeReentry = workManager
                .getWorkInfosForUniqueWork(STARTUP_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertTrue("Startup unique work should have a recorded request", startupBeforeReentry.isNotEmpty())
            assertTrue("Two unlocked entries must keep at most one unfinished startup request",
                startupBeforeReentry.count { !it.state.isFinished } <= 1)
            startupBeforeReentry.forEach {
                assertTrue("Startup request must target the journal-only worker",
                    it.tags.contains(UpdateJournalRecoveryWorker::class.java.name))
            }

            val periodicBeforeReentry = workManager
                .getWorkInfosForUniqueWork(PERIODIC_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertTrue("Periodic unique work should have a recorded request", periodicBeforeReentry.isNotEmpty())
            assertTrue("Repeated calls must keep at most one unfinished periodic request",
                periodicBeforeReentry.count { !it.state.isFinished } <= 1)
            periodicBeforeReentry.forEach {
                assertTrue("Periodic request must target the journal-only worker",
                    it.tags.contains(UpdateJournalRecoveryWorker::class.java.name))
            }

            val priorIds = startupBeforeReentry.map { it.id }.toSet()
            val requestToFinish = startupBeforeReentry.lastOrNull { !it.state.isFinished }
                ?: startupBeforeReentry.last()
            workManager.cancelWorkById(requestToFinish.id).result.get(10, TimeUnit.SECONDS)
            awaitTerminal(workManager, STARTUP_RECOVERY, requestToFinish.id)

            scheduler.schedule(workManager)
            val afterReentry = workManager
                .getWorkInfosForUniqueWork(STARTUP_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertTrue(
                "A later unlocked manager entry must enqueue fresh startup reconciliation after a terminal request",
                afterReentry.any { it.id !in priorIds },
            )
            assertTrue("Re-entry must not leave duplicate unfinished startup requests",
                afterReentry.count { !it.state.isFinished } <= 1)
            val periodicAfterReentry = workManager
                .getWorkInfosForUniqueWork(PERIODIC_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertTrue("Periodic recovery remains unique after another manager entry",
                periodicAfterReentry.count { !it.state.isFinished } <= 1)
        } finally {
            runCatching { workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS) }
            runCatching { WorkManagerTestInitHelper.closeWorkDatabase() }
        }
    }

    private fun awaitTerminal(workManager: WorkManager, name: String, id: UUID) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val current = workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
                .firstOrNull { it.id == id }
            if (current == null || current.state.isFinished) return
            Thread.sleep(20)
        }
        val final = workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            .firstOrNull { it.id == id }
        assertFalse("Cancelled startup request should become terminal; state=${final?.state}",
            final != null && !final.state.isFinished)
    }

    private companion object {
        const val STARTUP_RECOVERY = "fonesi-update-journal-startup-recovery"
        const val PERIODIC_RECOVERY = "fonesi-update-journal-periodic-recovery"
    }
}
