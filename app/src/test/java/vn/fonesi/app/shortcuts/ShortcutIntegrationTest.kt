package vn.fonesi.app.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.app.MainActivity
import vn.fonesi.app.storage.*
import vn.fonesi.core.*
import vn.fonesi.core.update.*
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real SQLite/OS locks and Android shortcut shadows. No guest application is executed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ShortcutIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private lateinit var engine: ShortcutEngineDouble
    private val io = Executors.newSingleThreadExecutor()
    private fun <T> background(task: () -> T): T = io.submit(Callable(task)).get(30, TimeUnit.SECONDS)
    private val source = InstalledSource("test.social", 3, setOf("signer"), listOf("/base.apk"))
    private fun lock() = FileQueueLock(context.filesDir.toPath().resolve("shortcut-$name.lock"))
    private fun repo(key: String = "engine-a", provider: CloneEngine = engine) = CloneMetadataRepository(db, key, provider, lock())
    private fun controller(key: String = "engine-a", provider: CloneEngine = engine) = CloneLaunchController(db, key, provider, lock())
    private fun assertBlocked(reason: LaunchBlock, result: LaunchResult) = assertEquals(LaunchResult.Blocked(reason), result)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "shortcut-${UUID.randomUUID()}.db"
        db = FonesiDatabase.open(context, name)
        engine = ShortcutEngineDouble()
    }
    @After fun teardown() {
        background { db.close() }
        context.deleteDatabase(name)
        io.shutdownNow()
    }

    @Test fun canonicalRouteContainsOnlyOpaqueTokenAndIgnoresIdentityOverrides() = background {
        val row = repo().create(source, "Facebook A")
        val intent = CloneRoute.intent(context, row.launchToken)
            .putExtra("cloneId", "another-account").putExtra("packageName", "other.app")
        assertEquals(CloneLaunchActivity::class.java.name, intent.component?.className)
        assertEquals(row.launchToken, CloneRoute.parse(intent))
        assertFalse(intent.dataString!!.contains(row.cloneId))
        assertFalse(intent.dataString!!.contains(row.engineKey))
        assertEquals(row.launchToken, CloneRoute.tokenFromShortcutId(CloneRoute.shortcutId(row.launchToken)))
        assertEquals(LaunchResult.Accepted(row), controller().launch(CloneRoute.parse(intent)!!))
        assertEquals(listOf(row.cloneId), engine.launched)
    }

    @Test fun malformedOrModifiedRoutesNeverReachTheEngine() = background {
        val token = "a1234567-89ab-4cde-8fab-1234567890ab"
        for (value in listOf("", "https://clone/$token", "fonesi://other/$token", "fonesi://clone:12/$token",
            "fonesi://clone/$token?cloneId=b", "fonesi://clone/$token#b", "fonesi://clone/x/$token",
            "fonesi://clone/%${token.first().code.toString(16)}${token.drop(1)}", "fonesi://clone/${token.uppercase()}",
            "fonesi://clone/${token.take(8)}")) {
            assertNull(value, CloneRoute.parse(Intent(Intent.ACTION_VIEW, Uri.parse(value))))
        }
        assertNull(CloneRoute.parse(null))
        assertNull(CloneRoute.parse(Intent(Intent.ACTION_MAIN, Uri.parse("fonesi://clone/$token"))))
        assertBlocked(LaunchBlock.INVALID_LINK, controller().launch("../$token"))
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun twoInstancesOfSameAppDispatchOnlyTheSelectedInstance() = background {
        val a = repo().create(source, "A")
        val b = repo().create(source, "B")
        assertNotEquals(a.launchToken, b.launchToken)
        assertEquals(LaunchResult.Accepted(b), controller().inspect(b.launchToken))
        assertTrue(engine.launched.isEmpty())
        assertEquals(LaunchResult.Accepted(b), controller().launch(b.launchToken))
        assertEquals(listOf(b.cloneId), engine.launched)
        assertFalse(engine.records.single { it.id == a.cloneId }.running!!)
    }

    @Test fun renameAndDatabaseReopenKeepTheSameShortcutIdentity() = background {
        val a = repo().create(source, "A")
        repo().rename(a.cloneId, "Cửa hàng 🛒", "Bán hàng")
        db.close()
        db = FonesiDatabase.open(context, name)
        val renamed = db.clones().findByLaunchToken(a.launchToken)!!
        assertEquals(a.cloneId, renamed.cloneId)
        assertEquals("Cửa hàng 🛒", renamed.label)
        assertEquals(LaunchResult.Accepted(renamed), controller().launch(a.launchToken))
    }

    @Test fun deletingThenRecreatingSameEngineIdCannotReviveAnOldShortcut() = background {
        val old = repo().create(source, "Old account")
        repo().delete(old.cloneId)
        engine.sequence = 0 // A provider reusing its ID after a confirmed delete.
        val replacement = repo().create(source, "New account")
        assertEquals(old.cloneId, replacement.cloneId)
        assertNotEquals(old.launchToken, replacement.launchToken)
        assertBlocked(LaunchBlock.CLONE_MISSING, controller().launch(old.launchToken))
        assertTrue(engine.launched.isEmpty())
        assertEquals(LaunchResult.Accepted(replacement), controller().launch(replacement.launchToken))
    }

    @Test fun engineNamespacesAndAvailabilityAreVerifiedBeforeDispatch() = background {
        val a = repo().create(source, "A")
        assertBlocked(LaunchBlock.ENGINE_CHANGED, controller("engine-b").launch(a.launchToken))
        assertBlocked(LaunchBlock.ENGINE_UNAVAILABLE, controller(provider = UnavailableCloneEngine()).launch(a.launchToken))
        engine.records.clear()
        assertBlocked(LaunchBlock.CLONE_MISSING, controller().launch(a.launchToken))
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun changedPackageOrAmbiguousEngineIdsAreNeverDispatched() = background {
        val a = repo().create(source, "A")
        val record = engine.records.single()
        engine.records[0] = record.copy(packageName = "changed.application")
        assertBlocked(LaunchBlock.IDENTITY_CHANGED, controller().launch(a.launchToken))
        engine.records[0] = record
        engine.records += record
        assertBlocked(LaunchBlock.IDENTITY_CHANGED, controller().launch(a.launchToken))
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun interruptedUpdateBlocksLaunchCreateAndDeleteUntilReconciled() = background {
        val a = repo().create(source, "A")
        val store = RoomUpdateTaskStore(db)
        val queued = UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(source.copy(versionCode = 4), listOf(a.cloneId))), UpdateScope.PER_CLONE).single()
        val preparing = queued.move(TaskState.PREPARING, 1)
        assertTrue(store.replace(queued, preparing))
        assertBlocked(LaunchBlock.UPDATE_UNRESOLVED, controller().launch(a.launchToken))
        val applying = preparing.move(TaskState.APPLYING, 2, affectedIds = listOf(a.cloneId), fingerprint = "f".repeat(64))
        assertTrue(store.replace(preparing, applying))
        assertBlocked(LaunchBlock.UPDATE_UNRESOLVED, controller().launch(a.launchToken))
        val verifying = applying.move(TaskState.VERIFYING, 3)
        assertTrue(store.replace(applying, verifying))
        assertBlocked(LaunchBlock.UPDATE_UNRESOLVED, controller().launch(a.launchToken))
        val review = verifying.move(TaskState.REVIEW_REQUIRED, 4, issue = TaskIssue.INTERRUPTED)
        assertTrue(store.replace(verifying, review))
        assertBlocked(LaunchBlock.UPDATE_UNRESOLVED, controller().launch(a.launchToken))
        assertThrows(IllegalStateException::class.java) { repo().delete(a.cloneId) }
        assertThrows(IllegalStateException::class.java) { repo().create(source, "B") }
        assertNotNull(db.clones().findByLaunchToken(a.launchToken))
        assertEquals(1, engine.sequence)
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun queuedWorkAndUnrelatedPackagesDoNotBlockOpening() = background {
        val a = repo().create(source, "A")
        val store = RoomUpdateTaskStore(db)
        val queue = UpdateQueue(store)
        queue.enqueue("engine-a", listOf(UpdateTarget(source.copy(versionCode = 4), listOf(a.cloneId))), UpdateScope.PER_CLONE)
        val other = queue.enqueue("engine-a", listOf(UpdateTarget(source.copy(packageName = "other.app"), listOf("other-id"))), UpdateScope.PER_CLONE).single()
        val preparing = other.move(TaskState.PREPARING, 1)
        assertTrue(store.replace(other, preparing))
        assertEquals(LaunchResult.Accepted(a), controller().launch(a.launchToken))
    }

    @Test fun launchAndLifecycleUseSameOsLockAsUpdateRunner() = background {
        val a = repo().create(source, "A")
        lock().acquire()!!.use {
            assertBlocked(LaunchBlock.OPERATION_BUSY, controller().launch(a.launchToken))
            assertThrows(IllegalStateException::class.java) { repo().create(source, "B") }
            assertThrows(IllegalStateException::class.java) { repo().delete(a.cloneId) }
        }
        assertTrue(engine.launched.isEmpty())
        engine.onLaunch = { assertNull(lock().acquire()) }
        assertEquals(LaunchResult.Accepted(a), controller().launch(a.launchToken))
        lock().acquire()!!.close()
    }

    @Test fun unreadableJournalFailsClosedAndPreservesRows() = background {
        val a = repo().create(source, "A")
        val job = UpdateTask.create("engine-a", UpdateTarget(source, listOf(a.cloneId)), UpdateScope.PER_CLONE, 1)
        db.tasks().insert(TaskCodec.row(job).copy(payload = "not valid JSON"))
        assertBlocked(LaunchBlock.STORAGE_ERROR, controller().launch(a.launchToken))
        assertEquals(1, db.tasks().list().size)
        assertNotNull(db.clones().findByLaunchToken(a.launchToken))
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun engineDispatchExceptionIsNotConvertedIntoSuccessOrRetried() = background {
        val a = repo().create(source, "A")
        engine.onLaunch = { throw IllegalStateException("private SDK message") }
        assertBlocked(LaunchBlock.ENGINE_ERROR, controller().launch(a.launchToken))
        assertEquals(1, engine.attempts)
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun lockIsCheckedBeforeLookupAndAgainImmediatelyBeforeNativeDispatch() = background {
        val row = repo().create(source, "A")
        var allowed = false
        val guarded = CloneLaunchController(db, "engine-a", engine, lock()) { allowed }
        assertBlocked(LaunchBlock.LOCKED, guarded.launch(row.launchToken))
        allowed = true
        engine.onList = { allowed = false }
        assertBlocked(LaunchBlock.LOCKED, guarded.launch(row.launchToken))
        assertEquals(0, engine.attempts)
    }

    @Test fun pinUsesOpaqueIdExplicitDestinationAndUnicodeLabelsWithoutDynamicQuota() = background {
        val row = repo().create(source, "🛒".repeat(25) + "a".repeat(30))
        val service = context.getSystemService(ShortcutManager::class.java)
        shadowOf(service).setIsRequestPinShortcutSupported(false)
        val shortcuts = AndroidCloneShortcuts(context, db)
        assertEquals(PinResult.UNSUPPORTED, shortcuts.requestPin(row))
        shadowOf(service).setIsRequestPinShortcutSupported(true)
        assertEquals(PinResult.REQUEST_SENT, shortcuts.requestPin(row))
        val info = service.pinnedShortcuts.single()
        assertEquals(CloneRoute.shortcutId(row.launchToken), info.id)
        assertEquals(MainActivity::class.java.name, info.activity?.className)
        assertEquals(CloneLaunchActivity::class.java.name, info.intent?.component?.className)
        assertEquals(row.launchToken, CloneRoute.parse(info.intent))
        assertEquals("🛒".repeat(25) + "a".repeat(15), info.shortLabel.toString())
        assertEquals(row.label, info.longLabel.toString())
        assertTrue(service.dynamicShortcuts.isEmpty())
        assertTrue(engine.launched.isEmpty())
    }

    @Test fun pinnedNamesFollowMetadataAndDeletedInstancesAreDisabledWithoutTouchingOthers() = background {
        val a = repo().create(source, "A")
        val b = repo().create(source, "B")
        val service = context.getSystemService(ShortcutManager::class.java)
        shadowOf(service).setIsRequestPinShortcutSupported(true)
        val shortcuts = AndroidCloneShortcuts(context, db)
        shortcuts.requestPin(a)
        shortcuts.requestPin(b)
        repo().rename(b.cloneId, "Tên 🛒 mới", "Group")
        shortcuts.synchronizePinned()
        assertEquals("Tên 🛒 mới", service.pinnedShortcuts.single { it.id == CloneRoute.shortcutId(b.launchToken) }.shortLabel.toString())
        repo().delete(a.cloneId)
        shortcuts.synchronizePinned()
        // Robolectric 4.15.1 tracks disabled IDs but leaves ShortcutInfo.isEnabled unchanged.
        // Verify the observable disabled-ID rule through requestPinShortcut instead of that flag.
        val rejected = assertThrows(IllegalArgumentException::class.java) { shortcuts.requestPin(a) }
        assertTrue(rejected.message!!.contains("disabled"))
        assertEquals(PinResult.REQUEST_SENT, shortcuts.requestPin(db.clones().findByLaunchToken(b.launchToken)!!))
        assertBlocked(LaunchBlock.CLONE_MISSING, controller().launch(a.launchToken))
    }

    @Test fun schemaOneMigratesTokensWithoutLosingMetadataQueueOrPause(): Unit = background {
        db.close()
        val schema = javaClass.classLoader!!.getResourceAsStream("vn.fonesi.app.storage.FonesiDatabase/1.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val job = UpdateTask.create("engine-a", UpdateTarget(source, listOf("legacy")), UpdateScope.PER_CLONE, 99)
        val taskRow = TaskCodec.row(job)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) raw.execSQL(setup.getString(index))
            for (key in listOf("engine-a", "engine-b")) {
                raw.execSQL("INSERT INTO clone_metadata VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(key, "legacy", source.packageName, "Cửa hàng 🛒", "Nhóm", 10L, 20L))
            }
            raw.execSQL("INSERT INTO update_tasks (taskId,packageName,state,revision,activeKey,payload) VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>(taskRow.taskId, taskRow.packageName, taskRow.state, taskRow.revision, taskRow.activeKey, taskRow.payload))
            raw.execSQL("INSERT INTO queue_control VALUES (1,1)")
            raw.version = 1
        }
        db = FonesiDatabase.open(context, name)
        val rows = db.clones().list()
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.launchToken }.distinct().size)
        assertTrue(rows.all { CloneRoute.validToken(it.launchToken) && it.label == "Cửa hàng 🛒" && it.changedAt == 20L })
        assertEquals(job, RoomUpdateTaskStore(db).list().single())
        assertTrue(RoomUpdateTaskStore(db).isPaused())
        val tokens = rows.associate { it.engineKey to it.launchToken }
        db.close()
        db = FonesiDatabase.open(context, name)
        assertEquals(tokens, db.clones().list().associate { it.engineKey to it.launchToken })
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            db.clones().insert(rows.first().copy(cloneId = "new-id"))
        }
    }
}

/** Test-only provider, never bundled into an APK or used as a production engine. */
private class ShortcutEngineDouble : CloneEngine {
    override val availability = EngineAvailability.Ready(UpdateScope.PER_CLONE)
    val records = mutableListOf<CloneRecord>()
    val launched = mutableListOf<String>()
    var sequence = 0
    var attempts = 0
    var onLaunch: () -> Unit = {}
    var onList: () -> Unit = {}
    override fun listClones(): List<CloneRecord> { onList(); return records.toList() }
    override fun create(source: InstalledSource, label: String): CloneRecord = CloneRecord(
        "instance-${++sequence}", source.packageName, source.versionCode, source.signerDigests, false,
    ).also { records += it }
    override fun launch(cloneId: String) {
        attempts++
        onLaunch()
        launched += cloneId
        val index = records.indexOfFirst { it.id == cloneId }
        records[index] = records[index].copy(running = true)
    }
    override fun delete(cloneId: String) { records.removeAll { it.id == cloneId } }
    override fun update(target: UpdateTarget): List<CloneRecord> = error("Not an update SDK")
}
