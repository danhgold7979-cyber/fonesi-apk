package vn.fonesi.app.storage

import android.content.Context
import vn.fonesi.app.AndroidSourceCatalog
import vn.fonesi.app.shortcuts.CloneLaunchController
import vn.fonesi.app.shortcuts.AndroidCloneShortcuts
import vn.fonesi.core.UnavailableCloneEngine
import vn.fonesi.app.runtime.RuntimeBindings
import vn.fonesi.core.update.*

/** Real persistence/orchestration; no production virtualization provider is enabled yet. */
class LocalServices private constructor(context: Context) {
    val database = FonesiDatabase.open(context)
    val engine = RuntimeBindings.engine(context.applicationContext, database)
    val tasks = RoomUpdateTaskStore(database)
    val queue = UpdateQueue(tasks)
    val engineKey = RuntimeBindings.key(context)
    val launches by lazy { CloneLaunchController(database, engineKey, engine, operationLock()) }
    val shortcuts by lazy { AndroidCloneShortcuts(context.applicationContext, database) }

    fun operationLock() = FileQueueLock(java.io.File(contextFiles, "updates/queue.lock").toPath())

    fun metadata(engineKey: String) = CloneMetadataRepository(database, engineKey,
        if (engineKey == this.engineKey) engine else UnavailableCloneEngine(), operationLock())

    fun runner(): UpdateRunner {
        val root = java.io.File(contextFiles, "updates").toPath()
        val catalog = AndroidSourceCatalog(packageManager)
        return UpdateRunner(engineKey, engine, tasks, catalog,
            FileSourceSnapshotter(root.resolve("snapshots"), catalog), operationLock())
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
