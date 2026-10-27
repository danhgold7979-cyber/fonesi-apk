package vn.fonesi.app.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

@Composable internal fun AutomaticUpdatesCard(
    state: AutoUpdateState, policy: AutoUpdatePolicy, enabled: Boolean,
    choose: (AutoUpdateMode) -> Unit, check: () -> Unit, preview: () -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tự kiểm tra/cập nhật khi mở Fonesi", style = MaterialTheme.typography.titleLarge)
        Text("Chỉ khi Fonesi đang hiển thị và đã mở khóa: kiểm tra lúc mở và mỗi phút. Không có worker tự khởi engine khi app đã đóng.")
        Text("Lựa chọn đã lưu: ${policy.mode.label()}")
        if (policy.unreadable) Text("Lựa chọn hỏng/không đọc được nên đang tắt; chọn lại để lưu.")
        TextButton(onClick = { choose(AutoUpdateMode.OFF) }) { Text("Tắt tự động") }
        OutlinedButton(onClick = { choose(AutoUpdateMode.CHECK_ON_OPEN) }, enabled = enabled) { Text("Chỉ kiểm tra và báo") }
        OutlinedButton(onClick = { consent = true }, enabled = enabled) { Text("Cho phép tự áp dụng khi mở") }
        if (state.running) LinearProgressIndicator()
        state.summary?.let { Text(it) }
        OutlinedButton(onClick = check, enabled = enabled && !state.running) { Text("Kiểm tra ngay theo lựa chọn") }
        TextButton(onClick = preview, enabled = !state.running && state.preview != null) { Text("Xem preview gần nhất") }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Tự áp dụng theo app gốc?") },
        text = { Text("Áp dụng cho các bản hiện tại và tạo thêm sau này thuộc engine đang dùng. Nguồn là app gốc đã cài; engine dùng chung mã sẽ ảnh hưởng toàn nhóm kể cả bản không chọn. Chỉ áp dụng khi Fonesi hiển thị/đã mở khóa; đang chạy hoặc chưa rõ kết quả sẽ bị hoãn. Pause vẫn được giữ. Không cam kết giữ phiên hoặc tương thích cho tới khi thử máy thật.") },
        confirmButton = { TextButton(onClick = { consent = false; choose(AutoUpdateMode.APPLY_WHILE_OPEN) }) { Text("Lưu và cho phép") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Hủy") } })
}
private fun AutoUpdateMode.label() = when (this) {
    AutoUpdateMode.OFF -> "Tắt"
    AutoUpdateMode.CHECK_ON_OPEN -> "Chỉ kiểm tra khi mở"
    AutoUpdateMode.APPLY_WHILE_OPEN -> "Tự áp dụng khi đang mở/đã mở khóa"
}
