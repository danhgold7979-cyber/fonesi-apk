package vn.fonesi.app

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import vn.fonesi.core.InstalledSource
import vn.fonesi.core.update.SourceCatalog
import java.security.MessageDigest

/** Reads the original installed package, not an invented or downloaded release. */
class AndroidSourceCatalog(private val pm: PackageManager) : SourceCatalog {
    @Suppress("DEPRECATION")
    fun info(packageName: String): PackageInfo? = try {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(packageName, flags)
    } catch (_: PackageManager.NameNotFoundException) { null }

    override fun current(packageName: String): InstalledSource? = info(packageName)?.let(::source)

    @Suppress("DEPRECATION")
    fun source(info: PackageInfo): InstalledSource? {
        val app = info.applicationInfo ?: return null
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val digests = signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }.toSet()
        return InstalledSource(info.packageName,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            digests, listOf(app.sourceDir) + app.splitSourceDirs.orEmpty())
    }
}
