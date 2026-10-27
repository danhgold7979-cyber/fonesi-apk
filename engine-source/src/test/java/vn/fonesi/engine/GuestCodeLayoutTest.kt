package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest

/** Real signed/private APK layout policy; these tests do not execute ART or ResourcesManager. */
class GuestCodeLayoutTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
    private val store by lazy { PrivateApkStore(temporary.root.toPath().resolve("code")) }
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun publish(vararg names: String): String {
        val signers = ApkVerifier.Builder(apk("main-disguised")).setMinCheckedPlatformVersion(26).build().verify()
            .signerCertificates.map { MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { b -> "%02x".format(b.toInt() and 255) } }.toSet()
        return store.publish(InstalledSource("vn.fonesi.fixture", 1, signers, names.map { apk(it).path }), 34).id
    }
    private fun layout(id: String, root: File = temporary.root, host: String = "vn.fonesi.host") =
        GuestCodeLayout.read(store, id, 34, root, host)

    @Test fun layoutKeepsEveryFeatureAndConfigInMatchingAndroidSplitNameOrder() {
        val id = publish("chat-config", "dependent-feature", "base", "main-disguised", "base-config")
        val plan = layout(id)
        assertEquals(listOf("config.en", "feature.chat", "feature.chat.en", "feature.pay"), plan.splitNames)
        assertEquals(5, plan.allApks.size)
        assertEquals("base.apk", plan.base.name)
        val verified = store.open(id, 34).apks.splits.associateBy { it.splitName }
        plan.splitNames.indices.forEach { assertEquals(verified.getValue(plan.splitNames[it]).file, plan.splits[it]) }
        assertTrue(plan.allApks.all { it.path.startsWith(temporary.root.path + File.separator) })
    }
    @Test fun singleApkDoesNotInventSplitOrOriginalPaths() {
        val plan = layout(publish("main-disguised"))
        assertTrue(plan.splits.isEmpty()); assertTrue(plan.splitNames.isEmpty()); assertEquals(listOf(plan.base), plan.allApks)
        assertNotEquals(apk("main-disguised"), plan.base)
    }
    @Test fun isolatedSplitFlagCannotSilentlyUseFlatLoader() {
        val id = publish("isolated-base", "base-config")
        assertTrue(store.open(id, 34).apks.base.isolatedSplits)
        assertThrows(IllegalArgumentException::class.java) { layout(id) }
    }
    @Test fun hostPackageCannotBeLoadedAsGuest() {
        val id = publish("main-disguised")
        assertThrows(IllegalArgumentException::class.java) { layout(id, host = "vn.fonesi.fixture") }
    }
    @Test fun authenticatedCodeOutsideHostPrivateRootCannotLoad() {
        val id = publish("main-disguised", "base-config")
        val otherRoot = temporary.newFolder("other")
        assertThrows(IllegalArgumentException::class.java) { layout(id, root = otherRoot) }
    }
    @Test fun corruptionBetweenPublicationAndLayoutHasNoOriginalAppFallback() {
        val id = publish("main-disguised", "base-config")
        assertTrue(store.open(id, 34).apks.splits.single().file.delete())
        assertThrows(Exception::class.java) { layout(id) }
        assertTrue(apk("base-config").isFile)
    }
    @Test fun failedLayoutDoesNotDeleteCodeOrChangeAnotherCluster() {
        val first = publish("main-disguised"); val second = publish("isolated-base", "base-config")
        assertThrows(Exception::class.java) { layout(second) }
        assertEquals(first, layout(first).cluster.id)
        assertTrue(store.open(second, 34).apks.base.isolatedSplits)
    }
}
