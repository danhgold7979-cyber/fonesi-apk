package vn.fonesi.app.storage

import android.content.Context
import android.app.Application
import vn.fonesi.app.AndroidSourceCatalog
import vn.fonesi.app.shortcuts.CloneLaunchController
import vn.fonesi.app.shortcuts.AndroidCloneShortcuts
import vn.fonesi.core.UnavailableCloneEngine
import vn.fonesi.app.runtime.RuntimeBindings
import vn.fonesi.app.security.AccessBroker
import vn.fonesi.app.update.UpdateRequestService
import vn.fonesi.app.update.AutoUpdatePolicyStore
import vn.fonesi.app.update.AutoUpdateMode
import vn.fonesi.app.update.ForegroundAutoUpdater
import vn.fonesi.core.update.*

/** Application-scoped persistence/orchestration; candidate runtime still requires explicit opt-in. */
class LocalServices private constructor(private val context: Application) {
    val database = FonesiDatabase.open(context)
    val engine = RuntimeBindings.engine(context.applicationContext, database)
    val tasks = RoomUpdateTaskStore(database)
    val queue = UpdateQueue(tasks)
    val engineKey = RuntimeBindings.key(context)
    val launches by lazy { CloneLaunchController(database, engineKey, engine, operationLock()) { AccessBroker.get(context).hasForegroundAccess() } }
    val shortcuts by lazy { AndroidCloneShortcuts(context.applicationContext, database) }
    val updateRequests by lazy { UpdateRequestService(database, engineKey, engine, AndroidSourceCatalog(packageManager),
        tasks, operationLock()) { AccessBroker.get(context).hasForegroundAccess() } }
    val autoPolicy by lazy { AutoUpdatePolicyStore(context) }
    val automatic by lazy { ForegroundAutoUpdater(database, engineKey, autoPolicy, updateRequests, tasks,
        FileQueueLock(java.io.File(contextFiles, "updates/auto.lock").toPath()), operationLock(),
        { AccessBroker.get(context).hasForegroundAccess() }, { runner { autoPolicy.read().mode == AutoUpdateMode.APPLY_WHILE_OPEN } }) }

    fun operationLock() = FileQueueLock(java.io.File(contextFiles, "updates/queue.lock").toPath())

    fun metadata(engineKey: String) = CloneMetadataRepository(database, engineKey,
        if (engineKey == this.engineKey) engine else UnavailableCloneEngine(), operationLock())

    fun runner(allowed: () -> Boolean = { true }): UpdateRunner {
        val root = java.io.File(contextFiles, "updates").toPath()
        val catalog = AndroidSourceCatalog(packageManager)
        return UpdateRunner(engineKey, engine, tasks, catalog,
            FileSourceSnapshotter(root.resolve("snapshots"), catalog), operationLock(),
            mayApply = { AccessBroker.get(context).hasForegroundAccess() && allowed() },
            allowLegacySource = RoomLegacyUpdateSourcePolicy(database)::allows)
    }

    private val contextFiles = context.filesDir
    private val packageManager = context.packageManager

    companion object {
        @Volatile private var instance: LocalServices? = null
        fun get(context: Context): LocalServices = instance ?: synchronized(this) {
            instance ?: LocalServices(context.applicationContext as Application).also { instance = it }
        }
    }
}
