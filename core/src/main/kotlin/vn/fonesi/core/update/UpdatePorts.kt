package vn.fonesi.core.update

import vn.fonesi.core.InstalledSource

fun interface SourceCatalog {
    fun current(packageName: String): InstalledSource?
}

/** Implementations must persist before returning and return tasks in enqueue order. */
interface UpdateTaskStore {
    fun list(): List<UpdateTask>
    fun insertOrGetActive(task: UpdateTask): UpdateTask
    /** Atomic revision CAS, optionally in the same transaction as checking the pause flag. */
    fun replace(expected: UpdateTask, replacement: UpdateTask, requireUnpaused: Boolean = false): Boolean
    fun isPaused(): Boolean
    fun setPaused(paused: Boolean)
}

data class SnapshotFile(val name: String, val size: Long, val sha256: String)
data class PreparedSource(val source: InstalledSource, val fingerprint: String, val files: List<SnapshotFile>)

interface SourceSnapshotter {
    /** Called only before the write-ahead APPLYING marker, under the queue's exclusive lock. */
    fun prepare(taskId: String, expected: InstalledSource): PreparedSource
    fun verify(snapshot: PreparedSource): Boolean
    fun discard(taskId: String)
}

fun interface QueueLock {
    /** Null means another runner owns the lock. It must be released on process exit. */
    fun acquire(): AutoCloseable?
}

class SourceChangedException : IllegalStateException("Installed source changed during preparation")
