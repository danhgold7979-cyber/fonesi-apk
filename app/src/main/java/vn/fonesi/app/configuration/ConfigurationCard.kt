package vn.fonesi.app.configuration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.fonesi.app.storage.CloneMetadataRow

@Composable internal fun ConfigurationCard(
    model: ConfigurationViewModel, targets: List<CloneMetadataRow>, engineReady: Boolean, enabled: Boolean,
    export: () -> Unit, open: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sao lưu tên và nhóm", style = MaterialTheme.typography.titleLarge)
        Text("Tệp có tên/nhóm bạn đặt nên có thể chứa thông tin riêng. Không chứa dữ liệu đăng nhập, APK hay lựa chọn khóa; không khôi phục tài khoản hoặc tạo bản clone.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = export, enabled = enabled && !state.busy) { Text("Chọn nơi xuất") }
            OutlinedButton(onClick = open, enabled = enabled && !state.busy) { Text("Chọn tệp nhập") }
        }
        state.feedback?.let { Text(it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.pending != null) {
            Text(if (state.pending == DocumentAction.EXPORT) "Chỉ ghi tệp sau khi bạn xác nhận bên dưới. Bỏ lựa chọn có thể để lại tệp rỗng ở nơi vừa tạo."
                else "Chỉ đọc để xem trước; chưa thay đổi tên/nhóm.")
            OutlinedButton(onClick = model::processPending, enabled = enabled && !state.busy) {
                Text(if (state.pending == DocumentAction.EXPORT) "Xác nhận xuất và đọc lại" else "Đọc tệp để xem trước")
            }
            TextButton(onClick = model::discard, enabled = !state.busy) { Text("Bỏ lựa chọn") }
        }
        state.archive?.let { archive ->
            ConfigurationPreview(archive, targets, enabled && !state.busy && engineReady, model::applyMappings)
            if (!engineReady) Text("Cần engine sẵn sàng và bản clone thật để đối chiếu trước khi áp dụng.")
        }
    }
}

@Composable private fun ConfigurationPreview(
    archive: ConfigurationArchive, targets: List<CloneMetadataRow>, enabled: Boolean, apply: (Map<Int, String>) -> Unit,
) {
    val mappings = remember(archive) { mutableStateMapOf<Int, String>() }
    var page by remember(archive) { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    val start = page * 20
    val end = minOf(start + 20, archive.entries.size)
    val selected = mappings.filterValues { token -> targets.any { it.launchToken == token } }
    Text("Đã chọn ${selected.size}/${archive.entries.size} mục; các mục chưa chọn sẽ được bỏ qua.")
    Text("Chọn rõ bản nhận. Nếu các bản cùng app trùng tên, hãy đổi tên trong danh sách Bản clone trước để phân biệt.")
    archive.entries.subList(start, end).forEach { entry ->
        var expanded by remember(entry.reference) { mutableStateOf(false) }
        val samePackage = targets.filter { it.packageName == entry.packageName }
        val candidates = samePackage.filter { candidate -> samePackage.count { it.label == candidate.label } == 1 }
        val chosen = targets.singleOrNull { it.launchToken == mappings[entry.reference] }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${entry.reference}. ${entry.label}", style = MaterialTheme.typography.titleSmall)
                Text(entry.packageName)
                entry.group?.let { Text("Nhóm nhập: $it") }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(chosen?.label ?: "Chọn bản nhận / bỏ qua") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Bỏ qua mục này") }, onClick = { mappings.remove(entry.reference); expanded = false })
                        candidates.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate.label + candidate.groupName?.let { " · $it" }.orEmpty()) },
                                onClick = { mappings[entry.reference] = candidate.launchToken; expanded = false })
                        }
                    }
                }
                if (candidates.isEmpty()) Text("Chưa có bản nhận cùng app với tên phân biệt được.")
            }
        }
    }
    if (archive.entries.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { page-- }, enabled = page > 0) { Text("Trước") }
            Text("${start + 1}–$end/${archive.entries.size}")
            TextButton(onClick = { page++ }, enabled = end < archive.entries.size) { Text("Sau") }
        }
    }
    val unique = selected.values.distinct().size == selected.size
    if (!unique) Text("Hai mục đang chọn cùng một bản nhận. Hãy sửa lựa chọn trước khi áp dụng.")
    Button(onClick = { confirming = true }, enabled = enabled && selected.isNotEmpty() && unique) { Text("Xem xác nhận áp dụng") }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Áp dụng tên và nhóm?") },
        text = { Text("Đổi tên/nhóm của ${selected.size} bản đã chọn; bỏ qua ${archive.entries.size - selected.size} mục còn lại. Đối chiếu engine trước khi lưu; không tạo hoặc sao chép tài khoản.") },
        confirmButton = { TextButton(onClick = { confirming = false; apply(selected.toMap()) }) { Text("Áp dụng các mục đã chọn") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Hủy") } })
}
