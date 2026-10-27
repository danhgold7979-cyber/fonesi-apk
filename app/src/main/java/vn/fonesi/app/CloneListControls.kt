package vn.fonesi.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.fonesi.core.CloneStatusFilter

@Composable internal fun CloneListControls(
    query: String, queryChange: (String) -> Unit, groups: List<String>, group: String?, groupChange: (String?) -> Unit,
    status: CloneStatusFilter, statusChange: (CloneStatusFilter) -> Unit,
    selected: Int, visible: Int, selectableVisible: Int, selectVisible: () -> Unit, clear: () -> Unit, update: () -> Unit, enabled: Boolean,
) {
    OutlinedTextField(query, queryChange, label = { Text("Tìm tên, nhóm hoặc app · có thể gõ không dấu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    var groupOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { groupOpen = true }) { Text(group?.takeIf { it.isNotEmpty() } ?: if (group == "") "Chưa phân nhóm" else "Mọi nhóm") }
            DropdownMenu(expanded = groupOpen, onDismissRequest = { groupOpen = false }) {
                DropdownMenuItem(text = { Text("Mọi nhóm") }, onClick = { groupChange(null); groupOpen = false })
                DropdownMenuItem(text = { Text("Chưa phân nhóm") }, onClick = { groupChange(""); groupOpen = false })
                groups.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { groupChange(name); groupOpen = false }) }
            }
        }
        Box {
            OutlinedButton(onClick = { statusOpen = true }) { Text(status.label()) }
            DropdownMenu(expanded = statusOpen, onDismissRequest = { statusOpen = false }) {
                CloneStatusFilter.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label()) }, onClick = { statusChange(value); statusOpen = false }) }
            }
        }
    }
    Text("Đang hiển thị $visible bản, $selectableVisible bản có thể chọn; đã chọn $selected bản, gồm cả mục đang ẩn bởi bộ lọc.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = selectVisible, enabled = enabled && selectableVisible > 0) { Text("Chọn đang hiện") }
        TextButton(onClick = clear, enabled = selected > 0) { Text("Bỏ chọn hết") }
    }
    OutlinedButton(onClick = update, enabled = enabled && selected > 0) { Text("Xem cập nhật $selected bản đã chọn") }
}
private fun CloneStatusFilter.label() = when (this) {
    CloneStatusFilter.ALL -> "Mọi trạng thái"
    CloneStatusFilter.RUNNING -> "Engine báo đang chạy"
    CloneStatusFilter.STOPPED -> "Engine báo đang dừng"
    CloneStatusFilter.UNKNOWN -> "Chưa xác định"
}
