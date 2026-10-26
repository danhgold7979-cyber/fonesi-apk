package vn.fonesi.app.storage

import android.content.Context
import vn.fonesi.app.AndroidSourceCatalog
import vn.fonesi.core.UnavailableCloneEngine
import vn.fonesi.core.update.*

/** Real persistence/orchestration; no production virtualization provider is enabled yet. */
class LocalServices private constructor(context: Context) {
    val engine = UnavailableCloneEngine()
    val database = FonesiDatabase.open(context)
    val tasks = RoomUpdateTaskStore(database)
    val queue = UpdateQueue(tasks)

    fun metadata(engineKey: String) = CloneMetadataRepository(database, engineKey, engine)

    fun runner(): UpdateRunner {
        val root = java.io.File(contextFiles, "updates").toPath()
        val catalog = AndroidSourceCatalog(packageManager)
        return UpdateRunner("not-integrated", engine, tasks, catalog,
            FileSourceSnapshotter(root.resolve("snapshots"), catalog), FileQueueLock(root.resolve("queue.lock")))
    }

    private val contextFiles = context.filesDir
    private val packageManager = context.packageManager

    companion object {
        @Volatile private var instance: LocalServices? = null
        fun get(context: Context): LocalServices = instance ?: synchronized(this) {
            instance ?: LocalServices(context.applicationContext).also { instance = it }
        }
    }
}
