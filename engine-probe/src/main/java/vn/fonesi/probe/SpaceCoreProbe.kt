package vn.fonesi.probe

import android.content.Context
import android.content.pm.PackageManager
import com.fvbox.lib.FCore

/** Real SDK calls restricted to our signed synthetic payload. Not a production CloneEngine. */
class SpaceCoreProbe(private val context: Context) {
    fun payloadInstalled(): Boolean = try {
        context.packageManager.checkSignatures(context.packageName, PAYLOAD) == PackageManager.SIGNATURE_MATCH
    } catch (_: Exception) { false }

    fun create(userId: Int): String {
        requireReady(userId)
        check(payloadInstalled()) { "Cài APK dữ liệu mẫu do cùng bản build cung cấp trước." }
        val core = FCore.get()
        if (core.isInstalled(PAYLOAD, userId)) return "Vùng $userId đã tồn tại; không tạo hoặc ghi đè dữ liệu."
        if (core.users.none { it.id == userId }) checkNotNull(core.createUser(userId))
        core.setEnableDevice(userId, false)
        val result = core.installPackageAsUser(PAYLOAD, userId)
        check(result.success) { "SDK báo cài dữ liệu mẫu thất bại." }
        check(core.isInstalled(PAYLOAD, userId)) { "SDK chưa xác nhận bản cài tồn tại." }
        val installed = core.getPackageInfo(PAYLOAD, 0, userId)
        check(installed?.packageName == PAYLOAD) { "Không đọc được thông tin bản cài từ SDK." }
        return "Đã đọc lại bản cài vùng $userId từ SDK. Cần mở và kiểm tra dữ liệu để xác minh cách ly."
    }

    fun launch(userId: Int) {
        requireReady(userId)
        check(payloadInstalled()) { "Không tìm thấy APK dữ liệu mẫu đúng chữ ký." }
        val core = FCore.get()
        check(core.isInstalled(PAYLOAD, userId)) { "Vùng này chưa có ứng dụng dữ liệu mẫu." }
        core.launchApk(PAYLOAD, userId)
    }

    fun delete(userId: Int): String {
        requireReady(userId)
        val core = FCore.get()
        check(!core.isRunning(PAYLOAD, userId)) { "Đóng vùng này trước khi xóa." }
        core.uninstallPackageAsUser(PAYLOAD, userId)
        check(!core.isInstalled(PAYLOAD, userId)) { "SDK vẫn báo bản cài tồn tại; chưa xác nhận xóa." }
        return "SDK xác nhận không còn bản cài vùng $userId. Hãy kiểm tra vùng còn lại và bản gốc."
    }

    fun stop(userId: Int): String {
        requireReady(userId)
        val core = FCore.get()
        check(core.isInstalled(PAYLOAD, userId)) { "Vùng này chưa có bản dữ liệu mẫu." }
        core.stopPackage(PAYLOAD, userId)
        check(!core.isRunning(PAYLOAD, userId)) { "SDK vẫn báo vùng đang chạy; chưa xác nhận dừng." }
        return "SDK báo vùng $userId đã dừng. Dữ liệu mẫu không bị yêu cầu xóa."
    }

    fun status(): String {
        check((context.applicationContext as ProbeApplication).runtime == ProbeRuntime.Initialized)
        val core = FCore.get()
        return USERS.joinToString("\n") { id ->
            "Vùng $id: ${if (core.isInstalled(PAYLOAD, id)) "có bản cài" else "chưa có bản cài"}"
        } + "\nĐây là trạng thái SDK báo, chưa phải kết quả kiểm tra cách ly dữ liệu."
    }

    private fun requireReady(userId: Int) {
        require(userId in USERS) { "Chỉ được thao tác vùng dữ liệu mẫu của probe." }
        check((context.applicationContext as ProbeApplication).runtime == ProbeRuntime.Initialized) {
            "SDK chưa khởi tạo được."
        }
    }

    companion object {
        const val PAYLOAD = "vn.fonesi.payload"
        val USERS = listOf(1001, 1002)
    }
}
