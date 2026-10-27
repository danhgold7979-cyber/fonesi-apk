package vn.fonesi.app

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable internal fun DeviceDiagnosticsCard(info: DeviceDiagnostics?, refresh: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long?) = bytes?.let { Formatter.formatFileSize(context, it) } ?: "Chưa đọc được"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Thiết bị và dung lượng", style = MaterialTheme.typography.titleLarge)
        if (info == null) Text("Chưa đọc được thông tin hệ thống.") else {
            Text("${info.model} · API ${info.api} · ${info.abis.joinToString()}")
            Text("Kích thước trang: ${info.pageSize?.let { "$it byte" } ?: "Chưa đọc được"}")
            Text("Trống trên phân vùng Fonesi: ${size(info.freeBytes)}")
            Text("APK Fonesi: ${size(info.appBytes)} · dữ liệu: ${size(info.dataBytes)}")
            Text("Cache: ${size(info.cacheBytes)}; Android tính cache trong phần dữ liệu, không cộng hai lần.")
            Text("Số liệu cho toàn ứng dụng Fonesi và kho nó sở hữu, chưa tách chính xác theo từng bản clone.")
            Text(when (info.ignoresBatteryOptimization) {
                true -> "Android báo Fonesi được miễn tối ưu pin."
                false -> "Android đang áp dụng tối ưu pin; công việc nền/thông báo có thể bị trì hoãn."
                null -> "Chưa đọc được lựa chọn tối ưu pin."
            })
        }
        OutlinedButton(onClick = refresh) { Text("Đọc lại số liệu") }
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Cài đặt quyền và pin của Fonesi") }
    }
}
