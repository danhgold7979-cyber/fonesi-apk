package vn.fonesi.app.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.fonesi.core.UpdateBlockReason
import vn.fonesi.core.UpdateScope

data class UpdatePreviewState(val loading: Boolean = false, val preview: UpdatePreview? = null, val error: String? = null)

@Composable internal fun UpdatePreviewDialog(state: UpdatePreviewState, enabled: Boolean, dismiss: () -> Unit, confirm: (String) -> Unit) {
    if (!state.loading && state.preview == null && state.error == null) return
    AlertDialog(onDismissRequest = dismiss, title = { Text("Xem trước cập nhật") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.loading) { CircularProgressIndicator(); Text("Đang đối chiếu engine và ứng dụng gốc…") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.preview?.let { preview ->
                if (preview.availability.updateScope == UpdateScope.SHARED_PACKAGE) Text("Engine dùng chung mã: mọi bản cùng app trong nhóm dưới đây đều bị ảnh hưởng, gồm bản bạn không chọn.")
                preview.plan.targets.forEach { target ->
                    Text("${target.source.packageName} → phiên bản ${target.source.versionCode}", style = MaterialTheme.typography.titleSmall)
                    Text("Ảnh hưởng ${target.affectedCloneIds.size} bản: " + target.affectedCloneIds.joinToString { preview.labels[it] ?: "Bản chưa có tên quản lý" })
                }
                if (preview.plan.targets.isEmpty()) Text("Không có nhóm đủ điều kiện xếp hàng.")
                if (preview.plan.alreadyCurrent.isNotEmpty()) Text("Đã đúng phiên bản: " + preview.plan.alreadyCurrent.joinToString { preview.labels[it] ?: "Bản chưa có tên" })
                preview.plan.blocked.forEach { block -> Text("${preview.labels[block.requestedCloneId] ?: "Bản đã chọn"}: ${block.reason.label()}") }
                Text("Xác nhận chỉ xếp hàng các nhóm đủ điều kiện ở trên. Thay đổi nguồn/trạng thái/phạm vi sẽ yêu cầu xem lại; chưa áp dụng cập nhật ngay.")
            }
        }
    }, confirmButton = {
        val preview = state.preview
        TextButton(onClick = { preview?.let { confirm(it.id) } },
            enabled = enabled && !state.loading && state.error == null && preview?.plan?.targets?.isNotEmpty() == true) { Text("Xác nhận xếp hàng") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Đóng") } })
}

private fun UpdateBlockReason.label() = when (this) {
    UpdateBlockReason.ENGINE_UNAVAILABLE -> "Engine chưa sẵn sàng."
    UpdateBlockReason.UNKNOWN_CLONE -> "Không đọc được bản cài tương ứng từ engine."
    UpdateBlockReason.SOURCE_MISSING -> "Không đọc được ứng dụng gốc đã cài."
    UpdateBlockReason.SOURCE_INVALID -> "Bộ cài/phiên bản/chữ ký nguồn chưa hợp lệ."
    UpdateBlockReason.SIGNER_REVIEW_REQUIRED -> "Chữ ký khác; cần đối chiếu."
    UpdateBlockReason.NEWER_CLONE_VERSION -> "Bản clone có phiên bản mới hơn nguồn; không hạ phiên bản."
    UpdateBlockReason.IN_USE_OR_UNKNOWN -> "Có bản liên quan đang chạy hoặc chưa rõ trạng thái."
    UpdateBlockReason.SOURCE_UNSUPPORTED -> "Engine hiện tại chưa nhập được bộ split APK."
    UpdateBlockReason.INCOMPLETE_GROUP -> "Chưa đọc lại đầy đủ các bản liên quan trong nhóm; cần đối chiếu trước khi cập nhật."
}
