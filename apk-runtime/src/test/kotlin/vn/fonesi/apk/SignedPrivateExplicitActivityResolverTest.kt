package vn.fonesi.apk

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest

/** Real signed binary manifests; not Android Activity execution or a permission broker. */
class SignedPrivateExplicitActivityResolverTest {
    @get:Rule val temp = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.apkFixtures")))
    private val store by lazy { PrivateApkStore(temp.root.toPath().resolve("private")) }
    private val pkg = "vn.fonesi.fixture"
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun signers(): Set<String> = ApkVerifier.Builder(apk("catalog-public"))
        .setMinCheckedPlatformVersion(26).build().verify().signerCertificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { b -> "%02x".format(b.toInt() and 255) }
        }.toSet()
    private fun manifest(vararg names: String): VerifiedPrivateComponentInventory {
        val cluster = store.publish(InstalledSource(pkg, 1L, signers(), names.map { apk(it).path }), 34)
        return PrivateApkComponentCatalog.inspect(store, cluster.id, 34)
    }
    private fun select(
        inventory: VerifiedPrivateComponentInventory,
        name: String,
        caller: ExplicitActivityCaller = ExplicitActivityCaller.EXTERNAL_UNTRUSTED,
    ) = SignedPrivateExplicitActivityResolver.select(inventory, pkg, "$pkg.$name", caller)

    @Test fun signedExplicitPublicActivityMayBeSelectedWithoutStartingAndroidActivity() {
        val signed = manifest("catalog-public")
        val selection = select(signed, "PublicActivity")
        assertEquals(signed.clusterId, selection.clusterId)
        assertEquals(pkg, selection.packageName)
        assertEquals("$pkg.PublicActivity", selection.requestedName)
        assertEquals(selection.requestedName, selection.targetActivityName)
        assertNull(selection.requestedSplit)
        assertNull(selection.targetSplit)
        assertFalse(selection.throughAlias)
    }

    @Test fun internalActivityMayBeNotExportedButExternalRequestFailsClosed() {
        val signed = manifest("catalog-public")
        val internal = select(signed, "LocalActivity", ExplicitActivityCaller.TRUSTED_INTERNAL)
        assertEquals("$pkg.LocalActivity", internal.targetActivityName)
        assertThrows(IllegalArgumentException::class.java) { select(signed, "LocalActivity") }
    }

    @Test fun exportedOrEnabledNotExplicitlyDeclaredIsNeverAuthorizedExternally() {
        val signed = manifest("catalog-public")
        assertNull(signed.components.single { it.className == "$pkg.UnknownExported" }.exported)
        assertNull(signed.components.single { it.className == "$pkg.UnknownEnabled" }.enabled)
        assertThrows(IllegalArgumentException::class.java) { select(signed, "UnknownExported") }
        assertThrows(IllegalArgumentException::class.java) { select(signed, "UnknownEnabled") }
        assertEquals("$pkg.UnknownExported",
            select(signed, "UnknownExported", ExplicitActivityCaller.TRUSTED_INTERNAL).targetActivityName)
    }

    @Test fun disabledActivityAliasAndAliasTargetAreRejectedForAllCallers() {
        val signed = manifest("catalog-public")
        for (name in listOf("DisabledActivity", "DisabledAlias", "AliasToDisabled")) {
            for (caller in ExplicitActivityCaller.values()) {
                assertThrows(IllegalArgumentException::class.java) { select(signed, name, caller) }
            }
        }
    }

    @Test fun permissionProtectedActivityRequiresUnimplementedPermissionMediator() {
        val signed = manifest("catalog-public")
        val protected = signed.components.single { it.className == "$pkg.ProtectedActivity" }
        assertEquals("vn.fonesi.fixture.ACCESS", protected.permission)
        for (caller in ExplicitActivityCaller.values()) {
            assertThrows(IllegalArgumentException::class.java) {
                select(signed, "ProtectedActivity", caller)
            }
        }
    }

    @Test fun exportedSignedAliasSelectsExactlyItsPrivateActivityTarget() {
        val signed = manifest("catalog-public")
        val selection = select(signed, "PublicAlias")
        assertTrue(selection.throughAlias)
        assertEquals("$pkg.PublicAlias", selection.requestedName)
        assertEquals("$pkg.AliasTarget", selection.targetActivityName)
        assertNull(selection.requestedSplit)
        assertNull(selection.targetSplit)
        assertThrows(IllegalArgumentException::class.java) { select(signed, "AliasTarget") }
    }

    @Test fun cannotSubstituteAnotherPackageComponentKindOrImplicitEntry() {
        val signed = manifest("catalog-public")
        assertThrows(IllegalArgumentException::class.java) {
            SignedPrivateExplicitActivityResolver.select(signed, "vn.fonesi.other",
                "$pkg.PublicActivity", ExplicitActivityCaller.EXTERNAL_UNTRUSTED)
        }
        assertThrows(IllegalArgumentException::class.java) { select(signed, "NotActivity") }
        assertThrows(IllegalArgumentException::class.java) { select(signed, "MissingActivity") }
        assertThrows(IllegalArgumentException::class.java) {
            SignedPrivateExplicitActivityResolver.select(signed, pkg,
                "", ExplicitActivityCaller.TRUSTED_INTERNAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SignedPrivateExplicitActivityResolver.select(signed, pkg,
                "PublicActivity", ExplicitActivityCaller.EXTERNAL_UNTRUSTED)
        }
    }

    @Test fun inheritedSignedApplicationPermissionBlocksActivityWithoutPermissionBroker() {
        val signed = manifest("catalog-app-protected")
        assertEquals("vn.fonesi.fixture.ACCESS", signed.applicationPermission)
        assertNull(signed.applicationEnabled)
        assertNull(signed.components.single().permission)
        for (caller in ExplicitActivityCaller.values()) {
            assertThrows(IllegalArgumentException::class.java) {
                select(signed, "AppProtectedActivity", caller)
            }
        }
    }

    @Test fun disabledSignedApplicationBlocksEvenExplicitlyEnabledPublicActivity() {
        val signed = manifest("catalog-app-disabled")
        assertEquals(false, signed.applicationEnabled)
        assertNull(signed.applicationPermission)
        val activity = signed.components.single()
        assertEquals(true, activity.enabled)
        assertEquals(true, activity.exported)
        for (caller in ExplicitActivityCaller.values()) {
            assertThrows(IllegalArgumentException::class.java) {
                select(signed, "AppDisabledActivity", caller)
            }
        }
    }

    @Test fun featureSplitCannotOverrideBaseApplicationPermissionPolicy() {
        val cluster = store.publish(InstalledSource(pkg, 1L, signers(), listOf(
            apk("catalog-public").path, apk("catalog-split-app-permission").path
        )), 34)
        assertThrows(IllegalArgumentException::class.java) {
            PrivateApkComponentCatalog.inspect(store, cluster.id, 34)
        }
    }

    @Test fun featureActivityCanOnlyBeResolvedFromItsVerifiedSignedSplit() {
        val signed = manifest("catalog-public", "catalog-public-feature", "catalog-config")
        val selection = select(signed, "FeatureActivity")
        assertEquals("feature.public", selection.requestedSplit)
        assertEquals("feature.public", selection.targetSplit)
        assertEquals(signed.clusterId, selection.clusterId)
        val withoutFeature = manifest("catalog-public")
        assertThrows(IllegalArgumentException::class.java) { select(withoutFeature, "FeatureActivity") }
    }
}
