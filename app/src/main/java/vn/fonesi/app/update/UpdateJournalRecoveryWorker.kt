package vn.fonesi.app.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.app.storage.RoomUpdateTaskStore
import vn.fonesi.core.update.FileQueueLock
import vn.fonesi.core.update.FileSourceSnapshotter
import vn.fonesi.core.update.SourceCatalog
import vn.fonesi.core.update.UpdateJournalRecovery
import java.io.File

/**
 * Background process may repair journal metadata only. It deliberately has no dependency on
 * RuntimeBindings, LocalServices, PackageManager, the clone engine, or guest/account APIs.
 */
class UpdateJournalRecoveryWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var database: FonesiDatabase? = null
        try {
            val context = applicationContext
            database = FonesiDatabase.open(context)
            recoverJournal(database, File(context.filesDir, "updates"))
        } catch (_: Exception) {
            // No sensitive task/source details in WorkManager output. A later run may retry safely.
            Result.retry()
        } finally {
            database?.close()
        }
    }

    /**
     * Narrow test seam: the caller supplies an already-open database and private root.
     * WorkManager input cannot choose either. Production doWork() always opens the fixed
     * production database and uses applicationContext.filesDir/updates.
     */
    internal fun recoverJournal(database: FonesiDatabase, updates: File): Result {
        return try {
            val updatesPath = updates.toPath()
            val tasks = RoomUpdateTaskStore(database)
            // Recovery only calls discard(); SourceCatalog is never consulted in this worker.
            val snapshots = FileSourceSnapshotter(updatesPath.resolve("snapshots"), SourceCatalog { null })
            val result = UpdateJournalRecovery(
                tasks,
                snapshots,
                FileQueueLock(updatesPath.resolve("queue.lock")),
            ).recover()
            if (!result.lockAcquired || result.retryable) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
