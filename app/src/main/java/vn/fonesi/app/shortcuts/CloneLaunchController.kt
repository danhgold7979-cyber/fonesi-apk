package vn.fonesi.app.shortcuts

import vn.fonesi.app.storage.CloneMetadataRow
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.app.storage.RoomUpdateTaskStore
import vn.fonesi.core.CloneEngine
import vn.fonesi.core.EngineAvailability
import vn.fonesi.core.update.QueueLock
import vn.fonesi.core.update.TaskState

enum class LaunchBlock {
    INVALID_LINK, CLONE_MISSING, ENGINE_UNAVAILABLE, ENGINE_CHANGED, IDENTITY_CHANGED,
    UPDATE_UNRESOLVED, OPERATION_BUSY, STORAGE_ERROR, ENGINE_ERROR, LOCKED,
}

sealed interface LaunchResult {
    /** A verified target for a pin request, or a dispatched engine request; neither proves guest UI. */
    data class Accepted(val clone: CloneMetadataRow) : LaunchResult
    data class Blocked(val reason: LaunchBlock) : LaunchResult
}

/** Background-thread boundary shared by shortcut and in-app launch. Never falls back to the original. */
class CloneLaunchController(
    private val db: FonesiDatabase,
    private val engineKey: String,
    private val engine: CloneEngine,
    private val lock: QueueLock,
    private val authorized: () -> Boolean = { true },
) {
    fun inspect(token: String): LaunchResult = guarded(token, dispatch = false)
    fun launch(token: String): LaunchResult = guarded(token, dispatch = true)

    private fun guarded(token: String, dispatch: Boolean): LaunchResult {
        if (!authorized()) return blocked(LaunchBlock.LOCKED)
        if (!CloneRoute.validToken(token)) return blocked(LaunchBlock.INVALID_LINK)
        val lease = try { lock.acquire() } catch (_: Exception) { return blocked(LaunchBlock.STORAGE_ERROR) }
            ?: return blocked(LaunchBlock.OPERATION_BUSY)
        return try { lease.use { dispatchVerified(token, dispatch) } }
        catch (_: Exception) { blocked(LaunchBlock.STORAGE_ERROR) }
    }

    private fun dispatchVerified(token: String, dispatch: Boolean): LaunchResult {
        val saved = try { db.clones().findByLaunchToken(token) }
            catch (_: Exception) { return blocked(LaunchBlock.STORAGE_ERROR) }
            ?: return blocked(LaunchBlock.CLONE_MISSING)
        if (saved.engineKey != engineKey) return blocked(LaunchBlock.ENGINE_CHANGED)
        val unresolved = try {
            RoomUpdateTaskStore(db).list().any {
                it.packageName == saved.packageName && it.state in setOf(
                    TaskState.PREPARING, TaskState.APPLYING, TaskState.VERIFYING, TaskState.REVIEW_REQUIRED,
                )
            }
        } catch (_: Exception) { return blocked(LaunchBlock.STORAGE_ERROR) }
        if (unresolved) return blocked(LaunchBlock.UPDATE_UNRESOLVED)
        return try {
            if (engine.availability !is EngineAvailability.Ready) return blocked(LaunchBlock.ENGINE_UNAVAILABLE)
            val records = engine.listClones()
            if (records.map { it.id }.distinct().size != records.size) return blocked(LaunchBlock.IDENTITY_CHANGED)
            val live = records.singleOrNull { it.id == saved.cloneId } ?: return blocked(LaunchBlock.CLONE_MISSING)
            if (live.packageName != saved.packageName) return blocked(LaunchBlock.IDENTITY_CHANGED)
            if (!authorized()) return blocked(LaunchBlock.LOCKED)
            if (dispatch) engine.launch(saved.cloneId)
            LaunchResult.Accepted(saved)
        } catch (_: Exception) { blocked(LaunchBlock.ENGINE_ERROR) }
    }

    private fun blocked(reason: LaunchBlock) = LaunchResult.Blocked(reason)
}
