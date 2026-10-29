package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.apk.PrivateApkComponentCatalog
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest

/** Real signed binary base/feature/config metadata. No guest Android component executes. */
class PrivateGuestComponentPreflightTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
    private val store by lazy { PrivateApkStore(temporary.root.toPath().resolve("code")) }
    private fun apk(name: String) = File(fixtures, name + ".apk")
    private fun signer() = ApkVerifier.Builder(apk("catalog-base"))
        .setMinCheckedPlatformVersion(26).build().verify().signerCertificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.toSet()
    private fun publish(vararg names: String): String = store.publish(InstalledSource(
        "vn.fonesi.fixture", 1L, signer(), names.map { apk(it).path }), 34).id
    private fun layout(id: String) =
        GuestCodeLayout.read(store, id, 34, temporary.root, "vn.fonesi.host")
    private fun signed(id: String) = PrivateApkComponentCatalog.inspect(store, id, 34)

    @Test fun singleSignedPrivateBaseCatalogMatchesExactlyItsLoadedCodeLayout() {
        val id = publish("catalog-base")
        val l = layout(id)
        val manifest = PrivateGuestComponentPreflight.requireMatches(l, signed(id))
        assertEquals(id, manifest.clusterId)
        assertEquals(l.cluster.clusterDigest, manifest.clusterDigest)
        assertEquals(l.cluster.apks.base.packageName, manifest.packageName)
        assertEquals(6, manifest.components.size)
        assertTrue(manifest.splitNames.isEmpty())
        assertTrue(manifest.components.all { it.sourceSplit == null })
    }

    @Test fun signedPrivateBaseFeatureConfigManifestKeepsAllSourceSplitIdentities() {
        val id = publish("catalog-config", "catalog-feature", "catalog-base")
        val l = layout(id)
        val manifest = PrivateGuestComponentPreflight.requireMatches(l, signed(id))
        assertEquals(listOf("config.de", "feature.catalog"), manifest.splitNames)
        assertEquals(7, manifest.components.size)
        assertTrue(manifest.components.any { it.sourceSplit == "feature.catalog" })
        assertTrue(manifest.components.none { it.sourceSplit == "config.de" })
        assertEquals(l.cluster.apks.base.signers, manifest.signerDigests)
    }

    @Test fun differentClusterEvenWithSamePackageAndSignersCannotImpersonatePrivateCodeImage() {
        val id1 = publish("catalog-base")
        val id2 = publish("catalog-base")
        val fromOne = signed(id1)
        assertNotEquals(id1, id2)
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestComponentPreflight.requireMatches(layout(id2), fromOne)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestComponentPreflight.requireMatches(layout(id1),
                fromOne.copy(clusterDigest = "0".repeat(64)))
        }
    }

    @Test fun alteredSignerVersionSplitOrInventedComponentOriginIsRejected() {
        val id = publish("catalog-base", "catalog-feature", "catalog-config")
        val l = layout(id)
        val ref = signed(id)
        val changes = listOf(
            ref.copy(versionCode = ref.versionCode + 1),
            ref.copy(signerDigests = setOf("f".repeat(64))),
            ref.copy(splitNames = listOf("feature.catalog")),
            ref.copy(components = ref.components.mapIndexed { index, component ->
                if (index == 0) component.copy(sourceSplit = "feature.unknown") else component
            }),
        )
        changes.forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) {
                PrivateGuestComponentPreflight.requireMatches(l, candidate)
            }
        }
    }

    @Test fun isolatedSignedGraphCanBeCataloguedButProductionCodeLoaderRemainsBlocked() {
        val id = publish("isolated-base", "base-config")
        val l = GuestCodeLayout.readForGraphOnly(store, id, 34, temporary.root, "vn.fonesi.host")
        assertTrue(l.cluster.apks.base.isolatedSplits)
        val inventory = PrivateGuestComponentPreflight.requireMatches(l, signed(id))
        assertEquals(id, inventory.clusterId)
        assertThrows(IllegalArgumentException::class.java) {
            GuestCodeLayout.read(store, id, 34, temporary.root, "vn.fonesi.host")
        }
    }

    @Test fun modifiedPrivateApkFailsVerificationInsteadOfUsingOriginalCode() {
        val id = publish("catalog-base")
        val base = store.open(id, 34).apks.base.file
        assertTrue(base.setWritable(true))
        base.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(Exception::class.java) { signed(id) }
        assertTrue(apk("catalog-base").isFile)
    }
}
