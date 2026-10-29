package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.core.*
import vn.fonesi.core.update.*
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

/** Native SQLite under Robolectric; does not run a virtualization SDK or social application. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class StorageIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private val io = Executors.newSingleThreadExecutor()
    private fun <T> background(task: () -> T): T = io.submit(Callable(task)).get(30, TimeUnit.SECONDS)
    private val source = InstalledSource("test.social", 2, setOf("signer"), listOf("/base.apk"))
    private fun operationLock() = FileQueueLock(context.filesDir.toPath().resolve("test-mutations.lock"))

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "test-${UUID.randomUUID()}.db"
        db = FonesiDatabase.open(context, name)
    }

    @After fun teardown() {
        background { db.close() }
        context.deleteDatabase(name)
        io.shutdownNow()
    }

    private fun reopen() {
        db.close()
        db = FonesiDatabase.open(context, name)
    }

    @Test fun queueAndPauseSurviveDatabaseCloseWithoutLosingUnicodeOrLongVersion() = background {
        val store = RoomUpdateTaskStore(db)
        val unusual = source.copy(versionCode = Long.MAX_VALUE, signerDigests = setOf("ký-tự", "quote\"|signer"))
        val job = UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(unusual, listOf("a|b", "a", "Tài khoản 🛒"))), UpdateScope.SHARED_PACKAGE).single()
        store.setPaused(true)
        reopen()
        val restored = RoomUpdateTaskStore(db)
        assertEquals(job, restored.list().single())
        assertTrue(restored.isPaused())
        assertEquals(2, db.openHelper.writableDatabase.query("PRAGMA synchronous").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun compareAndSetAndPauseAreCheckedInSameTransaction() = background {
        val store = RoomUpdateTaskStore(db)
        val job = UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(source, listOf("a"))), UpdateScope.PER_CLONE).single()
        val preparing = job.move(TaskState.PREPARING, 10)
        assertTrue(store.replace(job, preparing))
        assertFalse(store.replace(job, job.move(TaskState.CANCELLED, 11)))
        val applying = preparing.move(TaskState.APPLYING, 12, affectedIds = listOf("a"), fingerprint = "f".repeat(64))
        store.setPaused(true)
        assertFalse(store.replace(preparing, applying, requireUnpaused = true))
        assertEquals(TaskState.PREPARING, store.list().single().state)
        store.setPaused(false)
        assertTrue(store.replace(preparing, applying, requireUnpaused = true))
        assertThrows(IllegalArgumentException::class.java) {
            store.replace(applying, applying.copy(revision = applying.revision + 1, state = TaskState.VERIFYING, packageName = "another.account"))
        }
        assertEquals("test.social", store.list().single().packageName)
    }

    @Test fun concurrentDuplicateEnqueueCreatesOnlyOneDurableTask() {
        background { db.tasks().list() } // Open/configure once before concurrent writers.
        val pool = Executors.newFixedThreadPool(4)
        try {
            val store = RoomUpdateTaskStore(db)
            val results = pool.invokeAll((0 until 8).map {
                Callable { UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(source, listOf("a"))), UpdateScope.PER_CLONE).single().id }
            }).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.distinct().size)
            assertEquals(1, background { store.list().size })
        } finally { pool.shutdownNow() }
    }

    @Test fun queueOrderUsesInsertionInsteadOfWallClockOrRandomUuid() = background {
        val store = RoomUpdateTaskStore(db)
        var time = 100L
        val queue = UpdateQueue(store) { --time }
        for (version in listOf(2L, 3L, 4L)) {
            queue.enqueue("engine-a", listOf(UpdateTarget(source.copy(versionCode = version), listOf("a"))), UpdateScope.PER_CLONE)
        }
        reopen()
        assertEquals(listOf(2L, 3L, 4L), RoomUpdateTaskStore(db).list().map { it.versionCode })
    }

    @Test fun interruptedApplyIsReadBackAfterReopenWithoutCallingUpdateAgain() = background {
        val store = RoomUpdateTaskStore(db)
        val queued = UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(source, listOf("a"))), UpdateScope.PER_CLONE).single()
        val preparing = queued.move(TaskState.PREPARING, 1)
        store.replace(queued, preparing)
        store.replace(preparing, preparing.move(TaskState.APPLYING, 2, affectedIds = listOf("a"), fingerprint = "f".repeat(64)))
        reopen()
        val engine = EngineDouble().apply { records += CloneRecord("a", source.packageName, 2, source.signerDigests, false) }
        val restored = RoomUpdateTaskStore(db)
        val root = Files.createTempDirectory("room-queue-")
        val catalog = SourceCatalog { source }
        val runner = UpdateRunner("engine-a", engine, restored, catalog,
            FileSourceSnapshotter(root.resolve("spool"), catalog), FileQueueLock(root.resolve("queue.lock")))
        assertTrue(runner.recover())
        assertEquals(CompletionKind.RECOVERED_VERSION_VERIFIED, restored.list().single().completion)
        assertEquals(RunResult.Idle, runner.runNext())
        assertEquals(0, engine.updates)
    }

    @Test fun realDatabaseAndFileSnapshotCompleteOneQueuedImport() = background {
        val root = Files.createTempDirectory("room-queue-")
        val apk = root.resolve("base.apk").also { Files.write(it, "synthetic bytes, not a real APK".toByteArray()) }
        val available = source.copy(apkPaths = listOf(apk.toString()))
        val engine = EngineDouble().apply { records += CloneRecord("a", source.packageName, 1, source.signerDigests, false) }
        val store = RoomUpdateTaskStore(db)
        UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(available, listOf("a"))), UpdateScope.PER_CLONE)
        val catalog = SourceCatalog { available }
        val runner = UpdateRunner("engine-a", engine, store, catalog,
            FileSourceSnapshotter(root.resolve("spool"), catalog), FileQueueLock(root.resolve("queue.lock")))
        assertEquals(TaskState.SUCCEEDED, (runner.runNext() as RunResult.Processed).task.state)
        reopen()
        assertEquals(CompletionKind.APPLIED_AND_VERIFIED, RoomUpdateTaskStore(db).list().single().completion)
        assertEquals(1, engine.updates)
    }

    @Test fun metadataIsSavedOnlyAfterReadbackAndDeletesOnlyAfterConfirmedAbsence() = background {
        val unavailable = CloneMetadataRepository(db, "engine-a", UnavailableCloneEngine(), operationLock())
        assertThrows(IllegalArgumentException::class.java) { unavailable.create(source, "A") }
        assertTrue(db.clones().list().isEmpty())
        val engine = EngineDouble()
        val repo = CloneMetadataRepository(db, "engine-a", engine, operationLock())
        engine.publishCreates = false
        assertThrows(IllegalStateException::class.java) { repo.create(source, "A") }
        assertTrue(db.clones().list().isEmpty())
        engine.publishCreates = true
        val a = repo.create(source, "  Tài khoản 🛒  ", " Cửa hàng ")
        val b = repo.create(source, "B")
        assertEquals("Tài khoản 🛒", a.label)
        engine.leaveOnDelete = true
        assertThrows(IllegalStateException::class.java) { repo.delete(a.cloneId) }
        assertNotNull(db.clones().find("engine-a", a.cloneId))
        engine.leaveOnDelete = false
        repo.delete(a.cloneId)
        assertNull(db.clones().find("engine-a", a.cloneId))
        assertNotNull(db.clones().find("engine-a", b.cloneId))
        assertTrue(engine.records.any { it.id == b.cloneId })
    }

    @Test fun inconsistentClusterOwnershipBlocksProviderDeletionBeforeGuestIsTouched() = background {
        val engine = EngineDouble()
        val repository = CloneMetadataRepository(db, "engine-a", engine, operationLock())
        val saved = repository.create(source, "Protected clone")
        val conflictingCluster = UUID.randomUUID().toString()
        db.clusterReferences().insert(ClusterReferenceRow(
            clusterId = conflictingCluster,
            ownerType = vn.fonesi.apk.ClusterOwnerType.CLONE.name,
            ownerId = saved.cloneId,
            engineKey = "engine-a",
            createdAtEpochMs = 5L,
        ))

        assertThrows(IllegalStateException::class.java) {
            repository.delete(saved.cloneId, saved.launchToken)
        }

        // The mismatch must be detected before engine.delete(), not merely after it.
        assertEquals(saved, db.clones().find("engine-a", saved.cloneId))
        assertEquals(listOf(saved.cloneId), engine.records.map { it.id })
        assertEquals(listOf(conflictingCluster), db.clusterReferences()
            .forOwner(vn.fonesi.apk.ClusterOwnerType.CLONE.name, saved.cloneId, "engine-a")
            .map { it.clusterId })
    }

    @Test fun providerFailurePreservesAssociatedMetadataAndOtherOwners() = background {
        val engine = EngineDouble()
        val repository = CloneMetadataRepository(db, "engine-a", engine, operationLock())
        val saved = repository.create(source, "Existing clone")
        val clusterId = UUID.randomUUID().toString()
        check(db.clones().associateCluster("engine-a", saved.cloneId, saved.launchToken, clusterId, 10L) == 1)
        db.clusterReferences().insert(ClusterReferenceRow(
            clusterId, vn.fonesi.apk.ClusterOwnerType.CLONE.name, saved.cloneId, "engine-a", 10L,
        ))
        db.clusterReferences().insert(ClusterReferenceRow(
            clusterId, vn.fonesi.apk.ClusterOwnerType.REVIEW_REQUIRED.name, "review-1", "engine-a", 11L,
        ))

        engine.leaveOnDelete = true
        assertThrows(IllegalStateException::class.java) {
            repository.delete(saved.cloneId, saved.launchToken)
        }
        assertEquals(clusterId, db.clones().find("engine-a", saved.cloneId)?.clusterId)
        assertTrue(engine.records.any { it.id == saved.cloneId })
        assertEquals(setOf(vn.fonesi.apk.ClusterOwnerType.CLONE.name, vn.fonesi.apk.ClusterOwnerType.REVIEW_REQUIRED.name),
            db.clusterReferences().forCluster(clusterId).map { it.ownerType }.toSet())
    }

    @Test fun namespacesAndOfflineRenamePreserveOtherEnginesMetadata() = background {
        val first = CloneMetadataRepository(db, "engine-a", EngineDouble(), operationLock()).create(source, "A")
        CloneMetadataRepository(db, "engine-b", EngineDouble(), operationLock()).create(source, "B")
        reopen()
        val offline = CloneMetadataRepository(db, "engine-a", UnavailableCloneEngine(), operationLock())
        offline.rename(first.cloneId, "Nhãn mới", "Nhóm 🛒")
        assertEquals("Nhãn mới", db.clones().find("engine-a", first.cloneId)?.label)
        assertEquals("B", db.clones().find("engine-b", first.cloneId)?.label)
        assertThrows(IllegalArgumentException::class.java) { offline.rename(first.cloneId, "   ", null) }
        assertEquals("Nhãn mới", db.clones().find("engine-a", first.cloneId)?.label)
    }

    @Test fun validatedCloneStyleSurvivesReopenWithoutChangingSdkOrOtherEngine() = background {
        val first = CloneMetadataRepository(db, "engine-a", EngineDouble(), operationLock()).create(source, "A")
        val other = CloneMetadataRepository(db, "engine-b", EngineDouble(), operationLock()).create(source, "B")
        val token = first.launchToken
        val offline = CloneMetadataRepository(db, "engine-a", UnavailableCloneEngine(), operationLock())
        offline.restyle(first.cloneId, "purple", "🛒")
        assertEquals("purple", db.clones().find("engine-a", first.cloneId)?.accentKey)
        assertEquals("🛒", db.clones().find("engine-a", first.cloneId)?.badgeText)
        assertEquals("teal", db.clones().find("engine-b", other.cloneId)?.accentKey)
        assertThrows(IllegalArgumentException::class.java) { offline.restyle(first.cloneId, "unknown", "A") }
        assertThrows(IllegalArgumentException::class.java) { offline.restyle(first.cloneId, "blue", "A\nB") }
        assertThrows(IllegalStateException::class.java) { offline.restyle("deleted-id", "blue", null) }
        reopen()
        val saved = checkNotNull(db.clones().find("engine-a", first.cloneId))
        assertEquals(token, saved.launchToken)
        assertEquals(first.cloneId, saved.cloneId)
        assertEquals(first.packageName, saved.packageName)
        assertEquals(first.createdAt, saved.createdAt)
        assertEquals("purple", saved.accentKey)
        assertEquals("🛒", saved.badgeText)
    }

    @Test fun v3DatabaseMigratesToV6KeepingCloneTokenQueueAndEngineReservation() = background {
        db.close()
        context.deleteDatabase(name) // Fresh test DB may not have materialized a file yet.
        val resource = checkNotNull(javaClass.classLoader!!.getResourceAsStream(
            "vn.fonesi.app.storage.FonesiDatabase/3.json"))
        val schema = JSONObject(resource.bufferedReader().use { it.readText() }).getJSONObject("database")
        val token = UUID.randomUUID().toString()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                val placeholder = "$" + "{TABLE_NAME}"
                raw.execSQL(entity.getString("createSql").replace(placeholder, table))
                val indexes = entity.optJSONArray("indices")
                if (indexes != null) for (j in 0 until indexes.length())
                    raw.execSQL(indexes.getJSONObject(j).getString("createSql").replace(placeholder, table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.execSQL("INSERT INTO clone_metadata(engineKey, cloneId, packageName, label, groupName, createdAt, changedAt, launchToken) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf("engine-a", "persist-id", "test.social", "Giữ nguyên", "Nhóm", 11, 12, token))
            raw.execSQL("INSERT INTO queue_control(id, paused) VALUES(1, 1)")
            raw.execSQL("INSERT INTO engine_instances(sequence, cloneId, engineKey, packageName, state, launchPending) VALUES(?,?,?,?,?,?)",
                arrayOf(1, "reserved-id", "engine-a", "test.social", "READY", 0))
            raw.version = 3
        }
        db = FonesiDatabase.open(context, name)
        val row = db.clones().list().single()
        assertEquals("persist-id", row.cloneId)
        assertEquals(token, row.launchToken)
        assertEquals("Giữ nguyên", row.label)
        assertEquals("Nhóm", row.groupName)
        assertEquals("teal", row.accentKey)
        assertNull(row.badgeText)
        assertTrue(RoomUpdateTaskStore(db).isPaused())
        assertEquals("reserved-id", db.instances().list("engine-a").single().cloneId)
        assertEquals(6, db.openHelper.writableDatabase.version)
    }

    @Test fun unknownSchemaDoesNotDestructivelyRecreateTheDatabase() = background {
        db.close()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            raw.execSQL("CREATE TABLE retained(value TEXT NOT NULL)")
            raw.execSQL("INSERT INTO retained VALUES ('must survive')")
            raw.version = 7
        }
        db = FonesiDatabase.open(context, name)
        assertThrows(IllegalStateException::class.java) { db.tasks().list() }
        db.close()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            raw.rawQuery("SELECT value FROM retained", null).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("must survive", cursor.getString(0))
            }
        }
    }

    @Test fun retainedSourceMarkerIsStrictAndLegacySourcePolicyChecksOlderOwnerReferences() = background {
        val job = UpdateTask.create("engine-a", UpdateTarget(source, listOf("clone-1")),
            UpdateScope.PER_CLONE, 100L)
        assertEquals(job, RoomUpdateTaskStore(db).insertOrGetActive(job))
        val policy = RoomLegacyUpdateSourcePolicy(db)
        assertTrue(policy.allows(job))
        assertFalse(TaskCodec.decode(TaskCodec.row(job)).retainedSource)

        val oldOwner = UUID.randomUUID().toString()
        assertTrue(db.clusterReferences().insert(ClusterReferenceRow(
            oldOwner, "UPDATE_TASK", job.id, "engine-a", 101L)) > 0)
        assertFalse("Pre-E44 tasks with an existing owner cannot use the original catalog", policy.allows(job))
        assertEquals(1, db.clusterReferences().delete(oldOwner, "UPDATE_TASK", job.id, "engine-a"))

        val marked = job.copy(retainedSource = true)
        val encoded = TaskCodec.row(marked)
        assertTrue(db.tasks().replace(job.id, job.revision, job.state.name, encoded.state,
            encoded.revision, encoded.activeKey, encoded.payload) == 1)
        reopen()
        assertEquals(marked, RoomUpdateTaskStore(db).list().single())
        assertFalse(RoomLegacyUpdateSourcePolicy(db).allows(marked))
        val corrupt = JSONObject(encoded.payload).put("retainedSource", "yes").toString()
        assertThrows(IllegalArgumentException::class.java) {
            TaskCodec.decode(encoded.copy(payload = corrupt))
        }
        assertThrows(IllegalArgumentException::class.java) {
            marked.move(TaskState.CANCELLED, 102L).copy(retainedSource = false)
                .requireSuccessorOf(marked)
        }
    }

    @Test fun unknownTaskEncodingFailsWithoutDeletingStoredRows() = background {
        val task = UpdateTask.create("engine-a", UpdateTarget(source, listOf("a")), UpdateScope.PER_CLONE, 1)
        val row = TaskCodec.row(task)
        db.tasks().insert(row.copy(payload = row.payload.replace("\"format\":1", "\"format\":2")))
        assertThrows(IllegalArgumentException::class.java) { RoomUpdateTaskStore(db).list() }
        assertEquals(1, db.tasks().list().size)
        reopen()
        assertEquals(1, db.tasks().list().size)
    }

    @Test fun corruptDatabaseIsNotDeletedOrReplacedWithAnEmptyOne() = background {
        db.close()
        val file = context.getDatabasePath(name)
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        val original = ByteArray(4096) { 66 }
        file.writeBytes(original)
        db = FonesiDatabase.open(context, name)
        assertThrows(android.database.sqlite.SQLiteException::class.java) { db.tasks().list() }
        db.close()
        assertTrue(file.exists())
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun observersPublishQueueAndPauseChangesWithoutPolling() = background {
        runBlocking {
            val store = RoomUpdateTaskStore(db)
            val states = Channel<Boolean>(Channel.UNLIMITED)
            val jobs = Channel<List<UpdateTask>>(Channel.UNLIMITED)
            val stateObserver = launch { store.observePaused().distinctUntilChanged().collect { states.send(it) } }
            val taskObserver = launch { store.observe().distinctUntilChanged().collect { jobs.send(it) } }
            try {
                assertFalse(withTimeout(5000) { states.receive() })
                assertTrue(withTimeout(5000) { jobs.receive() }.isEmpty())
                val queued = UpdateQueue(store).enqueue("engine-a", listOf(UpdateTarget(source, listOf("a"))), UpdateScope.PER_CLONE).single()
                assertEquals(queued.id, withTimeout(5000) { jobs.receive() }.single().id)
                store.setPaused(true)
                assertTrue(withTimeout(5000) { states.receive() })
            } finally {
                stateObserver.cancelAndJoin(); taskObserver.cancelAndJoin()
                states.close(); jobs.close()
            }
        }
    }
}

/** Test-only engine protocol implementation, never included in an APK. */
private class EngineDouble : CloneEngine {
    override val availability = EngineAvailability.Ready(UpdateScope.PER_CLONE)
    val records = mutableListOf<CloneRecord>()
    var publishCreates = true
    var leaveOnDelete = false
    var updates = 0
    private var sequence = 0
    override fun listClones() = records.toList()
    override fun create(source: InstalledSource, label: String): CloneRecord {
        val record = CloneRecord("id-${++sequence}", source.packageName, source.versionCode, source.signerDigests, false)
        if (publishCreates) records += record
        return record
    }
    override fun launch(cloneId: String) = Unit
    override fun delete(cloneId: String) { if (!leaveOnDelete) records.removeAll { it.id == cloneId } }
    override fun update(target: UpdateTarget): List<CloneRecord> {
        updates++
        for (i in records.indices) if (records[i].id in target.affectedCloneIds) {
            records[i] = records[i].copy(versionCode = target.source.versionCode, signerDigests = target.source.signerDigests)
        }
        return listClones()
    }
}
