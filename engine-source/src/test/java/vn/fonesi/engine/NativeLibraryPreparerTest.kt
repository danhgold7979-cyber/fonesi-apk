package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.InstalledSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

class NativeLibraryPreparerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
    private val apkRoot by lazy { temporary.root.toPath().resolve("code") }
    private val nativeRoot by lazy { temporary.root.toPath().resolve("native") }
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun source(vararg names: String): InstalledSource {
        val signers = ApkVerifier.Builder(apk("native-base")).setMinCheckedPlatformVersion(26).build().verify()
            .signerCertificates.map { MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) } }.toSet()
        return InstalledSource("vn.fonesi.fixture", 1, signers, names.map { apk(it).path })
    }
    private fun store() = PrivateApkStore(apkRoot)
    private fun preparer() = NativeLibraryPreparer(store(), nativeRoot)
    private fun prepare(vararg names: String, abi: String = "arm64-v8a", page: Int = 16384): PreparedNativeImage {
        val id = store().publish(source(*names), 34).id
        return preparer().prepare(id, abi, page, 34)
    }
    private fun files(path: Path) = Files.list(path).use { it.iterator().asSequence().map { f -> f.fileName.toString() }.toSet() }
    private fun reject(vararg names: String, abi: String = "arm64-v8a", page: Int = 16384) {
        assertThrows(Exception::class.java) { prepare(*names, abi = abi, page = page) }
        assertTrue(files(nativeRoot).isEmpty())
    }

    @Test fun extractsNativeFromBothBaseAndFeatureWithoutMixingAbi() {
        val image = prepare("native-base", "native-feature")
        assertEquals(setOf("libpayload.so", "libfeature.so"), image.libraries.map { it.name }.toSet())
        ZipFile(apk("native-base")).use { original ->
            assertArrayEquals(original.getInputStream(original.getEntry("lib/arm64-v8a/libpayload.so")).readBytes(),
                image.directory.resolve("libpayload.so").toFile().readBytes())
        }
        assertTrue(preparer().verify(image)); assertEquals(setOf(image.id), files(nativeRoot))
    }
    @Test fun selectsX86AbiWithoutCopyingArm64Library() {
        val image = prepare("native-base", abi = "x86_64")
        ZipFile(apk("native-base")).use { original ->
            assertArrayEquals(original.getInputStream(original.getEntry("lib/x86_64/libpayload.so")).readBytes(),
                image.directory.resolve("libpayload.so").toFile().readBytes())
        }
        assertTrue(preparer().verify(image))
    }
    @Test fun identicalNativeLibrariesAcrossSplitsDeduplicateByVerifiedBytes() {
        val image = prepare("native-base", "native-same-config")
        assertEquals(1, image.libraries.size); assertEquals(setOf("libpayload.so"), files(image.directory))
        assertTrue(preparer().verify(image))
    }
    @Test fun differentLibrariesWithSameNameFailInsteadOfChoosingOneSplit() { reject("native-base", "native-conflict-config") }
    @Test fun noMatchingAbiIsRejectedInsteadOfUsingSystemOrOtherAbi() { reject("native-x86-only") }
    @Test fun headerMachineMustMatchSelectedAbi() { reject("native-wrong-machine") }
    @Test fun signedButInvalidElfIsRejectedAndStageIsCleaned() { reject("native-bad-elf") }
    @Test fun segmentCongruenceMustRespectDeclaredAlignmentEvenOnFourKbDevice() { reject("native-misaligned-segment", page = 4096) }
    @Test fun overflowingVirtualSegmentRangeIsRejectedBeforeNativeLoading() {
        val id = store().publish(source("native-overflow-segment"), 34).id
        assertThrows(ArithmeticException::class.java) { preparer().prepare(id, "arm64-v8a", 4096, 34) }
        assertTrue(files(nativeRoot).isEmpty())
    }
    @Test fun archiveTraversalCannotWriteOutsideStage() {
        reject("native-escape")
        assertFalse(Files.exists(nativeRoot.resolve("outside.so")))
        assertFalse(Files.exists(temporary.root.toPath().resolve("outside.so")))
    }
    @Test fun fourKbElfCannotBeClaimedCompatibleWithSixteenKbPages() {
        reject("native-four-k")
        val image = prepare("native-four-k", page = 4096)
        assertTrue(preparer().verify(image))
        assertFalse(preparer().verify(image.copy(pageSize = 16384)))
    }
    @Test fun resourceOnlyApkNeedsNoNativeFiles() {
        val image = prepare("main-disguised", "base-config")
        assertTrue(image.libraries.isEmpty()); assertTrue(files(image.directory).isEmpty()); assertTrue(preparer().verify(image))
    }
    @Test fun unsupportedAbiOrPageSizeDoesNotCreateImage() {
        val id = store().publish(source("native-base"), 34).id; val preparer = preparer()
        for ((abi, page) in listOf("armeabi-v7a" to 4096, "arm64-v8a" to 8192, "x86" to 16384))
            assertThrows(Exception::class.java) { preparer.prepare(id, abi, page, 34) }
        assertTrue(files(nativeRoot).isEmpty())
    }
    @Test fun modifiedOrMissingExtractedLibraryFailsReadback() {
        val image = prepare("native-base"); val file = image.directory.resolve("libpayload.so").toFile()
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(file.setWritable(true)); file.writeBytes(bytes)
        assertFalse(preparer().verify(image))
        assertTrue(file.delete()); assertFalse(preparer().verify(image))
    }
    @Test fun extraFileOrSymlinkIsRejected() {
        val image = prepare("native-base")
        val extra = image.directory.resolve("extra.so"); Files.write(extra, "extra".toByteArray())
        assertFalse(preparer().verify(image)); Files.delete(extra)
        val library = image.directory.resolve("libpayload.so"); Files.delete(library)
        Files.createSymbolicLink(library, fixtures.toPath().resolve("arm16.so"))
        assertFalse(preparer().verify(image))
    }
    @Test fun invalidReceiptPathsAndRootSymlinksCannotEscapePrivateImage() {
        val image = prepare("native-base")
        assertFalse(preparer().verify(image.copy(id = "../outside")))
        assertFalse(preparer().verify(image.copy(directory = temporary.root.toPath())))
        val link = temporary.root.toPath().resolve("native-link"); Files.createSymbolicLink(link, nativeRoot)
        assertThrows(Exception::class.java) { NativeLibraryPreparer(store(), link) }
    }
    @Test fun failedNewExtractionKeepsExistingImageAndPublishedApks() {
        val first = prepare("native-base")
        val invalid = store().publish(source("native-bad-elf"), 34)
        assertThrows(Exception::class.java) { preparer().prepare(invalid.id, "arm64-v8a", 16384, 34) }
        assertTrue(preparer().verify(first)); assertEquals(setOf(first.id), files(nativeRoot))
        assertEquals(invalid, store().open(invalid.id, 34))
    }
    @Test fun changedPrivateApkCannotBeUsedAsExtractionSource() {
        val cluster = store().publish(source("native-base"), 34)
        val file = cluster.apks.base.file; assertTrue(file.setWritable(true)); file.writeBytes(byteArrayOf(0))
        val preparer = preparer()
        assertThrows(Exception::class.java) { preparer.prepare(cluster.id, "arm64-v8a", 16384, 34) }
        assertTrue(files(nativeRoot).isEmpty())
    }
}
