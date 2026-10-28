package vn.fonesi.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.app.storage.RoomUpdateTaskStore
import vn.fonesi.core.UpdateScope
import vn.fonesi.core.update.CompletionKind
import vn.fonesi.core.update.TaskIssue
import vn.fonesi.core.update.TaskState
import vn.fonesi.core.update.UpdateTask
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateJournalRecoveryWorkerTest {
    @Test fun idleJournalRecoveryCompletesWithoutStartingCloneEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<UpdateJournalRecoveryWorker>(context).build()
        val result = worker.startWork().get(30, TimeUnit.SECONDS)
        assertTrue("Journal-only worker should succeed when recovery has no retryable work",
            result is ListenableWorker.Result.Success)
    }

    @Test fun workerRecoversRealRoomRowsAndPrivateSnapshotsIdempotently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val io = Executors.newSingleThreadExecutor()
        val suffix = UUID.randomUUID().toString()
        val databaseName = "e27-$suffix.db"
        val testRoot = File(context.filesDir, "e27-$suffix/updates")
        val snapshotsRoot = File(testRoot, "snapshots")
        val db = onIo(io) { FonesiDatabase.open(context, databaseName) }
        try {
            val store = RoomUpdateTaskStore(db)
            val seeded = onIo(io) {
                val preparing = seed(store, TaskState.PREPARING)
                val applying = seed(store, TaskState.APPLYING)
                val verifying = seed(store, TaskState.VERIFYING)
                val review = seed(store, TaskState.REVIEW_REQUIRED)
                val queued = seed(store, TaskState.QUEUED)
                val failed = seed(store, TaskState.FAILED)
                val cancelled = seed(store, TaskState.CANCELLED)
                store.setPaused(true)
                listOf(preparing, applying, verifying, review, queued, failed, cancelled)
            }
            val (preparing, applying, verifying, review, queued, failed, cancelled) = seeded

            File(snapshotsRoot, preparing.id).apply {
                mkdirs()
                File(this, "partial.tmp").writeText("synthetic incomplete snapshot")
            }
            listOf(applying, verifying).forEach { task ->
                File(snapshotsRoot, task.id).apply {
                    mkdirs()
                    File(this, "prepared.apk").writeText("synthetic prepared snapshot")
                }
            }

            val worker = TestListenableWorkerBuilder<UpdateJournalRecoveryWorker>(context).build()
            val first = onIo(io) { worker.recoverJournal(db, testRoot) }
            assertTrue("Expected successful recovery", first is ListenableWorker.Result.Success)

            val recovered = onIo(io) { store.list().associateBy { it.id } }
            assertEquals(TaskState.QUEUED, recovered.getValue(preparing.id).state)
            assertEquals(preparing.revision + 1, recovered.getValue(preparing.id).revision)
            assertTrue(recovered.getValue(preparing.id).affectedIds.isEmpty())
            assertEquals(null, recovered.getValue(preparing.id).snapshotFingerprint)
            assertEquals(null, recovered.getValue(preparing.id).issue)
            assertFalse("Only the PREPARING task snapshot should be removed",
                File(snapshotsRoot, preparing.id).exists())

            listOf(applying, verifying).forEach { original ->
                val current = recovered.getValue(original.id)
                assertEquals(TaskState.REVIEW_REQUIRED, current.state)
                assertEquals(TaskIssue.INTERRUPTED, current.issue)
                assertEquals(original.revision + 1, current.revision)
                assertTrue("Uncertain update snapshot must be retained",
                    File(snapshotsRoot, original.id).isDirectory)
            }
            assertEquals(review, recovered.getValue(review.id))
            assertEquals(queued, recovered.getValue(queued.id))
            assertEquals(failed, recovered.getValue(failed.id))
            assertEquals(cancelled, recovered.getValue(cancelled.id))
            assertTrue("Recovery must preserve the user's paused queue", onIo(io) { store.isPaused() })

            val afterFirstRun = recovered
            val secondWorker = TestListenableWorkerBuilder<UpdateJournalRecoveryWorker>(context).build()
            val second = onIo(io) { secondWorker.recoverJournal(db, testRoot) }
            assertTrue(second is ListenableWorker.Result.Success)
            assertEquals("Second recovery must be idempotent", afterFirstRun,
                onIo(io) { store.list().associateBy { it.id } })
            assertTrue(onIo(io) { store.isPaused() })
        } finally {
            onIo(io) { db.close() }
            context.deleteDatabase(databaseName)
            testRoot.parentFile?.deleteRecursively()
            io.shutdownNow()
        }
    }

    @Test fun workerSourceHasNoRuntimeActivationDependency() {
        val source = File(
            "src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryWorker.kt"
        ).readText().replace(Regex("""/\*[\s\S]*?\*/|//[^\r\n]*"""), "")
        assertTrue(source.contains("UpdateJournalRecovery("))
        assertTrue(source.contains("RoomUpdateTaskStore"))
        assertTrue(!source.contains("RuntimeBindings"))
        assertTrue(!source.contains("LocalServices"))
        assertTrue(!source.contains("FCore"))
        assertTrue(!source.contains("PackageManager"))
        assertTrue(!source.contains("AndroidSourceCatalog"))
        assertTrue(source.contains("internal fun recoverJournal"))
        assertTrue(!source.contains("inputData"))
    }

    private fun <T> onIo(io: java.util.concurrent.ExecutorService, task: () -> T): T =
        io.submit(Callable(task)).get(30, TimeUnit.SECONDS)

    private fun seed(store: RoomUpdateTaskStore, initial: TaskState): UpdateTask {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val queued = UpdateTask(
            id = id,
            engineKey = "synthetic-test-engine",
            packageName = "vn.fonesi.synthetic",
            cloneIds = listOf("synthetic-clone-$id"),
            versionCode = 42L,
            signerDigests = setOf("synthetic-sha256"),
            scope = UpdateScope.PER_CLONE,
            dedupKey = "synthetic-dedup-$id",
            createdAt = now,
        )
        store.insertOrGetActive(queued)
        var current = queued
        if (initial == TaskState.CANCELLED) {
            val cancelled = queued.move(TaskState.CANCELLED, now + 2)
            check(store.replace(queued, cancelled))
            return cancelled
        }
        if (initial != TaskState.QUEUED) {
            current = current.move(TaskState.PREPARING, now + 1)
            check(store.replace(queued, current))
        }
        when (initial) {
            TaskState.QUEUED -> Unit
            TaskState.PREPARING -> Unit
            TaskState.APPLYING -> {
                val next = current.move(TaskState.APPLYING, now + 2,
                    affectedIds = current.cloneIds, fingerprint = "synthetic-fingerprint")
                check(store.replace(current, next))
                current = next
            }
            TaskState.VERIFYING -> {
                val applying = current.move(TaskState.APPLYING, now + 2,
                    affectedIds = current.cloneIds, fingerprint = "synthetic-fingerprint")
                check(store.replace(current, applying))
                val next = applying.move(TaskState.VERIFYING, now + 3)
                check(store.replace(applying, next))
                current = next
            }
            TaskState.REVIEW_REQUIRED -> {
                val applying = current.move(TaskState.APPLYING, now + 2,
                    affectedIds = current.cloneIds, fingerprint = "synthetic-fingerprint")
                check(store.replace(current, applying))
                val verifying = applying.move(TaskState.VERIFYING, now + 3)
                check(store.replace(applying, verifying))
                val next = verifying.move(TaskState.REVIEW_REQUIRED, now + 4,
                    issue = TaskIssue.INTERRUPTED)
                check(store.replace(verifying, next))
                current = next
            }
            TaskState.FAILED -> {
                val next = current.move(TaskState.FAILED, now + 2,
                    issue = TaskIssue.SOURCE_PREPARATION_FAILED)
                check(store.replace(current, next))
                current = next
            }
            TaskState.CANCELLED -> error("Handled before the PREPARING transition")
            TaskState.SUCCEEDED -> {
                val applying = current.move(TaskState.APPLYING, now + 2,
                    affectedIds = current.cloneIds, fingerprint = "synthetic-fingerprint")
                check(store.replace(current, applying))
                val verifying = applying.move(TaskState.VERIFYING, now + 3)
                check(store.replace(applying, verifying))
                val next = verifying.move(TaskState.SUCCEEDED, now + 4,
                    completion = CompletionKind.APPLIED_AND_VERIFIED)
                check(store.replace(verifying, next))
                current = next
            }
            TaskState.DEFERRED -> {
                val next = current.move(TaskState.DEFERRED, now + 2,
                    issue = TaskIssue.PAUSED)
                check(store.replace(current, next))
                current = next
            }
        }
        return current
    }
}
