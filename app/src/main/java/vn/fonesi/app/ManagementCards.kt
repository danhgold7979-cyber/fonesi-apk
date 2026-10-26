package vn.fonesi.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.fonesi.app.storage.CloneMetadataRow
import vn.fonesi.core.update.*

@Composable
internal fun SavedCloneCard(
    row: CloneMetadataRow, enabled: Boolean, open: () -> Unit, pin: () -> Unit,
    save: (String, String?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.label, style = MaterialTheme.typography.titleMedium)
            Text(row.packageName, style = MaterialTheme.typography.bodySmall)
            row.groupName?.let { Text("Nhóm: $it") }
            Text("Chưa đối chiếu trạng thái chạy", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = open, enabled = enabled) { Text("Mở") }
                OutlinedButton(onClick = pin, enabled = enabled) { Text("Đưa ra màn hình") }
            }
            TextButton(onClick = { editing = true }, enabled = enabled) { Text("Đổi tên / nhóm") }
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
