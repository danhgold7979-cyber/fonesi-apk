package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.apk.ClusterOwnerType
import vn.fonesi.core.InstalledSource
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.UpdateTarget
import vn.fonesi.core.update.TaskIssue
import vn.fonesi.core.update.TaskState
import vn.fonesi.core.update.UpdateQueue
import vn.fonesi.core.update.UpdateTask
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class EngineScopedQueueControlTest {
    private fun <T> onIo(executor: ExecutorService, action: () -> T): T =
        executor.submit(Callable { action() }).get(30, TimeUnit.SECONDS)

    @Test fun foreignRoomQueueControlsCannotErasePrivateOwnersOrRequeueOldEngineHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executors.newSingleThreadExecutor()
        val name = "e55-engine-controls-${UUID.randomUUID()}.db"
        var db = onIo(executor) { FonesiDatabase.open(context, name) }
        try {
            val (foreignRetained, foreignDeferred, local) = onIo(executor) {
                val store = RoomUpdateTaskStore(db)
                val queue = UpdateQueue(store)
                val source = InstalledSource("test.alpha", 2L, setOf("project-test-signer"),
                    listOf("/test-only/base.apk"))
                val retained = queue.enqueue("older-engine",
                    listOf(UpdateTarget(source, listOf("foreign-a"))), UpdateScope.PER_CLONE,
                    retainedSource = true).single()
                val clusterId = UUID.randomUUID().toString()
                assertTrue(db.clusterReferences().insert(ClusterReferenceRow(
                    clusterId, ClusterOwnerType.UPDATE_TASK.name, retained.id,
                    "older-engine", 100L)) > 0)

                val deferredSource = source.copy(packageName = "test.beta")
                val oldDeferred = queue.enqueue("older-engine",
                    listOf(UpdateTarget(deferredSource, listOf("foreign-b"))),
                    UpdateScope.PER_CLONE).single()
                val preparing = oldDeferred.move(TaskState.PREPARING, 101L)
                assertTrue(store.replace(oldDeferred, preparing))
                val deferred = preparing.move(TaskState.DEFERRED, 102L, TaskIssue.PAUSED)
                assertTrue(store.replace(preparing, deferred))

                val current = queue.enqueue("current-engine",
                    listOf(UpdateTarget(source, listOf("local-a"))), UpdateScope.PER_CLONE).single()

                assertFalse(queue.cancel("current-engine", retained.id))
                assertFalse(queue.retryDeferred("current-engine", deferred.id))
                assertFalse(queue.cancel("current-engine", deferred.id))
                assertEquals(retained, store.list().first { it.id == retained.id })
                assertEquals(deferred, store.list().first { it.id == deferred.id })
                assertEquals(1, db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name,
                    retained.id, "older-engine").size)
                assertTrue(queue.cancel("current-engine", current.id))
                Triple(retained, deferred, current)
            }

            onIo(executor) { db.close() }
            db = onIo(executor) { FonesiDatabase.open(context, name) }
            onIo(executor) {
                val store = RoomUpdateTaskStore(db)
                val tasks = store.list().associateBy(UpdateTask::id)
                assertEquals(foreignRetained, tasks.getValue(foreignRetained.id))
                assertEquals(foreignDeferred, tasks.getValue(foreignDeferred.id))
                assertEquals(TaskState.CANCELLED, tasks.getValue(local.id).state)
                assertEquals(1, db.clusterReferences().forOwner(ClusterOwnerType.UPDATE_TASK.name,
                    foreignRetained.id, "older-engine").size)
                val queue = UpdateQueue(store)
                assertFalse(queue.cancel("current-engine", foreignRetained.id))
                assertFalse(queue.retryDeferred("current-engine", foreignDeferred.id))
                assertEquals(foreignRetained, store.list().first { it.id == foreignRetained.id })
                assertEquals(foreignDeferred, store.list().first { it.id == foreignDeferred.id })
            }
        } finally {
            runCatching { onIo(executor) { db.close() } }
            context.deleteDatabase(name)
            executor.shutdownNow()
        }
    }
}
