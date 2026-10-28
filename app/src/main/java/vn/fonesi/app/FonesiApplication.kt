package vn.fonesi.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import vn.fonesi.app.update.UpdateJournalRecoveryWorker
import java.util.concurrent.TimeUnit

/**
 * Application startup schedules journal-only recovery; it must never initialize the clone SDK.
 * WorkManager can create this Application while Fonesi is backgrounded or locked.
 */
class FonesiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val work = try {
            WorkManager.getInstance(this)
        } catch (_: IllegalStateException) {
            // AndroidX Startup normally initializes WorkManager before Application.onCreate().
            // Some host/test environments omit the initializer; initialize only in that case.
            WorkManager.initialize(this, Configuration.Builder().build())
            WorkManager.getInstance(this)
        }
        work.enqueueUniqueWork(
            STARTUP_RECOVERY,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateJournalRecoveryWorker>().build(),
        )
        work.enqueueUniquePeriodicWork(
            PERIODIC_RECOVERY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateJournalRecoveryWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    private companion object {
        const val STARTUP_RECOVERY = "fonesi-update-journal-startup-recovery"
        const val PERIODIC_RECOVERY = "fonesi-update-journal-periodic-recovery"
    }
}
