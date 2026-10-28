package vn.fonesi.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UpdateJournalRecoverySchedulerTest {
    @Test fun repeatedUnlockedSchedulingKeepsUniqueWorkAndLaterEntryCanReconcileAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? =
                if (workerClassName == UpdateJournalRecoveryWorker::class.java.name) {
                    ControlledJournalWorker(appContext, workerParameters, workerStarted, releaseWorker)
                } else {
                    null
                }
        }
        val configuration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            configuration,
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        val workManager = WorkManager.getInstance(context)
        try {
            val scheduler = UpdateJournalRecoveryScheduler
            scheduler.schedule(workManager)
            scheduler.schedule(workManager)

            val startupBeforeRelease = workManager
                .getWorkInfosForUniqueWork(STARTUP_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertEquals("Two unlocked entries must keep one unfinished startup request",
                1, startupBeforeRelease.count { !it.state.isFinished })
            val startup = startupBeforeRelease.single { !it.state.isFinished }
            assertTrue(startup.state == WorkInfo.State.ENQUEUED || startup.state == WorkInfo.State.RUNNING)
            assertTrue("Startup request must target the journal-only worker",
                startup.tags.contains(UpdateJournalRecoveryWorker::class.java.name))

            val periodicBeforeRelease = workManager
                .getWorkInfosForUniqueWork(PERIODIC_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertEquals("Repeated calls must keep one unfinished periodic request",
                1, periodicBeforeRelease.count { !it.state.isFinished })
            val periodic = periodicBeforeRelease.single { !it.state.isFinished }
            assertTrue(periodic.state == WorkInfo.State.ENQUEUED || periodic.state == WorkInfo.State.RUNNING)
            assertTrue("Periodic request must target the journal-only worker",
                periodic.tags.contains(UpdateJournalRecoveryWorker::class.java.name))

            val testDriver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
            testDriver.setAllConstraintsMet(startup.id)
            testDriver.setAllConstraintsMet(periodic.id)
            assertTrue("A controlled journal worker should start for scheduling assertions",
                workerStarted.await(10, TimeUnit.SECONDS))

            releaseWorker.countDown()
            val completedStartup = awaitStartupCompletion(workManager)
            assertTrue("Startup recovery should complete with the controlled worker",
                completedStartup.any { it.id == startup.id && it.state == WorkInfo.State.SUCCEEDED })

            scheduler.schedule(workManager)
            val afterReentry = workManager
                .getWorkInfosForUniqueWork(STARTUP_RECOVERY)
                .get(10, TimeUnit.SECONDS)
            assertTrue(
                "A later unlocked manager entry must enqueue fresh startup reconciliation after completion",
                afterReentry.any { it.id != startup.id },
            )
            assertEquals("Periodic recovery remains unique after another manager entry",
                1,
                workManager.getWorkInfosForUniqueWork(PERIODIC_RECOVERY)
                    .get(10, TimeUnit.SECONDS).count { !it.state.isFinished },
            )
        } finally {
            releaseWorker.countDown()
            runCatching { workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS) }
            runCatching { WorkManagerTestInitHelper.closeWorkDatabase() }
        }
    }

    private fun awaitStartupCompletion(workManager: WorkManager): List<WorkInfo> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var latest = emptyList<WorkInfo>()
        while (System.nanoTime() < deadline) {
            latest = workManager.getWorkInfosForUniqueWork(STARTUP_RECOVERY).get(10, TimeUnit.SECONDS)
            if (latest.any { it.state == WorkInfo.State.SUCCEEDED }) return latest
            Thread.sleep(20)
        }
        return latest
    }

    private class ControlledJournalWorker(
        context: Context,
        parameters: WorkerParameters,
        private val started: CountDownLatch,
        private val release: CountDownLatch,
    ) : UpdateJournalRecoveryWorker(context, parameters) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            started.countDown()
            if (release.await(30, TimeUnit.SECONDS)) Result.success() else Result.failure()
        }
    }

    private companion object {
        const val STARTUP_RECOVERY = "fonesi-update-journal-startup-recovery"
        const val PERIODIC_RECOVERY = "fonesi-update-journal-periodic-recovery"
    }
}
