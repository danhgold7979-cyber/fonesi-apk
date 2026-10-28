package vn.fonesi.app.runtime

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.system.OsConstants
import com.fvbox.lib.FCore
import vn.fonesi.app.AndroidSourceCatalog
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.app.storage.RoomInstanceRegistry
import vn.fonesi.app.security.AccessBroker
import vn.fonesi.core.*
import vn.fonesi.core.update.FileSourceSnapshotter
import java.util.UUID

/** Native integration is build-time opt-in, startup opt-in and constrained to the audited ABI/API. */
object RuntimeBindings {
    const val candidate = true
    const val supportsSplits = false
    @Volatile private var initialized = false
    @Volatile private var message = "Chưa bật engine thử nghiệm."
    fun status() = message
    private fun preferences(context: Context) = context.getSharedPreferences("candidate_control", Context.MODE_PRIVATE)
    fun key(context: Context): String = synchronized(this) {
        val prefs = preferences(context)
        val epoch = prefs.getString("epoch", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("epoch", it).commit())
        }
        "spacecore-3826a2f/$epoch"
    }
    fun enable(context: Context, enabled: Boolean) {
        check(preferences(context).edit().putBoolean("enabled", enabled).putBoolean("initialization_pending", false).commit())
    }
    @Synchronized fun attach(context: Context) {
        if (initialized) return
        val rejection = ProbePolicy.rejectionReason(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(),
            try { Os.sysconf(OsConstants._SC_PAGESIZE) } catch (_: Exception) { -1 })
        if (rejection != null) { message = rejection; return }
        val prefs = preferences(context)
        if (!prefs.getBoolean("enabled", false)) return
        val process = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        if (process == null) { message = "Chưa xác định được tiến trình để khởi tạo engine."; return }
        val host = process == context.packageName
        if (host && prefs.getBoolean("initialization_pending", false)) {
            message = "Lần khởi tạo trước bị gián đoạn. Tắt engine rồi kiểm tra trước khi bật lại."
            return
        }
        if (host && !prefs.edit().putBoolean("initialization_pending", true).commit()) {
            message = "Không lưu được trạng thái khởi tạo."; return
        }
        try {
            FCore.get().apply {
                init(context, true)
                setAutoPreloadApplication(false); setAutoPreloadApplicationCount(0); setPreloadProcessCount(0)
                setAutoForeground(false); enableOptRule(false)
                setHideRoot(false); setHideSim(false); setHideVPN(false); setHidePath(false)
                setDisableKill(false); setAllowSystemInteraction(true)
            }
            if (host) check(prefs.edit().putBoolean("initialization_pending", false).commit())
            initialized = true
            message = "Engine đã trả về từ khởi tạo. Chưa xác minh clone/cách ly/tài khoản trên máy này."
        } catch (_: Exception) { message = "Engine khởi tạo lỗi; chưa bật thao tác clone." }
        catch (_: LinkageError) { message = "Không nạp được thư viện engine trên cấu hình này." }
    }
    fun engine(context: Context, db: FonesiDatabase): CloneEngine = ManagedGuestEngine(
        SpaceCoreBackend(context) { initialized }, RoomInstanceRegistry(db, key(context)),
        FileSourceSnapshotter(context.filesDir.toPath().resolve("create-snapshots"), AndroidSourceCatalog(context.packageManager)),
        mayMutate = { AccessBroker.get(context).hasForegroundAccess() },
    )
}
