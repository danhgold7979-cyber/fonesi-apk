package vn.fonesi.probe

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import vn.fonesi.core.ProbePolicy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Phase 1 laboratory UI; cannot select or clone third-party applications. */
class ProbeActivity : Activity() {
    private lateinit var output: TextView
    private val buttons = mutableListOf<Button>()
    private val bridge by lazy { SpaceCoreProbe(this) }
    private val app get() = application as ProbeApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun text(value: String) = TextView(this).apply {
            text = value; textSize = 16f; layout.addView(this)
        }
        text("Fonesi · Phase 1 / thử engine").textSize = 24f
        text("Chỉ thử dữ liệu mẫu. Chưa được dùng Facebook, Zalo hoặc tài khoản thật. Bản này không có quyền Internet.")
        text("Máy: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\nABI: ${Build.SUPPORTED_ABIS.joinToString()}\nTrang bộ nhớ: ${ProbeApplication.pageSize()} byte")
        text(when (val runtime = app.runtime) {
            is ProbeRuntime.NotStarted -> runtime.message
            ProbeRuntime.Initialized -> "SDK đã trả về từ khởi tạo. Chưa xác minh được clone hay cách ly dữ liệu."
        })
        output = text(lastResult)
        fun button(label: String, readyOnly: Boolean = true, action: () -> Unit) {
            layout.addView(Button(this).apply {
                this.text = label
                isEnabled = !busy.get() && (!readyOnly || app.runtime == ProbeRuntime.Initialized)
                setOnClickListener { action() }
                buttons += this
            })
        }
        if (app.runtime != ProbeRuntime.Initialized) {
            val reason = ProbePolicy.rejectionReason(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), ProbeApplication.pageSize())
            if (reason == null) {
                text("SDK là thư viện mã đóng bên thứ ba, dùng thử ngoại tuyến. Bật SDK sẽ đóng app; bạn mở lại biểu tượng để khởi tạo. Chưa bật thì SDK không được chủ động khởi tạo bởi Application.")
                button("Bật SDK và đóng bản thử", readyOnly = false) { changeEnabledAndClose(true) }
            }
        }
        button("Tắt SDK và đóng bản thử", readyOnly = false) { changeEnabledAndClose(false) }
        text("Cài APK ‘Fonesi dữ liệu mẫu’ cùng bản build trước. Mở bản gốc, lưu GỐC. Sau đó tạo A/B và lưu nhãn khác nhau.")
        button("Đọc trạng thái từ SDK") { operation { bridge.status() } }
        button("Tạo vùng A (1001)") { operation { bridge.create(1001) } }
        button("Mở vùng A") { operation { bridge.launch(1001); "Đã yêu cầu SDK mở A. Chỉ ghi đạt sau khi màn hình dữ liệu mẫu xuất hiện." } }
        button("Dừng vùng A") { operation { bridge.stop(1001) } }
        button("Tạo vùng B (1002)") { operation { bridge.create(1002) } }
        button("Mở vùng B") { operation { bridge.launch(1002); "Đã yêu cầu SDK mở B. Chỉ ghi đạt sau khi màn hình dữ liệu mẫu xuất hiện." } }
        button("Dừng vùng B") { operation { bridge.stop(1002) } }
        button("Xóa dữ liệu mẫu vùng A…") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Xóa bản dữ liệu mẫu A?")
                .setMessage("Chỉ yêu cầu xóa bản mẫu vùng 1001 trong SDK. Sau đó cần kiểm tra B và bản gốc còn nguyên.")
                .setPositiveButton("Xóa A") { _, _ -> operation { bridge.delete(1001) } }
                .setNegativeButton("Hủy", null).show()
        }
        text("Checklist: GỐC/A/B có ID khác nhau; lưu nhãn riêng; mở lại sau khi đóng và khởi động lại máy; xóa A không đổi B/GỐC. Đây chưa phải thử nghiệm tương thích mạng xã hội.")
        val scroll = ScrollView(this).apply { addView(layout) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    private fun operation(task: () -> String) {
        if (!busy.compareAndSet(false, true)) return
        buttons.forEach { it.isEnabled = false }
        output.text = "Đang thực hiện; chưa có kết quả."
        executor.execute {
            val result = try { task() } catch (error: Exception) {
                "Thao tác chưa đạt: ${error.javaClass.simpleName}. ${error.message.orEmpty().take(250)}"
            } catch (error: LinkageError) {
                "Không nạp được thành phần SDK: ${error.javaClass.simpleName}. Chưa đạt."
            }
            lastResult = result
            busy.set(false)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    output.text = result
                    recreate()
                }
            }
        }
    }

    private fun changeEnabledAndClose(enabled: Boolean) {
        if (busy.get()) return
        val saved = getSharedPreferences("probe_control", MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled).putBoolean("initialization_pending", false).commit()
        if (!saved) {
            output.text = "Không lưu được lựa chọn; chưa thay đổi SDK."
            return
        }
        finishAndRemoveTask()
        // End only this laboratory app's UID processes; the separately installed original stays intact.
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?.filter { it.uid == Process.myUid() && it.pid != Process.myPid() }
            ?.forEach { Process.killProcess(it.pid) }
        Process.killProcess(Process.myPid())
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        private val busy = AtomicBoolean(false)
        @Volatile private var lastResult = "Chưa thực hiện kiểm tra engine trên máy này."
    }
}
