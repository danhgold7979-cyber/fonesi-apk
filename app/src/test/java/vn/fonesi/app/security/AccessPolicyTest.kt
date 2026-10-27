package vn.fonesi.app.security

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
class AccessPolicyTest {
    private lateinit var context: Application
    private lateinit var store: AccessPolicyStore
    private lateinit var broker: AccessBroker
    private val models = ViewModelStore()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.noBackupFilesDir.mkdirs()
        clean()
        broker = AccessBroker(context)
        store = broker.policy
        broker.changePolicy(false)
    }
    private fun clean() { for (suffix in listOf("", ".bak", ".new")) File(context.noBackupFilesDir, "access-policy.json$suffix").deleteRecursively() }
    @After fun teardown() { models.clear(); broker.changePolicy(false); clean() }
    private fun model(id: String = "gate") = AccessGateModel(context, broker).also { models.put(id, it); it.foreground(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun policyPersistsWithoutSavingAnAuthenticatedSession() {
        assertEquals(AccessPolicy(false), store.read())
        store.write(true); assertEquals(AccessPolicy(true), AccessPolicyStore(context).read())
        store.write(false); assertEquals(AccessPolicy(false), AccessPolicyStore(context).read())
        assertEquals(setOf("version", "required"), org.json.JSONObject(File(context.noBackupFilesDir, "access-policy.json").readText()).keys().asSequence().toSet())
    }
    @Test fun corruptUnknownOrOversizedPolicyRequiresAuthenticationAndKeepsBytes() {
        for (content in listOf("broken", "{\"version\":2,\"required\":false}", "{\"version\":1,\"required\":false,\"extra\":0}", "x".repeat(2048))) {
            val file = File(context.noBackupFilesDir, "access-policy.json"); file.writeText(content)
            assertEquals(AccessPolicy(true, true), store.read())
            assertEquals(content, file.readText())
        }
    }
    @Test fun missingBackupRestoreAndUnavailableStorageDoNotTurnIntoAnUnlockedPolicy() {
        File(context.noBackupFilesDir, "access-policy.json.bak").writeText("{\"version\":1,\"required\":true}")
        assertTrue(store.read().required)
        val unavailable = File(context.cacheDir, "not-directory").also { it.writeText("keep") }
        val wrapped = object : ContextWrapper(context) { override fun getNoBackupFilesDir(): File = unavailable }
        val broken = AccessPolicyStore(wrapped)
        assertEquals(AccessPolicy(true, true), broken.read())
        assertThrows(Exception::class.java) { broken.write(false) }
        assertEquals("keep", unavailable.readText())
    }
    @Test fun enablingAndDisablingRequireTheCurrentAuthenticationResult() {
        val gate = model()
        val enable = gate.begin(AuthPurpose.ENABLE)!!
        gate.complete(enable, false); assertFalse(store.read().required)
        val accepted = gate.begin(AuthPurpose.ENABLE)!!
        gate.complete(accepted.copy(id = accepted.id + 1), true); assertFalse(store.read().required)
        gate.complete(accepted, true); assertTrue(store.read().required)
        gate.background(false); gate.foreground(); assertFalse(gate.state.value.unlocked)
        val disable = gate.begin(AuthPurpose.DISABLE)!!
        gate.complete(disable, false); assertTrue(store.read().required)
        gate.complete(gate.begin(AuthPurpose.DISABLE)!!, true); assertFalse(store.read().required)
    }
    @Test fun brokerRevokesForegroundGrantOnBackgroundAndRejectsStaleSuccess() {
        broker.changePolicy(true)
        val gate = model()
        assertFalse(broker.hasAccess())
        gate.complete(gate.begin(AuthPurpose.UNLOCK)!!, true)
        assertTrue(broker.hasAccess())
        gate.background(false); assertFalse(broker.hasAccess())
        gate.foreground(); val attempt = gate.begin(AuthPurpose.UNLOCK)!!
        gate.background(false); gate.foreground(); gate.complete(attempt, true)
        assertFalse(gate.state.value.unlocked); assertFalse(broker.hasAccess())
    }
    @Test fun enablingFromAnotherWindowInvalidatesItsPreviouslyUnlockedUi() {
        val first = model("first"); val second = model("second")
        assertTrue(second.state.value.unlocked)
        first.complete(first.begin(AuthPurpose.ENABLE)!!, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(first.state.value.unlocked); assertFalse(second.state.value.unlocked)
        first.background(false)
        assertFalse(broker.hasAccess())
    }
    @Test fun disabledUiLockDoesNotGiveBackgroundExecutionAccess() {
        val gate = model()
        assertTrue(broker.hasForegroundAccess())
        gate.background(false)
        assertTrue(broker.hasAccess()) // Lock policy OFF; foreground execution is a separate gate.
        assertFalse(broker.hasForegroundAccess())
    }
}
