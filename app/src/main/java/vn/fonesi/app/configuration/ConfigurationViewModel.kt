package vn.fonesi.app.configuration

import android.app.Application
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.fonesi.app.security.AccessBroker
import vn.fonesi.app.storage.LocalServices

enum class DocumentAction { EXPORT, IMPORT }
data class ConfigurationState(val pending: DocumentAction? = null, val busy: Boolean = false,
    val archive: ConfigurationArchive? = null, val feedback: String? = null)
private data class ConfigurationOutcome(val state: ConfigurationState, val clearPending: Boolean = false)
private class ExportVerificationException : IllegalStateException()

/** SAF callbacks only stage a URI. User authentication/confirmation is required before any IO. */
class ConfigurationViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val services by lazy { LocalServices.get(app) }
    private val broker by lazy { AccessBroker.get(app) }
    private val mutable = MutableStateFlow(ConfigurationState(pending = action()))
    val state = mutable.asStateFlow()
    private fun action(): DocumentAction? = saved.get<String>("document_action")?.let { runCatching { DocumentAction.valueOf(it) }.getOrNull() }
    private fun repository() = ConfigurationRepository(services.database, services.engineKey, services.engine,
        services.operationLock(), broker::hasAccess)

    fun documentSelected(action: DocumentAction, uri: Uri?) {
        if (uri == null) return
        if (mutable.value.busy) {
            mutable.value = mutable.value.copy(feedback = "Đang xử lý cấu hình đã xác nhận. Hãy đợi hoàn tất rồi chọn tệp khác.")
            return
        }
        val app = getApplication<Application>()
        if (uri.scheme != "content" || uri.authority?.startsWith(app.packageName + ".") == true || uri.authority == app.packageName) {
            mutable.value = mutable.value.copy(feedback = "Cần chọn tệp bằng trình chọn tài liệu Android.")
            return
        }
        saved["document_uri"] = uri.toString(); saved["document_action"] = action.name
        mutable.value = ConfigurationState(pending = action, feedback = "Đã chọn tệp. Sau khi mở khóa Fonesi, xác nhận để đọc hoặc ghi cấu hình.")
    }
    fun discard() {
        if (mutable.value.busy) return
        saved.remove<String>("document_uri"); saved.remove<String>("document_action")
        mutable.value = ConfigurationState(feedback = "Đã bỏ lựa chọn trong Fonesi. Không xóa tệp ở nơi bạn đã chọn.")
    }
    fun processPending() {
        val action = action() ?: return
        val uri = saved.get<String>("document_uri")?.let(Uri::parse) ?: return
        operate {
        check(broker.hasAccess())
        val app = getApplication<Application>()
        check(uri.scheme == "content" && DocumentsContract.isDocumentUri(app, uri))
        val provider = checkNotNull(app.packageManager.resolveContentProvider(checkNotNull(uri.authority), 0))
        check(provider.applicationInfo.uid != Process.myUid() && provider.packageName != app.packageName)
        if (action == DocumentAction.IMPORT) {
            val archive = checkNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use(ConfigurationCodec::read)
            check(broker.hasAccess())
            ConfigurationOutcome(ConfigurationState(pending = action, archive = archive,
                feedback = "Đã đọc ${archive.entries.size} mục. Chọn rõ bản nhận từng tên/nhóm trước khi áp dụng."))
        } else {
            val bytes = repository().export()
            check(broker.hasAccess())
            checkNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")).use { output ->
                output.write(bytes); output.flush()
            }
            try {
                val observed = checkNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use(ConfigurationCodec::read)
                check(observed == ConfigurationCodec.decode(bytes))
            } catch (_: Exception) { throw ExportVerificationException() }
            ConfigurationOutcome(ConfigurationState(feedback = "Đã ghi và đọc lại tệp tên/nhóm. Không có dữ liệu đăng nhập hoặc lựa chọn khóa trong tệp."), clearPending = true)
        }
        }
    }
    fun applyMappings(mappings: Map<Int, String>) {
        val archive = mutable.value.archive ?: return
        val selected = mappings.toMap()
        operate {
            val count = repository().apply(archive, selected)
            val synced = runCatching { services.shortcuts.synchronizePinned() }.isSuccess
            ConfigurationOutcome(ConfigurationState(feedback = "Đã lưu tên/nhóm cho $count bản đã chọn. " +
                if (synced) "Đã gửi đồng bộ shortcut." else "Launcher chưa nhận tên mới; quét lại để đồng bộ."), clearPending = true)
        }
    }
    private fun operate(action: () -> ConfigurationOutcome) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) { action() }
                if (outcome.clearPending) { saved.remove<String>("document_uri"); saved.remove<String>("document_action") }
                mutable.value = outcome.state
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ExportVerificationException) {
                mutable.value = mutable.value.copy(feedback = "Đã gửi ghi tệp nhưng chưa xác minh đọc lại được. Hãy kiểm tra nơi lưu hoặc chọn nơi khác; cấu hình gốc trong Fonesi vẫn giữ nguyên.")
            }
            catch (_: Exception) {
                mutable.value = mutable.value.copy(feedback = "Chưa hoàn tất thao tác cấu hình. Kiểm tra định dạng/tệp, quyền truy cập, engine và bản nhận rồi thử lại. Fonesi không xóa hay tạo lại guest để xử lý lỗi; tệp xuất có thể chưa đầy đủ nếu nơi lưu gặp lỗi.")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
