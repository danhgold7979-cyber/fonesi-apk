package vn.fonesi.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import vn.fonesi.core.AppCatalog
import vn.fonesi.core.InstalledSource
import java.text.Collator
import java.util.Locale

data class DeviceApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val source: InstalledSource,
    val icon: Bitmap,
    val priority: Boolean,
)

data class ScanResult(val apps: List<DeviceApp>, val unreadableCount: Int)

class InstalledApps(private val context: Context) {
    @Suppress("DEPRECATION")
    fun scan(): ScanResult {
        val pm = context.packageManager
        val sourceReader = AndroidSourceCatalog(pm)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else pm.queryIntentActivities(intent, 0)
        val packages = activities.map { it.activityInfo.packageName }.distinct()
            .filterNot { it == context.packageName }
        var unreadable = 0
        val apps = packages.mapNotNull { name ->
            try {
                val info = sourceReader.info(name)
                if (info == null) { unreadable++; return@mapNotNull null }
                val appInfo = info.applicationInfo
                if (appInfo == null) {
                    unreadable++
                    return@mapNotNull null
                }
                val drawable = appInfo.loadIcon(pm)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 96, 96)
                drawable.draw(Canvas(bitmap))
                DeviceApp(
                    packageName = name,
                    label = appInfo.loadLabel(pm).toString(),
                    versionName = info.versionName ?: "Không rõ",
                    source = checkNotNull(sourceReader.source(info)),
                    icon = bitmap,
                    priority = AppCatalog.isPriority(name),
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // A package may be removed while scanning; don't fail the entire scan.
                unreadable++
                null
            } catch (_: SecurityException) {
                unreadable++
                null
            }
        }
        val collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"))
        return ScanResult(apps.sortedWith { a, b -> collator.compare(a.label, b.label) }, unreadable)
    }
}
