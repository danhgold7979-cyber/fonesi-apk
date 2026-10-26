package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.core.*
import vn.fonesi.core.update.FileQueueLock
import java.util.UUID
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class InstanceRegistryTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private val executor = Executors.newSingleThreadExecutor()
    private fun <T> background(action: () -> T): T = executor.submit(Callable(action)).get(30, TimeUnit.SECONDS)
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); name = "instances-${UUID.randomUUID()}.db"; db = FonesiDatabase.open(context, name) }
    @After fun close() { background { db.close() }; context.deleteDatabase(name); executor.shutdownNow() }

    @Test fun reservationAndPendingLaunchSurviveReopenAndDeletedUsersAreNeverReused(): Unit = background {
        val registry = RoomInstanceRegistry(db, "engine-a")
        val a = registry.reserve("test.social")
        registry.state(a.id, InstanceState.READY); registry.pending(a.id, true)
        db.close(); db = FonesiDatabase.open(context, name)
        val restored = RoomInstanceRegistry(db, "engine-a")
        assertTrue(restored.list().single().launchPending)
        assertEquals(InstanceState.READY, restored.list().single().state)
        restored.state(a.id, InstanceState.DELETED)
        val b = restored.reserve("test.social")
        assertTrue(b.userId > a.userId); assertNotEquals(a.id, b.id)
    }
    @Test fun namespacesCannotMutateEachOthersRows(): Unit = background {
        val a = RoomInstanceRegistry(db, "engine-a"); val b = RoomInstanceRegistry(db, "engine-b")
        val first = a.reserve("test.social"); val second = b.reserve("test.social")
        assertTrue(second.userId > first.userId)
        assertThrows(IllegalStateException::class.java) { b.pending(first.id, true) }
        assertFalse(a.list().single().launchPending)
        assertEquals(listOf(second.id), b.list().map { it.id })
    }
    @Test fun concurrentReservationsReceiveDifferentDurableUserIds() {
        background { db.instances().list("engine-a") }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll((1..8).map { Callable { RoomInstanceRegistry(db, "engine-a").reserve("test.social") } }).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(8, results.map { it.userId }.distinct().size)
            assertEquals(8, results.map { it.id }.distinct().size)
            assertEquals(8, background { RoomInstanceRegistry(db, "engine-a").list().size })
        } finally { pool.shutdownNow() }
    }
    @Test fun readbackRecoveryImportsOnlyActualGuestsAndStaleDeleteTokenIsRejected(): Unit = background {
        val provider = RecoveryEngineDouble()
        val repo = CloneMetadataRepository(db, "engine-a", provider, FileQueueLock(context.filesDir.toPath().resolve("$name.lock")))
        assertEquals(1, repo.importDiscovered())
        assertEquals(0, repo.importDiscovered())
        val row = db.clones().list().single()
        assertEquals("actual-guest", row.cloneId)
        assertThrows(IllegalStateException::class.java) { repo.delete(row.cloneId, UUID.randomUUID().toString()) }
        assertEquals(0, provider.deletes)
        assertNotNull(db.clones().findByLaunchToken(row.launchToken))
    }
}

private class RecoveryEngineDouble : CloneEngine {
    var deletes = 0
    override val availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
    override fun listClones() = listOf(CloneRecord("actual-guest", "test.social", 2, setOf("signer"), false))
    override fun create(source: InstalledSource, label: String): CloneRecord = error("Not used")
    override fun launch(cloneId: String) = error("Not used")
    override fun delete(cloneId: String) { deletes++ }
    override fun update(target: UpdateTarget): List<CloneRecord> = error("Not used")
}
