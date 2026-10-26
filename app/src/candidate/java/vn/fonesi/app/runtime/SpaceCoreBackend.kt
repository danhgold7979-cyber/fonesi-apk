package vn.fonesi.app.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.fvbox.lib.FCore
import vn.fonesi.core.GuestBackend
import vn.fonesi.core.GuestPackage
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Calls the pinned SDK. No source-package launch, session copying or device identity spoofing. */
class SpaceCoreBackend(private val context: Context, private val isReady: () -> Boolean) : GuestBackend {
    override val ready get() = isReady()
    private val core get() = FCore.get().also { check(ready) }
    override fun users() = core.users.map { it.id }.toSet()
    override fun createUser(userId: Int) { checkNotNull(core.createUser(userId)); core.setEnableDevice(userId, false) }
    @Suppress("DEPRECATION")
    private val flags get() = PackageManager.GET_SIGNATURES or if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
    override fun inspect(userId: Int, packageName: String): GuestPackage? {
        if (!core.isInstalled(packageName, userId)) return null
        val info = checkNotNull(core.getPackageInfo(packageName, flags, userId))
        val copy = ownedCode(info)
        @Suppress("DEPRECATION")
        val archive = checkNotNull(context.packageManager.getPackageArchiveInfo(copy.path, flags))
        check(archive.packageName == info.packageName && version(archive) == version(info) && signers(archive) == signers(info)) {
            "Guest metadata does not match the private APK"
        }
        return GuestPackage(info.packageName, version(info), signers(info), core.isRunning(packageName, userId))
    }
    @Suppress("DEPRECATION")
    override fun install(userId: Int, source: InstalledSource) {
        check(source.apkPaths.size == 1)
        val input = File(source.apkPaths.single()).canonicalFile
        check(input.isFile && input.length() > 0)
        val archive = checkNotNull(context.packageManager.getPackageArchiveInfo(input.path, flags))
        check(archive.packageName == source.packageName && version(archive) == source.versionCode && signers(archive) == source.signerDigests)
        ZipFile(input).use { zip ->
            val nativeAbis = zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.map { it.name.split('/')[1] }.toSet()
            check(nativeAbis.isEmpty() || "arm64-v8a" in nativeAbis) { "Candidate requires ARM64 guest libraries" }
        }
        val expectedHash = hash(input)
        val result = core.installPackageAsUser(input, userId)
        check(result.success && result.packageName == source.packageName) { "Native import not confirmed" }
        val info = checkNotNull(core.getPackageInfo(source.packageName, flags, userId))
        check(version(info) == source.versionCode && signers(info) == source.signerDigests)
        val installed = ownedCode(info)
        check(installed != input && !installed.toPath().startsWith(checkNotNull(input.parentFile).toPath()) &&
            hash(installed) == expectedHash && hash(input) == expectedHash) {
            "SDK did not verify an independent private copy; retaining snapshot for review"
        }
    }
    override fun launch(userId: Int, packageName: String) { core.launchApk(packageName, userId) }
    override fun stop(userId: Int, packageName: String) { core.stopPackage(packageName, userId) }
    override fun uninstall(userId: Int, packageName: String) { core.uninstallPackageAsUser(packageName, userId) }
    private fun ownedCode(info: PackageInfo): File {
        val file = File(checkNotNull(info.applicationInfo).sourceDir).canonicalFile
        check(file.isFile && file.toPath().startsWith(context.dataDir.canonicalFile.toPath()) &&
            !file.toPath().startsWith(File(context.filesDir, "create-snapshots").canonicalFile.toPath()) &&
            !file.toPath().startsWith(File(context.filesDir, "updates/snapshots").canonicalFile.toPath())) {
            "Guest code is not an independent SDK-owned private file"
        }
        return file
    }
    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else null) ?: info.signatures
        return signatures.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet().also { check(it.isNotEmpty()) }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
