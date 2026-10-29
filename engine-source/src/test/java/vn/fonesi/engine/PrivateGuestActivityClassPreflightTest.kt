package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.apk.ExplicitActivityCaller
import vn.fonesi.apk.PrivateApkComponentCatalog
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.apk.SignedPrivateExplicitActivityResolver
import vn.fonesi.apk.VerifiedPrivateActivitySelection
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest

/**
 * Private-class ownership/type protocol tests. Neither JVM reflection nor the signed
 * manifest fixtures execute or instantiate an actual Android guest Activity.
 */
class PrivateGuestActivityClassPreflightTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun selection(target: Class<*>, alias: Boolean = false) =
        VerifiedPrivateActivitySelection(
            "test-private-cluster", "vn.fonesi.fixture",
            if (alias) "vn.fonesi.fixture.SignedAlias" else target.name,
            target.name, null, null, alias,
        )

    private val ownedLoader get() = ConcretePrivateActivityForTest::class.java.classLoader
    private val activityBase get() = ExpectedActivityBaseForTest::class.java

    @Test fun concreteActivityClassWithExactDefiningLoaderPassesPreflightWithoutInstantiation() {
        val selected = selection(ConcretePrivateActivityForTest::class.java)
        val loaded = PrivateGuestActivityClassPreflight.resolve(selected, ownedLoader, activityBase)
        assertSame(ConcretePrivateActivityForTest::class.java, loaded)
        assertSame(ownedLoader, loaded.classLoader)
    }

    @Test fun signedAliasPreflightsItsConcreteTargetInsteadOfAnAliasClass() {
        val selected = selection(ConcretePrivateActivityForTest::class.java, alias = true)
        val loaded = PrivateGuestActivityClassPreflight.resolve(selected, ownedLoader, activityBase)
        assertTrue(selected.throughAlias)
        assertNotEquals(selected.requestedName, loaded.name)
        assertEquals(selected.targetActivityName, loaded.name)
    }

    @Test fun candidateWithDifferentNameCannotImpersonateSignedTarget() {
        val selected = selection(ConcretePrivateActivityForTest::class.java)
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestActivityClassPreflight.requireMatches(
                selected, OtherPrivateActivityForTest::class.java, ownedLoader, activityBase)
        }
    }

    @Test fun parentDelegatedHostClassCannotImpersonatePrivateClassloaderDefinition() {
        val delegated = object : ClassLoader(ownedLoader) {}
        val candidate = Class.forName(ConcretePrivateActivityForTest::class.java.name, false, delegated)
        assertSame(ConcretePrivateActivityForTest::class.java, candidate)
        assertNotSame(delegated, candidate.classLoader)
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestActivityClassPreflight.requireMatches(
                selection(candidate), candidate, delegated, activityBase)
        }
    }

    @Test fun unrelatedClassAndActivityBaseItselfAreNotConcreteGuestActivities() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestActivityClassPreflight.requireMatches(
                selection(UnrelatedPrivateComponentForTest::class.java),
                UnrelatedPrivateComponentForTest::class.java, ownedLoader, activityBase)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrivateGuestActivityClassPreflight.requireMatches(
                selection(activityBase), activityBase, ownedLoader, activityBase)
        }
    }

    @Test fun abstractOrNonStaticInnerActivityCannotBeUsedAsGuestEntrypoint() {
        for (type in listOf(
            AbstractPrivateActivityForTest::class.java,
            ActivityOuterForTest.InnerActivity::class.java,
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                PrivateGuestActivityClassPreflight.requireMatches(
                    selection(type), type, ownedLoader, activityBase)
            }
        }
        val standalone = ActivityOuterForTest.StaticActivity::class.java
        assertSame(standalone, PrivateGuestActivityClassPreflight.requireMatches(
            selection(standalone), standalone, ownedLoader, activityBase))
    }

    @Test fun missingPrivateActivityClassNeverFallsBackToInstalledOriginalOrHost() {
        val missing = selection(ConcretePrivateActivityForTest::class.java)
            .copy(targetActivityName = "vn.fonesi.fixture.MissingSignedActivity")
        assertThrows(ClassNotFoundException::class.java) {
            PrivateGuestActivityClassPreflight.resolve(missing, ownedLoader, activityBase)
        }
    }

    @Test fun publicZeroArgumentConstructorIsInspectedButNeverInvoked() {
        ConstructorSideEffectForTest.count = 0
        val type = ConstructorSideEffectActivityForTest::class.java
        val loaded = PrivateGuestActivityClassPreflight.requireMatches(
            selection(type), type, ownedLoader, activityBase)
        assertSame(type, loaded)
        assertEquals(0, ConstructorSideEffectForTest.count)
    }

    @Test fun parameterizedOnlyPrivateAndProtectedConstructorsAreRejected() {
        val bad = listOf(
            ParameterizedOnlyActivityForTest::class.java,
            PrivateConstructorActivityForTest::class.java,
            ProtectedConstructorActivityForTest::class.java,
        )
        bad.forEach { type ->
            assertThrows(IllegalArgumentException::class.java) {
                PrivateGuestActivityClassPreflight.requireMatches(
                    selection(type), type, ownedLoader, activityBase)
            }
        }
        val valid = PublicNoArgAndParameterizedActivityForTest::class.java
        assertSame(valid, PrivateGuestActivityClassPreflight.requireMatches(
            selection(valid), valid, ownedLoader, activityBase))
    }

    @Test fun realSignedPrivateManifestWithoutDexIsNotAnExecutableGuestActivity() {
        // The aapt2/apksigner fixture declares an allowed Activity but has no classes.dex.
        // Manifest authorization alone cannot be accepted as an Android guest runtime.
        val fixtures = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
        val apk = File(fixtures, "catalog-public.apk")
        val signers = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(26).build()
            .verify().signerCertificates.map { certificate ->
                MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
            }.toSet()
        val store = PrivateApkStore(tmp.root.toPath().resolve("images"))
        val cluster = store.publish(InstalledSource("vn.fonesi.fixture", 1L, signers,
            listOf(apk.absolutePath)), 34)
        val signed = PrivateApkComponentCatalog.inspect(store, cluster.id, 34)
        val selected = SignedPrivateExplicitActivityResolver.select(signed, signed.packageName,
            "vn.fonesi.fixture.PublicActivity", ExplicitActivityCaller.EXTERNAL_UNTRUSTED)
        assertEquals(cluster.id, selected.clusterId)
        assertThrows(ClassNotFoundException::class.java) {
            PrivateGuestActivityClassPreflight.resolve(selected, ownedLoader, activityBase)
        }
    }
}

open class ExpectedActivityBaseForTest
class ConcretePrivateActivityForTest : ExpectedActivityBaseForTest()
class OtherPrivateActivityForTest : ExpectedActivityBaseForTest()
abstract class AbstractPrivateActivityForTest : ExpectedActivityBaseForTest()
class UnrelatedPrivateComponentForTest
class ActivityOuterForTest {
    inner class InnerActivity : ExpectedActivityBaseForTest()
    class StaticActivity : ExpectedActivityBaseForTest()
}

object ConstructorSideEffectForTest { var count: Int = 0 }
class ConstructorSideEffectActivityForTest : ExpectedActivityBaseForTest() {
    init { ConstructorSideEffectForTest.count++ }
}
class ParameterizedOnlyActivityForTest(value: String) : ExpectedActivityBaseForTest()
class PrivateConstructorActivityForTest private constructor() : ExpectedActivityBaseForTest()
class ProtectedConstructorActivityForTest protected constructor() : ExpectedActivityBaseForTest()
class PublicNoArgAndParameterizedActivityForTest() : ExpectedActivityBaseForTest() {
    constructor(value: String) : this()
}
