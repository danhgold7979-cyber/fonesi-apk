package vn.fonesi.app

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.system.Os
import android.system.OsConstants

data class DeviceDiagnostics(
    val model: String, val api: Int, val abis: List<String>, val pageSize: Long?,
    val freeBytes: Long?, val appBytes: Long?, val dataBytes: Long?, val cacheBytes: Long?, val ignoresBatteryOptimization: Boolean?,
) {
    companion object {
        /** Own package only. Never requests Usage Access or scans guest file names/contents. */
        fun read(context: Context): DeviceDiagnostics {
            val stats = runCatching {
                context.getSystemService(StorageStatsManager::class.java)
                    .queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle())
            }.getOrNull()
            return DeviceDiagnostics(Build.MODEL, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(),
                runCatching { Os.sysconf(OsConstants._SC_PAGESIZE).takeIf { it > 0 } }.getOrNull(),
                runCatching { StatFs(context.filesDir.path).availableBytes.takeIf { it >= 0 } }.getOrNull(),
                stats?.appBytes?.takeIf { it >= 0 }, stats?.dataBytes?.takeIf { it >= 0 }, stats?.cacheBytes?.takeIf { it >= 0 },
                runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrNull())
        }
    }
}
