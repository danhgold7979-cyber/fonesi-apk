package vn.fonesi.app.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.fonesi.core.update.*
import java.util.concurrent.Callable

class RoomUpdateTaskStore(private val db: FonesiDatabase) : UpdateTaskStore {
    fun observe(): Flow<List<UpdateTask>> = db.tasks().observe().map { rows -> rows.map(TaskCodec::decode) }
    fun observePaused(): Flow<Boolean> = db.tasks().observePaused().map { it ?: false }
    override fun list(): List<UpdateTask> = db.tasks().list().map(TaskCodec::decode)

    override fun insertOrGetActive(task: UpdateTask): UpdateTask = db.runInTransaction(Callable {
        require(task.state == TaskState.QUEUED && task.revision == 0L)
        db.tasks().findActive(task.dedupKey)?.let { return@Callable TaskCodec.decode(it) }
        val inserted = db.tasks().insert(TaskCodec.row(task))
        if (inserted == -1L) {
            return@Callable TaskCodec.decode(checkNotNull(db.tasks().findActive(task.dedupKey)))
        }
        task
    })

    override fun replace(expected: UpdateTask, replacement: UpdateTask, requireUnpaused: Boolean): Boolean =
        db.runInTransaction(Callable {
            replacement.requireSuccessorOf(expected)
            val current = db.tasks().find(expected.id)?.let(TaskCodec::decode) ?: return@Callable false
            if (current != expected || (requireUnpaused && isPaused())) return@Callable false
            val row = TaskCodec.row(replacement)
            db.tasks().replace(expected.id, expected.revision, expected.state.name, row.state,
                row.revision, row.activeKey, row.payload) == 1
        })

    override fun isPaused() = db.tasks().paused() ?: false
    override fun setPaused(paused: Boolean) = db.tasks().setControl(QueueControlRow(paused = paused))
}
