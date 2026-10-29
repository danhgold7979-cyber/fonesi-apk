package vn.fonesi.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.core.CloneRecord
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CloneStopIdentityGuardTest {
    private fun row() = CloneMetadataRow(
        engineKey = "engine-current",
        cloneId = "clone-1",
        packageName = "test.alpha",
        label = "Test-only identity",
        groupName = null,
        createdAt = 1L,
        changedAt = 1L,
        launchToken = UUID.randomUUID().toString(),
    )
    private fun live() = listOf(CloneRecord(
        "clone-1", "test.alpha", 1L, setOf("test-only-signer"), false,
    ))
    private fun refused(action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Stop guard should fail closed, got $failure",
            failure is IllegalStateException || failure is IllegalArgumentException)
    }
    private fun <T> onIo(executor: ExecutorService, action: () -> T): T =
        executor.submit(Callable { action() }).get(30, TimeUnit.SECONDS)

    @Test fun exactCurrentCloneMetadataAndLiveIdentityAreAccepted() {
        val saved = row()
        CloneStopIdentityGuard.requireSameGuest(saved, saved, saved.engineKey, saved.launchToken, live())
    }

    @Test fun staleTokenOrWrongEngineCannotStopClone() {
        val saved = row()
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved, saved.engineKey, "stale-token", live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved, "another-engine", saved.launchToken, live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved, "", saved.launchToken, live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved, saved.engineKey, "", live()) }
    }

    @Test fun changedPackageOrClusterGenerationCannotStopOriginalIdentity() {
        val saved = row()
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved.copy(packageName = "test.beta"),
            saved.engineKey, saved.launchToken, live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved.copy(clusterId = UUID.randomUUID().toString()),
            saved.engineKey, saved.launchToken, live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved.copy(changedAt = 2L),
            saved.engineKey, saved.launchToken, live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, null,
            saved.engineKey, saved.launchToken, live()) }
    }

    @Test fun duplicateMissingOrWrongPackageEngineRecordCannotStopClone() {
        val saved = row()
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved,
            saved.engineKey, saved.launchToken, emptyList()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved,
            saved.engineKey, saved.launchToken, live() + live()) }
        refused { CloneStopIdentityGuard.requireSameGuest(saved, saved,
            saved.engineKey, saved.launchToken, live().map { it.copy(packageName = "test.other") }) }
    }

    @Test fun nativeRoomReadbackRejectsChangedMetadataWithoutDeletingIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executors.newSingleThreadExecutor()
        val name = "e56-stop-race-${UUID.randomUUID()}.db"
        val db = onIo(executor) { FonesiDatabase.open(context, name) }
        val captured = row()
        try {
            onIo(executor) {
                db.clones().insert(captured)
                val current = db.clones().findByLaunchToken(captured.launchToken)
                assertEquals(captured, current)
                CloneStopIdentityGuard.requireSameGuest(captured, current,
                    captured.engineKey, captured.launchToken, live())

                assertEquals(1, db.clones().rename(captured.engineKey, captured.cloneId,
                    "Renamed during stop", null, 2L))
                val changed = db.clones().findByLaunchToken(captured.launchToken)
                refused { CloneStopIdentityGuard.requireSameGuest(captured, changed,
                    captured.engineKey, captured.launchToken, live()) }
                assertEquals("Renamed during stop", db.clones().find(captured.engineKey, captured.cloneId)?.label)

                assertEquals(1, db.clones().associateCluster(captured.engineKey, captured.cloneId,
                    captured.launchToken, UUID.randomUUID().toString(), 3L))
                val switched = db.clones().findByLaunchToken(captured.launchToken)
                refused { CloneStopIdentityGuard.requireSameGuest(captured, switched,
                    captured.engineKey, captured.launchToken, live()) }
                assertTrue(db.clones().find(captured.engineKey, captured.cloneId)?.clusterId != null)

                db.openHelper.writableDatabase.execSQL(
                    "UPDATE clone_metadata SET packageName = ? WHERE engineKey = ? AND cloneId = ?",
                    arrayOf("test.beta", captured.engineKey, captured.cloneId),
                )
                val changedPackage = db.clones().findByLaunchToken(captured.launchToken)
                refused { CloneStopIdentityGuard.requireSameGuest(captured, changedPackage,
                    captured.engineKey, captured.launchToken, live()) }
                assertEquals("test.beta", db.clones().find(captured.engineKey, captured.cloneId)?.packageName)
            }
        } finally {
            runCatching { onIo(executor) { db.close() } }
            context.deleteDatabase(name)
            executor.shutdownNow()
        }
    }
}
