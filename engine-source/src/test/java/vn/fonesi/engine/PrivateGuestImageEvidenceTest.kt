package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.After
import org.junit.Test
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.GuestImageRef
import vn.fonesi.core.InstalledSource
import java.nio.file.Files
import java.util.Comparator
import java.security.MessageDigest
import java.io.File

/** Real signed APK fixture readback; no guest Application or activity is executed. */
class PrivateGuestImageEvidenceTest {
    private val root = Files.createTempDirectory("fonesi-image-evidence-")
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
    private val store = PrivateApkStore(root.resolve("images"))
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun signer(): Set<String> = ApkVerifier.Builder(apk("main-disguised"))
        .setMinCheckedPlatformVersion(26).build().verify().signerCertificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.toSet()
    private fun image(vararg names: String): GuestImageRef {
        val original = InstalledSource("vn.fonesi.fixture", 1, signer(), names.map { apk(it).path })
        val cluster = store.publish(original, 34)
        return PrivateGuestImageEvidence(store, 34).resolve(cluster.id)
    }

    @After fun cleanup() {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }

    @Test fun signedPrivateBaseAndSplitsResolveToExactCodeIdentity() {
        val evidence = PrivateGuestImageEvidence(store, 34)
        val verified = image("main-disguised", "base-config", "dependent-feature", "chat-config", "base")
        check(evidence.matches(verified))
        check(verified.packageName == "vn.fonesi.fixture")
        check(verified.versionCode == 1L)
        check(verified.signerSha256 == signer())
    }

    @Test fun differentImageIdOrVersionCannotImpersonateTheStoredCode() {
        val verified = image("main-disguised")
        val evidence = PrivateGuestImageEvidence(store, 34)
        check(!evidence.matches(verified.copy(versionCode = verified.versionCode + 1)))
        check(!evidence.matches(verified.copy(packageName = "vn.fonesi.other")))
    }

    @Test fun tamperedPrivateClusterFailsWithoutSourceFallback() {
        val verified = image("main-disguised", "base-config")
        val privateBase = root.resolve("images").resolve(verified.clusterId).resolve("base.apk")
        privateBase.toFile().setWritable(true)
        Files.write(privateBase, byteArrayOf(1, 2, 3))
        check(runCatching { PrivateGuestImageEvidence(store, 34).matches(verified) }.isFailure)
        check(apk("main-disguised").isFile)
    }
}
