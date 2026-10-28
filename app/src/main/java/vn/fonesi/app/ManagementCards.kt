package vn.fonesi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.fonesi.app.storage.CloneMetadataRow
import vn.fonesi.app.storage.CloneStylePolicy
import vn.fonesi.core.update.*
import vn.fonesi.core.CloneRecord

@Composable
internal fun SavedCloneCard(
    row: CloneMetadataRow, enabled: Boolean, operational: Boolean, selected: Boolean, toggle: () -> Unit,
    live: CloneRecord?, open: () -> Unit, pin: () -> Unit,
    stop: () -> Unit, delete: () -> Unit, duplicate: (String) -> Unit, update: () -> Unit,
    saveStyle: (String, String?) -> Unit,
    save: (String, String?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var duplicating by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = selected, onCheckedChange = { toggle() }, enabled = enabled && operational && live?.packageName == row.packageName)
                Box(Modifier.size(30.dp).background(cloneAccentColor(row.accentKey), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(row.badgeText ?: "F", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Text(row.label, style = MaterialTheme.typography.titleMedium)
            }
            Text(row.packageName, style = MaterialTheme.typography.bodySmall)
            row.groupName?.let { Text("Nhóm: $it") }
            Text(when { live == null -> "Chưa đọc được bản cài từ engine"; live.running == true -> "Engine báo đang chạy · phiên bản ${live.versionCode}"
                live.running == null -> "Chưa rõ kết quả mở; cần dừng/đối chiếu trước khi cập nhật"; else -> "Engine báo đang dừng · phiên bản ${live.versionCode}" }, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = open, enabled = enabled && operational) { Text("Mở") }
                OutlinedButton(onClick = pin, enabled = enabled && operational) { Text("Đưa ra màn hình") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = stop, enabled = enabled && operational) { Text("Dừng") }
                TextButton(onClick = update, enabled = enabled && operational) { Text("Cập nhật") }
                TextButton(onClick = { deleting = true }, enabled = enabled && operational) { Text("Xóa") }
            }
            TextButton(onClick = { duplicating = true }, enabled = enabled && operational) { Text("Tạo thêm bản sạch") }
            TextButton(onClick = { editing = true }, enabled = enabled) { Text("Đổi tên / nhóm") }
            TextButton(onClick = { styling = true }, enabled = enabled) { Text("Màu / huy hiệu") }
        }
    }
    if (editing) {
        var label by remember(row.label) { mutableStateOf(row.label) }
        var group by remember(row.groupName) { mutableStateOf(row.groupName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false }, title = { Text("Tên và nhóm bản clone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Tên, tối đa 80 ký tự") }, singleLine = true)
                    OutlinedTextField(group, { group = it }, label = { Text("Nhóm, tối đa 40 ký tự") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { save(label, group); editing = false },
                    enabled = label.trim().length in 1..80 && group.trim().length <= 40) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Hủy") } },
        )
    }
    if (styling) {
        var accent by remember(row.accentKey) { mutableStateOf(row.accentKey) }
        var badge by remember(row.badgeText) { mutableStateOf(row.badgeText.orEmpty()) }
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { styling = false },
            title = { Text("Màu và huy hiệu bản clone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text("Màu biểu tượng: " + cloneAccentLabel(accent))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            CloneStylePolicy.accentKeys.forEach { key ->
                                DropdownMenuItem(text = { Text(cloneAccentLabel(key)) },
                                    onClick = { accent = key; expanded = false })
                            }
                        }
                    }
                    OutlinedTextField(badge, { badge = it },
                        label = { Text("Huy hiệu (tối đa 2 ký tự Unicode)") },
                        singleLine = true, maxLines = 1)
                    Text("Để trống để dùng chữ F. Chỉ đổi biểu tượng quản lý, không thay APK hay tài khoản.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { saveStyle(accent, badge); styling = false },
                    enabled = runCatching { CloneStylePolicy.normalize(accent, badge) }.isSuccess) {
                    Text("Lưu màu / huy hiệu")
                }
            },
            dismissButton = { TextButton(onClick = { styling = false }) { Text("Hủy") } },
        )
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Xóa ${row.label}?") },
        text = { Text("Yêu cầu engine xóa bản này và dữ liệu đăng nhập của nó. Thao tác không thể hoàn tác; cần dừng bản đang chạy trước.") },
        confirmButton = { TextButton(onClick = { deleting = false; delete() }) { Text("Xóa bản này") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Hủy") } })
    if (duplicating) {
        var name by remember { mutableStateOf("${row.label.take(60)} mới") }
        AlertDialog(onDismissRequest = { duplicating = false }, title = { Text("Thêm bản sạch") },
            text = { Column { Text("Lấy bộ cài từ app gốc; không sao chép dữ liệu hay phiên đăng nhập.")
                OutlinedTextField(name, { name = it }, label = { Text("Tên bản mới") }, singleLine = true) } },
            confirmButton = { TextButton(onClick = { duplicating = false; duplicate(name) }, enabled = name.trim().length in 1..80) { Text("Tạo") } },
            dismissButton = { TextButton(onClick = { duplicating = false }) { Text("Hủy") } })
    }
}

@Composable
internal fun UpdateTaskCard(task: UpdateTask, enabled: Boolean, cancel: () -> Unit, retry: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.packageName, style = MaterialTheme.typography.titleMedium)
            Text("Phiên bản yêu cầu: ${task.versionCode}")
            Text("Trạng thái đã lưu: ${task.state.label()}")
            task.issue?.let { Text(it.description(), style = MaterialTheme.typography.bodySmall) }
            if (task.completion == CompletionKind.ALREADY_CURRENT) Text("Đã đúng phiên bản; không cần áp dụng lại.")
            if (task.state == TaskState.REVIEW_REQUIRED) {
                Text("Không tự chạy lại. Cần đối chiếu kết quả với bộ máy clone.", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.state == TaskState.DEFERRED) TextButton(onClick = retry, enabled = enabled) { Text("Kiểm tra lại") }
                if (task.state in setOf(TaskState.QUEUED, TaskState.DEFERRED)) {
                    TextButton(onClick = cancel, enabled = enabled) { Text("Hủy yêu cầu") }
                }
            }
        }
    }
}

private fun TaskState.label() = when (this) {
    TaskState.QUEUED -> "Chờ xử lý"
    TaskState.PREPARING -> "Chuẩn bị nguồn"
    TaskState.APPLYING -> "Áp dụng; cần xác minh kết quả"
    TaskState.VERIFYING -> "Đối chiếu phiên bản"
    TaskState.DEFERRED -> "Tạm hoãn"
    TaskState.REVIEW_REQUIRED -> "Cần đối chiếu"
    TaskState.SUCCEEDED -> "Đã xác minh phiên bản"
    TaskState.FAILED -> "Chưa hoàn thành"
    TaskState.CANCELLED -> "Đã hủy trước khi thực thi"
}

private fun TaskIssue.description() = when (this) {
    TaskIssue.EXECUTION_POLICY -> "Đợi Fonesi đang mở/đã mở khóa và chế độ cho phép thực thi."
    TaskIssue.SUPERSEDED_SOURCE -> "Yêu cầu nguồn cũ đã được thay thế trước khi thực thi."
    TaskIssue.PAUSED -> "Hàng đợi đã được tạm dừng."
    TaskIssue.ENGINE_CHANGED -> "Bộ máy clone đã thay đổi; cần kiểm tra lại mã bản clone."
    TaskIssue.ENGINE_UNAVAILABLE -> "Chưa có bộ máy clone sẵn sàng."
    TaskIssue.SOURCE_MISSING -> "Không tìm thấy ứng dụng gốc."
    TaskIssue.SOURCE_CHANGED -> "Ứng dụng gốc đã thay đổi; cần tạo yêu cầu với phiên bản mới."
    TaskIssue.SOURCE_PREPARATION_FAILED -> "Chưa chuẩn bị được bộ cài; chưa xác nhận cập nhật."
    TaskIssue.UNSAFE_TARGET -> "Danh sách bản clone, chữ ký hoặc phiên bản cần được kiểm tra lại."
    TaskIssue.CLONE_BUSY -> "Bản liên quan đang chạy hoặc chưa xác định được trạng thái."
    TaskIssue.SCOPE_CHANGED -> "Phạm vi cập nhật đã thay đổi."
    TaskIssue.INTERRUPTED, TaskIssue.ENGINE_OUTCOME_UNKNOWN, TaskIssue.VERSION_NOT_VERIFIED ->
        "Kết quả cập nhật chưa rõ; không tự áp dụng lại."
}

private fun cloneAccentColor(key: String): Color = when (key) {
    "teal" -> Color(0xFF126B55)
    "blue" -> Color(0xFF265BB8)
    "purple" -> Color(0xFF723DB3)
    "amber" -> Color(0xFFA56409)
    "red" -> Color(0xFFB83641)
    "slate" -> Color(0xFF556277)
    else -> Color(0xFF126B55)
}

private fun cloneAccentLabel(key: String): String = when (key) {
    "teal" -> "Xanh ngọc"
    "blue" -> "Xanh dương"
    "purple" -> "Tím"
    "amber" -> "Hổ phách"
    "red" -> "Đỏ"
    "slate" -> "Xám"
    else -> "Chưa rõ"
}
