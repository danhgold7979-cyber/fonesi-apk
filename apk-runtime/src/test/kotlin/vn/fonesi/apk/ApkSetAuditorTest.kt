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

/** Real aapt2 binary manifests and APK signatures; this is not a guest runtime test. */
class ApkSetAuditorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.apkFixtures")))
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun signers() = ApkVerifier.Builder(apk("main-disguised")).setMinCheckedPlatformVersion(26).build().verify()
        .signerCertificates.map { certificate -> MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 255) } }.toSet()
    private fun source(vararg names: String) = InstalledSource("vn.fonesi.fixture", 1, signers(), names.map { apk(it).path })
    private fun reject(source: InstalledSource) {
        assertThrows(Exception::class.java) { ApkSetAuditor.verify(source, 34) }
    }

    @Test fun singleApkVerifiesRealSignatureWithoutRelyingOnBaseFilename() {
        for (api in listOf(26, 34, 36)) {
            val result = ApkSetAuditor.verify(source("main-disguised"), api)
            assertEquals(apk("main-disguised"), result.base.file)
            assertNull(result.base.splitName); assertTrue(result.splits.isEmpty())
            assertEquals(64, result.base.sha256.length); assertEquals(signers(), result.base.signers)
        }
    }
    @Test fun clusterKeepsAllFeaturesConfigurationsAndDependenciesRegardlessOfInputOrder() {
        val result = ApkSetAuditor.verify(source("chat-config", "dependent-feature", "base", "main-disguised", "base-config"), 34)
        assertEquals("main-disguised.apk", result.base.file.name)
        assertEquals(setOf("feature.chat", "feature.chat.en", "feature.pay", "config.en"), result.splits.map { it.splitName }.toSet())
        assertEquals(setOf("feature.chat"), result.splits.single { it.splitName == "feature.pay" }.dependencies)
        assertEquals("feature.chat", result.splits.single { it.splitName == "feature.chat.en" }.configFor)
    }
    @Test fun versionCodeMajorUsesFullLongInsteadOfTruncatingToInt() {
        val source = source("version-major-config", "version-major").copy(versionCode = (1L shl 32) or 7)
        assertEquals(source.versionCode, ApkSetAuditor.verify(source, 34).base.version)
        reject(source.copy(versionCode = 7))
    }
    @Test fun unsignedAndTamperedArchivesNeverPassUsingClaimedSourceCertificates() {
        reject(source("unsigned")); reject(source("tampered"))
    }
    @Test fun signedTextManifestIsRejectedAsInvalidApk() {
        reject(source("text-manifest"))
    }
    @Test fun oversizedSignedManifestIsRejectedBeforeSignatureLibraryReadsIt() {
        val error = assertThrows(IllegalArgumentException::class.java) { ApkSetAuditor.verify(source("oversized-manifest"), 34) }
        assertEquals("Manifest exceeds size budget", error.message)
    }
    @Test fun wrongSourcePackageVersionOrSigningIdentityIsRejected() {
        reject(source("main-disguised").copy(packageName = "vn.fonesi.other"))
        reject(source("main-disguised").copy(versionCode = 2))
        reject(source("main-disguised").copy(signerDigests = setOf("not-a-certificate")))
    }
    @Test fun mixedPackageVersionAndSignerAreRejectedForEverySplit() {
        reject(source("main-disguised", "wrong-package"))
        reject(source("main-disguised", "new-version-config"))
        reject(source("main-disguised", "wrong-signer"))
    }
    @Test fun exactlyOneBaseIsRequired() {
        reject(source("base", "chat-config"))
        val secondBase = temporary.newFile("another.apk").also { apk("main-disguised").copyTo(it, overwrite = true) }
        reject(source("main-disguised").let { it.copy(apkPaths = it.apkPaths + secondBase.path) })
    }
    @Test fun duplicateSplitIdentityAndDuplicatePathsAreRejected() {
        val copy = temporary.newFile("renamed-feature.apk").also { apk("base").copyTo(it, overwrite = true) }
        reject(source("main-disguised", "base").let { it.copy(apkPaths = it.apkPaths + copy.path) })
        reject(source("main-disguised", "main-disguised"))
    }
    @Test fun missingFeatureOrConfigurationDependencyIsRejected() {
        reject(source("main-disguised", "missing-dependency"))
        reject(source("main-disguised", "missing-config-target"))
        reject(source("main-disguised", "dependent-feature"))
    }
    @Test fun dependencyCycleIsRejectedInsteadOfRecursingOrDroppingFeature() {
        reject(source("main-disguised", "cycle-a", "cycle-b"))
    }
    @Test fun splitRequiredBaseCannotBeAcceptedAsStandalone() {
        reject(source("requires-any-split"))
        val verified = ApkSetAuditor.verify(source("requires-any-split", "base-config"), 34)
        assertTrue(verified.base.splitRequired); assertEquals(1, verified.splits.size)
    }
    @Test fun requiredSplitTypesMustBePresentInsteadOfAcceptingAnyRandomSplit() {
        reject(source("requires-abi-type", "base-config"))
        val verified = ApkSetAuditor.verify(source("requires-abi-type", "provides-abi-type"), 34)
        assertEquals(setOf("base__abi"), verified.base.requiredTypes)
        assertEquals(setOf("base__abi"), verified.splits.single().providedTypes)
    }
    @Test fun symlinkFileAndSymlinkDirectoryAreRejected() {
        val fileLink = temporary.root.toPath().resolve("link.apk")
        Files.createSymbolicLink(fileLink, apk("main-disguised").toPath())
        reject(source("main-disguised").copy(apkPaths = listOf(fileLink.toString())))
        val directoryLink = temporary.root.toPath().resolve("dir")
        Files.createSymbolicLink(directoryLink, fixtures.toPath())
        reject(source("main-disguised").copy(apkPaths = listOf(directoryLink.resolve("main-disguised.apk").toString())))
    }
    @Test fun missingEmptyOrNonApkInputFailsWithoutModifyingOriginal() {
        val original = apk("main-disguised").readBytes()
        val empty = temporary.newFile("empty.apk")
        reject(source("main-disguised").copy(apkPaths = listOf(empty.path)))
        reject(source("nonexistent"))
        reject(source())
        assertArrayEquals(original, apk("main-disguised").readBytes())
    }
}
