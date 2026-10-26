package vn.fonesi.probe

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.system.OsConstants
import com.fvbox.lib.FCore
import vn.fonesi.core.ProbePolicy

sealed interface ProbeRuntime {
    data class NotStarted(val message: String) : ProbeRuntime
    data object Initialized : ProbeRuntime
}

class ProbeApplication : Application() {
    var runtime: ProbeRuntime = ProbeRuntime.NotStarted("Chưa bật SDK thử nghiệm.")
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val rejection = ProbePolicy.rejectionReason(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), pageSize())
        if (rejection != null) {
            runtime = ProbeRuntime.NotStarted(rejection)
            return
        }
        val preferences = getSharedPreferences("probe_control", MODE_PRIVATE)
        if (!preferences.getBoolean("enabled", false)) return
        val process = if (Build.VERSION.SDK_INT >= 28) getProcessName() else {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        val host = process == packageName
        if (host && preferences.getBoolean("initialization_pending", false)) {
            runtime = ProbeRuntime.NotStarted("Lần khởi tạo trước chưa hoàn tất. Hãy tắt SDK; không tiếp tục thử tài khoản.")
            return
        }
        if (host && !preferences.edit().putBoolean("initialization_pending", true).commit()) {
            runtime = ProbeRuntime.NotStarted("Không lưu được trạng thái khởi tạo; SDK chưa được bật.")
            return
        }
        try {
            FCore.get().init(this, true)
            FCore.get().setAutoPreloadApplication(false)
            FCore.get().setAutoPreloadApplicationCount(0)
            FCore.get().setPreloadProcessCount(0)
            FCore.get().setAutoForeground(false)
            FCore.get().enableOptRule(false)
            FCore.get().setHideRoot(false)
            FCore.get().setHideSim(false)
            FCore.get().setHideVPN(false)
            FCore.get().setHidePath(false)
            FCore.get().setDisableKill(false)
            FCore.get().setAllowSystemInteraction(true)
            if (host) {
                check(preferences.edit().putBoolean("initialization_pending", false).commit())
            }
            runtime = ProbeRuntime.Initialized
        } catch (error: Exception) {
            runtime = ProbeRuntime.NotStarted("SDK khởi tạo thất bại: ${error.javaClass.simpleName}.")
        } catch (error: LinkageError) {
            runtime = ProbeRuntime.NotStarted("Không nạp được SDK/native: ${error.javaClass.simpleName}.")
        }
    }

    companion object {
        fun pageSize(): Long = try { Os.sysconf(OsConstants._SC_PAGESIZE) } catch (_: Exception) { -1 }
    }
}
