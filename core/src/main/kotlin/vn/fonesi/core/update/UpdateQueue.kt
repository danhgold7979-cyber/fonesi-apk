package vn.fonesi.core.update

import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget

class UpdateQueue(
    private val store: UpdateTaskStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun enqueue(
        engineKey: String,
        targets: List<UpdateTarget>,
        scope: UpdateScope,
        retainedSource: Boolean = false,
    ): List<UpdateTask> =
        targets.map {
            store.insertOrGetActive(UpdateTask.create(engineKey, it, scope, clock())
                .copy(retainedSource = retainedSource))
        }

    fun pause() = store.setPaused(true)
    fun resume() = store.setPaused(false)

    fun retryDeferred(id: String): Boolean {
        val current = store.list().firstOrNull { it.id == id } ?: return false
        if (current.state != TaskState.DEFERRED) return false
        return store.replace(current, current.move(TaskState.QUEUED, clock()))
    }

    /** Never cancels an in-flight or uncertain engine operation. */
    fun cancel(id: String): Boolean {
        val current = store.list().firstOrNull { it.id == id } ?: return false
        if (current.state !in setOf(TaskState.QUEUED, TaskState.DEFERRED)) return false
        return store.replace(current, current.move(TaskState.CANCELLED, clock()))
    }
}
