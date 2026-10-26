package vn.fonesi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val model: AppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFF9CD8BD), secondary = Color(0xFFB0CCC0),
            ) else lightColorScheme(
                primary = Color(0xFF126B55), secondary = Color(0xFF49665A),
                background = Color(0xFFF7FAF6), surface = Color(0xFFF7FAF6),
            )
            MaterialTheme(colorScheme = scheme) {
                val state by model.state.collectAsStateWithLifecycle()
                val management by model.management.collectAsStateWithLifecycle()
                FonesiScreen(state, management, model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

@Composable
private fun FonesiScreen(state: AppsState, management: ManagementState, model: AppsViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var priorityOnly by rememberSaveable { mutableStateOf(true) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleApps = remember(state.apps, query, priorityOnly) {
        state.apps.filter {
            (!priorityOnly || it.priority) &&
                (it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
        }
    }
    val selected = state.apps.firstOrNull { it.packageName == selectedPackage }
    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fonesi", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Không gian cho nhiều tài khoản", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = model::refresh, enabled = !state.loading) { Text("Quét lại") }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bản đang phát triển · chưa hỗ trợ clone", fontWeight = FontWeight.SemiBold)
                        Text("Đã có phần quản lý và lưu hàng đợi. Bộ máy clone chưa được tích hợp vào app này; chưa thể tạo tài khoản độc lập hoặc áp dụng cập nhật.")
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Ứng dụng", "Bản clone", "Cập nhật").forEachIndexed { index, title ->
                        FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) })
                    }
                }
                management.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                management.feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (tab == 0) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Tìm tên hoặc mã ứng dụng") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = priorityOnly, onClick = { priorityOnly = true }, label = { Text("Ứng dụng ưu tiên") })
                    FilterChip(selected = !priorityOnly, onClick = { priorityOnly = false }, label = { Text("Tất cả") })
                }
                Text("Mạng xã hội, mua sắm và video được ưu tiên hiển thị. Danh sách này chưa xác nhận khả năng clone.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Đang quét ứng dụng trên máy…")
                    }
                }
            }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (state.unreadableCount > 0) {
                item { Text("Có ${state.unreadableCount} ứng dụng không đọc được trong lần quét này.", style = MaterialTheme.typography.bodySmall) }
            }
            if (state.scanned && !state.loading && visibleApps.isEmpty()) {
                item {
                    Text(if (query.isNotBlank()) "Không có ứng dụng khớp tìm kiếm."
                    else if (priorityOnly) "Chưa thấy ứng dụng ưu tiên. Chọn Tất cả để xem các ứng dụng Android cho phép đọc."
                    else "Chưa thấy ứng dụng có thể mở trong hồ sơ Android hiện tại.")
                }
            }
            items(visibleApps, key = { "app:${it.packageName}" }) { app ->
                AppCard(app) { selectedPackage = app.packageName }
            }
            } else if (tab == 1) {
                item {
                    Text("Bản clone đã lưu", style = MaterialTheme.typography.titleLarge)
                    Text("Tên và nhóm được lưu riêng. Trạng thái chạy cần bộ máy clone đối chiếu; một mục đã lưu chưa có nghĩa bản đó đang hoạt động.")
                    if (!management.loaded && management.error == null) Text("Đang đọc dữ liệu quản lý…")
                    else if (management.clones.isEmpty()) Text("Chưa có bản clone được xác nhận và lưu. App không tạo bản mẫu trong danh sách này.")
                }
                items(management.clones, key = { "clone:${it.engineKey.length}:${it.engineKey}:${it.cloneId}" }) { row ->
                    SavedCloneCard(row, enabled = management.error == null,
                        open = { model.openClone(row.launchToken) }, pin = { model.pinClone(row.launchToken) }) { label, group ->
                        model.renameClone(row.engineKey, row.cloneId, label, group)
                    }
                }
            } else {
                item {
                    Text("Hàng đợi cập nhật", style = MaterialTheme.typography.titleLarge)
                    Text(if (management.paused) "Đã lưu: tạm dừng hàng đợi." else "Đã lưu: cho phép hàng đợi.")
                    Text("Chưa có bộ máy clone nên chưa thực thi cập nhật. Nút bên dưới chỉ lưu lựa chọn cho hàng đợi.")
                    OutlinedButton(onClick = { model.setQueuePaused(!management.paused) }, enabled = management.loaded && management.error == null) {
                        Text(if (management.paused) "Cho phép chạy" else "Tạm dừng")
                    }
                    if (management.loaded && management.jobs.isEmpty()) Text("Chưa có yêu cầu cập nhật. Không có tác vụ mẫu hoặc kết quả giả.")
                }
                items(management.jobs.asReversed(), key = { "task:${it.id}" }) { task ->
                    UpdateTaskCard(task, management.error == null, { model.cancelTask(task.id) }, { model.retryDeferred(task.id) })
                }
            }
            item {
                Text("Danh sách được xử lý trên máy. Bản này không có quyền truy cập Internet và không gửi danh sách ứng dụng đi.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedPackage = null },
            title = { Text(selected.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selected.packageName)
                    Text("Phiên bản gốc: ${selected.versionName} (${selected.source.versionCode})")
                    Text("Bộ cài đang có: ${selected.source.apkPaths.size} tệp APK")
                    Text("Chưa kiểm chứng khả năng clone. Khi có bộ máy phù hợp, bản clone sẽ lấy cập nhật từ ứng dụng gốc này.")
                    Text("Chức năng tạo clone hiện chưa khả dụng; không có bản sao tài khoản nào được tạo.")
                }
            },
            confirmButton = { TextButton(onClick = { selectedPackage = null }) { Text("Đóng") } },
        )
    }
}

@Composable
private fun AppCard(app: DeviceApp, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text("Bản gốc ${app.versionName}", style = MaterialTheme.typography.bodySmall)
                Text("Chưa kiểm chứng clone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text("Chi tiết", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
