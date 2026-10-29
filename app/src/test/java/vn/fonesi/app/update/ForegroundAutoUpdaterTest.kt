package vn.fonesi.app.update

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.app.storage.*
import vn.fonesi.core.*
import vn.fonesi.core.update.*
import java.io.File
import java.util.UUID
import java.util.concurrent.*

/** Full host pipeline with SQLite/files/locks, but synthetic bytes and a test-only guest provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ForegroundAutoUpdaterTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private lateinit var root: File
    private lateinit var policy: AutoUpdatePolicyStore
    private lateinit var tasks: RoomUpdateTaskStore
    private lateinit var engine: AutomaticEngineDouble
    private lateinit var rows: List<CloneMetadataRow>
    private var foreground = true
    private val sources = mutableMapOf<String, InstalledSource>()
    private val io = Executors.newSingleThreadExecutor()
    private fun <T> background(action: () -> T): T = io.submit(Callable(action)).get(30, TimeUnit.SECONDS)
    private fun operationLock() = FileQueueLock(root.toPath().resolve("queue.lock"))
    private fun autoLock() = FileQueueLock(root.toPath().resolve("auto.lock"))
    private fun catalog() = SourceCatalog { sources[it] }
    private fun runner(): UpdateExecution = UpdateRunner("engine", engine, tasks, catalog(),
        FileSourceSnapshotter(root.toPath().resolve("snapshots"), catalog()), operationLock(),
        mayApply = { foreground && policy.read().mode == AutoUpdateMode.APPLY_WHILE_OPEN },
        allowLegacySource = RoomLegacyUpdateSourcePolicy(db)::allows)
    private fun automatic() = ForegroundAutoUpdater(db, "engine", policy,
        UpdateRequestService(db, "engine", engine, catalog(), tasks, operationLock()) { foreground }, tasks,
        autoLock(), operationLock(), { foreground }, ::runner)
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); name = "automatic-${UUID.randomUUID()}.db"
        root = File(context.cacheDir, name).also { it.mkdirs() }
        val directory = File(root, "policies").also { it.mkdirs() }
        val wrapped = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
        policy = AutoUpdatePolicyStore(wrapped)
        db = FonesiDatabase.open(context, name); tasks = RoomUpdateTaskStore(db)
        rows = listOf("a" to "test.alpha", "b" to "test.beta").mapIndexed { i, (id, pkg) -> CloneMetadataRow("engine", id, pkg, id, null, i.toLong(), 0) }
        background { rows.forEach(db.clones()::insert) }
        engine = AutomaticEngineDouble(rows)
        for (row in rows) {
            val apk = File(root, "${row.packageName}/base.apk").also { it.parentFile!!.mkdirs(); it.writeText("synthetic bytes, not an actual APK") }
            sources[row.packageName] = InstalledSource(row.packageName, 2, setOf("signer"), listOf(apk.path))
        }
    }
    @After fun close() { background { db.close() }; context.deleteDatabase(name); root.deleteRecursively(); io.shutdownNow() }
    @Test fun defaultOffAndBackgroundNeverEnqueueOrInvokeGuest(): Unit = background {
        val auto = automatic(); auto.check()
        assertEquals(AutoUpdateMode.OFF, policy.read().mode); assertTrue(tasks.list().isEmpty())
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN); foreground = false; auto.check()
        assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.updates)
    }
    @Test fun corruptPolicyFallsBackToOffWithoutDeletingItsBytes(): Unit = background {
        val file = File(root, "policies/auto-update-policy.json"); file.writeText("broken")
        automatic().check()
        assertEquals(AutoUpdatePolicy(AutoUpdateMode.OFF, true), policy.read())
        assertEquals("broken", file.readText()); assertTrue(tasks.list().isEmpty())
    }
    @Test fun checkOnlyReportsPreviewWithoutEnqueueOrApply(): Unit = background {
        policy.write(AutoUpdateMode.CHECK_ON_OPEN); val auto = automatic(); auto.check()
        assertEquals(2, auto.state.value.preview!!.plan.targets.size)
        assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.updates)
    }
    @Test fun automaticApplyUsesTheDurableQueueAndDeduplicatesOnNextCheck(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN); val auto = automatic(); auto.check()
        assertEquals(2, engine.updates); assertTrue(tasks.list().all { it.state == TaskState.SUCCEEDED })
        auto.check(); assertEquals(2, engine.updates); assertEquals(2, tasks.list().size)
    }
    @Test fun pausedQueueIsNeverResumedByAutomaticMode(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN); tasks.setPaused(true); automatic().check()
        assertTrue(tasks.isPaused()); assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.updates)
    }
    @Test fun switchingOffInsideFirstApplyFinishesVerificationButStopsNextTask(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        engine.beforeUpdate = { policy.write(AutoUpdateMode.OFF) }
        automatic().check()
        assertEquals(1, engine.updates)
        assertEquals(1, tasks.list().count { it.state == TaskState.SUCCEEDED })
        assertEquals(1, tasks.list().count { it.state == TaskState.QUEUED })
    }
    @Test fun deferredBusyJobOnlyRetriesWhenFreshPlanIsNoLongerBusy(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        val source = sources.getValue("test.alpha")
        val job = UpdateQueue(tasks).enqueue("engine", listOf(UpdateTarget(source, listOf("a"))), UpdateScope.SHARED_PACKAGE).single()
        val preparing = job.move(TaskState.PREPARING, 1); tasks.replace(job, preparing)
        tasks.replace(preparing, preparing.move(TaskState.DEFERRED, 2, TaskIssue.CLONE_BUSY))
        engine.records[0] = engine.records[0].copy(running = true)
        automatic().check()
        assertEquals(TaskState.DEFERRED, tasks.list().single { it.id == job.id }.state)
        engine.records[0] = engine.records[0].copy(running = false)
        automatic().check()
        assertEquals(TaskState.SUCCEEDED, tasks.list().single { it.id == job.id }.state)
    }
    @Test fun uncertainApplyIsNeverAutomaticallyReplayed(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        val source = sources.getValue("test.alpha")
        val job = UpdateQueue(tasks).enqueue("engine", listOf(UpdateTarget(source, listOf("a"))), UpdateScope.SHARED_PACKAGE).single()
        val preparing = job.move(TaskState.PREPARING, 1); tasks.replace(job, preparing)
        tasks.replace(preparing, preparing.move(TaskState.APPLYING, 2, affectedIds = listOf("a"), fingerprint = "f".repeat(64)))
        automatic().check()
        assertEquals(TaskState.REVIEW_REQUIRED, tasks.list().single { it.id == job.id }.state)
        assertEquals(1, engine.updates) // Only unrelated beta is allowed; alpha is held for review.
    }
    @Test fun oldQueuedSourceIsSupersededWithoutTouchingAnInflightTask(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        val old = UpdateQueue(tasks).enqueue("engine", listOf(UpdateTarget(sources.getValue("test.alpha"), listOf("a"))), UpdateScope.SHARED_PACKAGE).single()
        sources["test.alpha"] = sources.getValue("test.alpha").copy(versionCode = 3)
        automatic().check()
        assertEquals(TaskState.CANCELLED, tasks.list().single { it.id == old.id }.state)
        assertEquals(TaskIssue.SUPERSEDED_SOURCE, tasks.list().single { it.id == old.id }.issue)
        assertEquals(3L, engine.records.first { it.id == "a" }.versionCode)
    }
    @Test fun retainedPrivateQueueIsNotCancelledByOriginalAppVersionChange(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        val oldPrivate = sources.getValue("test.alpha").copy(versionCode = 1L)
        // Only durable marker is required to reproduce foreground authority confusion.
        // No synthetic bytes are represented as a signed private-store publication.
        val retained = UpdateQueue(tasks).enqueue("engine",
            listOf(UpdateTarget(oldPrivate, listOf("a"))), UpdateScope.SHARED_PACKAGE,
            retainedSource = true).single()
        assertTrue(retained.retainedSource)

        automatic().check()

        assertEquals(retained, tasks.list().first { it.id == retained.id })
        assertEquals(TaskState.QUEUED, tasks.list().first { it.id == retained.id }.state)
        assertEquals(1, engine.updates) // Unrelated original-source work may proceed; retained alpha must not run.
    }

    @Test fun preMarkerPrivateOwnerTypesAreNotCancelledByOriginalAppPreview(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        val protected = rows.mapIndexed { i, row ->
            val oldSource = sources.getValue(row.packageName).copy(versionCode = 1L)
            val task = UpdateQueue(tasks).enqueue("engine",
                listOf(UpdateTarget(oldSource, listOf(row.cloneId))), UpdateScope.SHARED_PACKAGE).single()
            assertFalse(task.retainedSource) // Legacy E41 marker absent.
            val ownerType = if (i == 0) "UPDATE_TASK" else "REVIEW_REQUIRED"
            assertTrue(db.clusterReferences().insert(ClusterReferenceRow(
                UUID.randomUUID().toString(), ownerType, task.id, "engine", 10L + i)) > 0)
            task
        }

        automatic().check()

        assertTrue(protected.all { old -> tasks.list().first { it.id == old.id } == old })
        assertEquals(2, db.clusterReferences().list().size)
        assertEquals(0, engine.updates)
    }

    @Test fun anotherCoordinatorLeasePreventsASecondRun(): Unit = background {
        policy.write(AutoUpdateMode.APPLY_WHILE_OPEN)
        autoLock().acquire()!!.use { automatic().check() }
        assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.updates)
    }
}

private class AutomaticEngineDouble(rows: List<CloneMetadataRow>) : CloneEngine {
    override val availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
    val records = rows.map { CloneRecord(it.cloneId, it.packageName, 1, setOf("signer"), false) }.toMutableList()
    var updates = 0
    var beforeUpdate: () -> Unit = {}
    override fun listClones() = records.toList()
    override fun update(target: UpdateTarget): List<CloneRecord> {
        updates++; beforeUpdate()
        records.indices.filter { records[it].packageName == target.source.packageName }.forEach { records[it] = records[it].copy(versionCode = target.source.versionCode) }
        return records.toList()
    }
    override fun create(source: InstalledSource, label: String): CloneRecord = error("Not an automatic update")
    override fun launch(cloneId: String) = error("Automatic mode must not launch guest UI")
    override fun delete(cloneId: String) = error("Automatic mode must not delete account data")
}
