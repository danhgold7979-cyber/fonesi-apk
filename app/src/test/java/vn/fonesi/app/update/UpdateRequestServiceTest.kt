package vn.fonesi.app.update

import android.content.Context
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
import java.util.UUID
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class UpdateRequestServiceTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private lateinit var engine: PreviewEngineDouble
    private lateinit var rows: List<CloneMetadataRow>
    private lateinit var tasks: RoomUpdateTaskStore
    private val io = Executors.newSingleThreadExecutor()
    private var allowed = true
    private val sources = mutableMapOf<String, InstalledSource>()
    private var onRead: () -> Unit = {}
    private fun <T> background(action: () -> T): T = io.submit(Callable(action)).get(30, TimeUnit.SECONDS)
    private fun lock() = FileQueueLock(context.filesDir.toPath().resolve("$name.lock"))
    private fun service(store: UpdateTaskStore = tasks) = UpdateRequestService(db, "engine", engine,
        SourceCatalog { onRead(); sources[it] }, store, lock()) { allowed }
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); name = "preview-${UUID.randomUUID()}.db"
        db = FonesiDatabase.open(context, name); tasks = RoomUpdateTaskStore(db)
        rows = listOf("a" to "test.alpha", "b" to "test.alpha", "c" to "test.beta").mapIndexed { i, (id, pkg) ->
            CloneMetadataRow("engine", id, pkg, "Name $id", null, i.toLong(), 0)
        }
        engine = PreviewEngineDouble(rows)
        for (pkg in listOf("test.alpha", "test.beta")) sources[pkg] = InstalledSource(pkg, 2, setOf("signer"), listOf("/$pkg/base.apk"))
        background { rows.forEach(db.clones()::insert) }
    }
    @After fun close() { background { db.close() }; context.deleteDatabase(name); io.shutdownNow() }

    @Test fun previewShowsUnselectedSharedPeersWithoutEnqueueOrNativeMutation(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        assertEquals(setOf("a", "b"), preview.plan.targets.single().affectedCloneIds.toSet())
        assertEquals(listOf("a"), preview.requests.map { it.cloneId })
        assertEquals("Name b", preview.labels["b"])
        assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.mutations)
    }
    @Test fun confirmedPreviewEnqueuesOnceAndRepeatedConsentDeduplicates(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        val first = service().confirm(preview).single()
        assertEquals(first.id, service().confirm(preview).single().id)
        assertEquals(1, tasks.list().size); assertEquals(0, engine.mutations)
        assertEquals(2L, first.versionCode)
    }
    @Test fun changedSourceVersionOrSameVersionPathsRequireNewConsent(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        val original = sources.getValue("test.alpha")
        for (changed in listOf(original.copy(versionCode = 3), original.copy(apkPaths = listOf("/changed/base.apk")))) {
            sources[original.packageName] = changed
            assertThrows(UpdatePreviewChangedException::class.java) { service().confirm(preview) }
        }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun newSharedPeerCannotBeEnqueuedUnderAnOldImpactPreview(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        engine.records += CloneRecord("new", "test.alpha", 1, setOf("signer"), false)
        assertThrows(UpdatePreviewChangedException::class.java) { service().confirm(preview) }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun peerOpeningOrUnknownStateInvalidatesPriorPreview(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        for (running in listOf(true, null)) {
            engine.records[1] = engine.records[1].copy(running = running)
            assertThrows(UpdatePreviewChangedException::class.java) { service().confirm(preview) }
        }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun missingUnselectedMetadataPeerHoldsItsWholeSharedGroup(): Unit = background {
        engine.records.removeAll { it.id == "b" }
        val preview = service().preview(listOf(rows[0].launchToken, rows[2].launchToken))
        assertEquals("test.beta", preview.plan.targets.single().source.packageName)
        assertEquals(UpdateBlock("a", UpdateBlockReason.INCOMPLETE_GROUP), preview.plan.blocked.single())
        assertEquals("test.beta", service().confirm(preview).single().packageName)
        assertEquals(0, engine.mutations)
    }
    @Test fun scopeAndSplitCapabilityChangesRequireFreshConsent(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        engine.availability = EngineAvailability.Ready(UpdateScope.PER_CLONE)
        assertThrows(UpdatePreviewChangedException::class.java) { service().confirm(preview) }
        engine.availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE, importsSplitApks = true)
        assertThrows(UpdatePreviewChangedException::class.java) { service().confirm(preview) }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun staleTokenCannotApplyToRecreatedMetadata(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        db.clones().delete("engine", "a")
        db.clones().insert(rows[0].copy(launchToken = UUID.randomUUID().toString(), label = "Replacement"))
        assertThrows(IllegalStateException::class.java) { service().confirm(preview) }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun authorizationIsRecheckedAfterSourceRead(): Unit = background {
        allowed = false
        assertThrows(IllegalStateException::class.java) { service().preview(listOf(rows[0].launchToken)) }
        allowed = true
        val preview = service().preview(listOf(rows[0].launchToken))
        onRead = { allowed = false }
        assertThrows(IllegalStateException::class.java) { service().confirm(preview) }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun wrongEngineNamespaceCannotSupplyAnUpdateTarget(): Unit = background {
        val other = rows[0].copy(engineKey = "other", cloneId = "other-id", launchToken = UUID.randomUUID().toString())
        db.clones().insert(other)
        assertThrows(IllegalStateException::class.java) { service().preview(listOf(other.launchToken)) }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun busyOperationLockPreventsPreviewOrEnqueue(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        lock().acquire()!!.use {
            assertThrows(IllegalStateException::class.java) { service().preview(listOf(rows[0].launchToken)) }
            assertThrows(IllegalStateException::class.java) { service().confirm(preview) }
        }
        assertTrue(tasks.list().isEmpty())
    }
    @Test fun splitGroupIsReportedBlockedWhileSingleApkGroupCanBeConsented(): Unit = background {
        sources["test.beta"] = sources.getValue("test.beta").copy(apkPaths = listOf("base.apk", "split.apk"))
        val preview = service().preview(listOf(rows[0].launchToken, rows[2].launchToken))
        assertEquals(UpdateBlock("c", UpdateBlockReason.SOURCE_UNSUPPORTED), preview.plan.blocked.single())
        assertEquals("test.alpha", service().confirm(preview).single().packageName)
        assertEquals(0, engine.mutations)
    }
    @Test fun enqueueFailureInSecondGroupRollsBackFirstGroupToo(): Unit = background {
        var inserts = 0
        val failing = object : UpdateTaskStore by tasks {
            override fun insertOrGetActive(task: UpdateTask): UpdateTask {
                check(++inserts != 2) { "Synthetic storage failure" }
                return tasks.insertOrGetActive(task)
            }
        }
        val preview = service(failing).preview(listOf(rows[0].launchToken, rows[2].launchToken))
        assertThrows(IllegalStateException::class.java) { service(failing).confirm(preview) }
        assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.mutations)
    }
    @Test fun perCloneComparisonRetainsEveryTargetWithTheSameSource(): Unit = background {
        engine.availability = EngineAvailability.Ready(UpdateScope.PER_CLONE)
        val preview = service().preview(listOf(rows[0].launchToken, rows[1].launchToken))
        val changed = preview.copy(plan = preview.plan.copy(targets = listOf(
            preview.plan.targets[0].copy(affectedCloneIds = listOf("different")), preview.plan.targets[1],
        )))
        assertFalse(preview.equivalent(changed))
        assertEquals(2, preview.plan.targets.size)
    }
    @Test fun engineBecomingUnavailableCannotTurnConsentIntoACommand(): Unit = background {
        val preview = service().preview(listOf(rows[0].launchToken))
        engine.availability = EngineAvailability.Unavailable("test")
        assertThrows(IllegalStateException::class.java) { service().confirm(preview) }
        assertTrue(tasks.list().isEmpty()); assertEquals(0, engine.mutations)
    }
}

private class PreviewEngineDouble(rows: List<CloneMetadataRow>) : CloneEngine {
    override var availability: EngineAvailability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
    val records = rows.map { CloneRecord(it.cloneId, it.packageName, 1, setOf("signer"), false) }.toMutableList()
    var mutations = 0
    override fun listClones() = records.toList()
    override fun create(source: InstalledSource, label: String): CloneRecord { mutations++; error("Not a preview operation") }
    override fun launch(cloneId: String) { mutations++ }
    override fun delete(cloneId: String) { mutations++ }
    override fun update(target: UpdateTarget): List<CloneRecord> { mutations++; error("Not a preview operation") }
}
