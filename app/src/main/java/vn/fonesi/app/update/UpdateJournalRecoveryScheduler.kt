package vn.fonesi.app.update

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Call only after Fonesi is visible and unlocked. Once registered, periodic journal recovery
 * continues while the UI is closed. The worker itself never initializes the clone runtime.
 */
object UpdateJournalRecoveryScheduler {
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val work = try {
            WorkManager.getInstance(app)
        } catch (_: IllegalStateException) {
            // Some test/host environments omit AndroidX Startup. Real Android initializes it
            // before Activity launch; initialize only when no instance exists.
            WorkManager.initialize(app, Configuration.Builder().build())
            WorkManager.getInstance(app)
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

    private const val STARTUP_RECOVERY = "fonesi-update-journal-startup-recovery"
    private const val PERIODIC_RECOVERY = "fonesi-update-journal-periodic-recovery"
}
