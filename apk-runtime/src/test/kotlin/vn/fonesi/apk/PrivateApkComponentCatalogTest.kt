package vn.fonesi.apk

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.InstalledSource
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Actual aapt2 binary AndroidManifest and cryptographically signed APKs, NOT guest execution. */
class PrivateApkComponentCatalogTest {
    @get:Rule val temp = TemporaryFolder()
    private val fixtureRoot = File(checkNotNull(System.getProperty("fonesi.apkFixtures")))
    private val store by lazy { PrivateApkStore(temp.root.toPath().resolve("private")) }
    private val pkg = "vn.fonesi.fixture"
    private fun apk(name: String): File = File(fixtureRoot, name + ".apk")
    private fun signers(): Set<String> = ApkVerifier.Builder(apk("catalog-base"))
        .setMinCheckedPlatformVersion(26).build().verify().signerCertificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.toSet()
    private fun publish(vararg members: String): PrivateApkCluster = store.publish(
        InstalledSource(pkg, 1, signers(), members.map { apk(it).path }), 34
    )
    private fun inspect(vararg members: String): VerifiedPrivateComponentInventory =
        PrivateApkComponentCatalog.inspect(store, publish(*members).id, 34)

    @Test fun signedPrivateBaseEnumeratesAllComponentTypesWithoutRunningThem() {
        val result = inspect("catalog-base")
        assertEquals(pkg, result.packageName)
        assertEquals(1L, result.versionCode)
        assertEquals(signers(), result.signerDigests)
        assertEquals("vn.fonesi.fixture.GuestApplication", result.applicationClass)
        assertEquals(64, result.clusterDigest.length)
        assertTrue(result.splitNames.isEmpty())
        assertEquals(6, result.components.size)
        assertEquals(setOf(PrivateComponentKind.ACTIVITY, PrivateComponentKind.ACTIVITY_ALIAS,
            PrivateComponentKind.SERVICE, PrivateComponentKind.RECEIVER, PrivateComponentKind.PROVIDER),
            result.components.map { it.kind }.toSet())
        val activity = result.components.single { it.className.endsWith(".EntryActivity") }
        assertEquals(PrivateComponentKind.ACTIVITY, activity.kind)
        assertEquals(true, activity.exported)
        assertNull(activity.sourceSplit)
        assertEquals(":guest", activity.processName)
        val unknown = result.components.single { it.className.endsWith(".ImplicitActivity") }
        assertNull("Never infer Android default exported", unknown.exported)
        assertNull(unknown.enabled)
        val alias = result.components.single { it.kind == PrivateComponentKind.ACTIVITY_ALIAS }
        assertEquals("vn.fonesi.fixture.EntryActivity", alias.aliasTarget)
        assertEquals(false, alias.exported)
        val worker = result.components.single { it.kind == PrivateComponentKind.SERVICE }
        assertEquals("vn.fonesi.fixture.ACCESS", worker.permission)
        assertEquals(true, worker.enabled)
        val provider = result.components.single { it.kind == PrivateComponentKind.PROVIDER }
        assertEquals(listOf("vn.fonesi.fixture.accounts", "vn.fonesi.fixture.files"), provider.authorities)
    }

    @Test fun signedBaseFeatureConfigInventoryKeepsExactOriginAndClusterIdentity() {
        val cluster = publish("catalog-config", "catalog-feature", "catalog-base")
        val result = PrivateApkComponentCatalog.inspect(store, cluster.id, 34)
        assertEquals(cluster.clusterDigest, result.clusterDigest)
        assertEquals(listOf("config.de", "feature.catalog"), result.splitNames)
        assertEquals(7, result.components.size)
        assertEquals("feature.catalog",
            result.components.single { it.className == "vn.fonesi.fixture.FeatureActivity" }.sourceSplit)
        assertTrue(result.components.none { it.sourceSplit == "config.de" })
        assertEquals(7, result.components.map { it.className }.toSet().size)
        val noFeature = inspect("catalog-base")
        assertNotEquals(noFeature.clusterDigest, result.clusterDigest)
        assertEquals(noFeature.packageName, result.packageName)
        assertEquals(noFeature.signerDigests, result.signerDigests)
    }

    @Test fun signedDuplicateComponentAndConfigInjectedComponentFailClosed() {
        val duplicate = publish("catalog-base", "catalog-duplicate")
        assertThrows(IllegalArgumentException::class.java) {
            PrivateApkComponentCatalog.inspect(store, duplicate.id, 34)
        }
        val config = publish("catalog-base", "catalog-feature", "catalog-illegal-config")
        assertThrows(IllegalArgumentException::class.java) {
            PrivateApkComponentCatalog.inspect(store, config.id, 34)
        }
    }

    @Test fun signedBaseApplicationProcessPreservedWithoutInferringGuestRuntime() {
        val signed = inspect("catalog-app-process")
        assertEquals(":remote", signed.applicationProcessName)
        assertNull(signed.applicationEnabled)
        assertNull(signed.applicationPermission)
        assertEquals(pkg, signed.packageName)
        assertEquals(1L, signed.versionCode)
        assertEquals(signers(), signed.signerDigests)
        assertNull(inspect("catalog-public").applicationProcessName)
    }

    @Test fun signedFeatureProcessOverrideCannotChangeBaseApplicationPolicy() {
        val cluster = publish("catalog-public", "catalog-split-app-process")
        assertThrows(IllegalArgumentException::class.java) {
            PrivateApkComponentCatalog.inspect(store, cluster.id, 34)
        }
        assertEquals(":remote", inspect("catalog-app-process").applicationProcessName)
    }

    @Test fun signedAliasWithoutActualActivityAndCollidingAuthoritiesAreRejected() {
        val alias = publish("catalog-bad-alias")
        assertThrows(IllegalArgumentException::class.java) {
            PrivateApkComponentCatalog.inspect(store, alias.id, 34)
        }
        val providers = publish("catalog-base", "catalog-duplicate-authority")
        assertThrows(IllegalArgumentException::class.java) {
            PrivateApkComponentCatalog.inspect(store, providers.id, 34)
        }
    }

    @Test fun changedPrivateBytesAreRejectedWithoutOriginalApkFallback() {
        val cluster = publish("catalog-base", "catalog-feature")
        val privateBase = cluster.apks.base.file
        assertTrue(privateBase.setWritable(true))
        Files.write(privateBase.toPath(), byteArrayOf(1, 2, 3, 4))
        assertThrows(Exception::class.java) {
            PrivateApkComponentCatalog.inspect(store, cluster.id, 34)
        }
        assertTrue(apk("catalog-base").isFile)
        assertNotEquals(privateBase.canonicalFile, apk("catalog-base").canonicalFile)
    }

    @Test fun incompleteSplitAndWrongSignerNeverBecomeCatalogEntries() {
        val incomplete = InstalledSource(pkg, 1, signers(), listOf(apk("catalog-base").path,
            apk("dependent-feature").path))
        assertThrows(Exception::class.java) { store.publish(incomplete, 34) }
        val mixed = InstalledSource(pkg, 1, signers(), listOf(apk("catalog-base").path,
            apk("wrong-signer").path))
        assertThrows(Exception::class.java) { store.publish(mixed, 34) }
        assertThrows(Exception::class.java) {
            PrivateApkComponentCatalog.inspect(store, "not-a-valid-uuid", 34)
        }
    }
}
