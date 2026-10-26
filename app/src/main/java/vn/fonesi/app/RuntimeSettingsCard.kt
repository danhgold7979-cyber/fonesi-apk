package vn.fonesi.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.fonesi.app.runtime.RuntimeBindings

@Composable internal fun RuntimeSettingsCard(change: (Boolean) -> Unit, enabled: Boolean) {
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        feedback = if (grants.values.all { it }) "Đã nhận kết quả cấp quyền. Từng ứng dụng khách còn cần kiểm chứng sử dụng quyền."
        else "Có quyền chưa được cấp; chức năng liên quan có thể không hoạt động."
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Thiết lập", style = MaterialTheme.typography.titleLarge)
        Text(RuntimeBindings.status())
        if (RuntimeBindings.candidate) {
            Text("SDK ứng viên: ARM64, Android/API 26–34, trang 4 KB, một APK đầy đủ. Đây là phạm vi cho phép thử, chưa phải chứng nhận tương thích.")
            Text("Lối tắt phụ thuộc Fonesi. Gỡ Fonesi có thể làm mất toàn bộ vùng tài khoản; không có sao lưu phiên đăng nhập.")
            Button(onClick = { confirm = true }, enabled = enabled) { Text("Bật engine và đóng Fonesi") }
            OutlinedButton(onClick = { confirm = false }, enabled = enabled) { Text("Tắt engine và đóng Fonesi") }
            OutlinedButton(onClick = {
                permissionRequest.launch(buildList {
                    add(Manifest.permission.CAMERA); add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray())
            }) { Text("Quyền camera, mic và thông báo") }
            Text("Quyền được Android cấp cho Fonesi; chưa chứng nhận SDK tách quyền theo từng bản clone. Không yêu cầu root.")
        } else Text("Bản cloud không chứa SDK. Cần build candidate riêng để thử engine; không có nút giả lập tài khoản.")
        feedback?.let { Text(it) }
    }
    confirm?.let { enabled ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (enabled) "Bật engine thử nghiệm?" else "Tắt engine?") },
            text = { Text("Fonesi và các tiến trình thuộc ứng dụng sẽ đóng. Mở lại Fonesi để áp dụng. SDK mã đóng chưa được nghiệm thu; hãy dùng dữ liệu thử trước tài khoản thật.") },
            confirmButton = { TextButton(onClick = { confirm = null; change(enabled) }) { Text("Lưu và đóng") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Hủy") } })
    }
}
