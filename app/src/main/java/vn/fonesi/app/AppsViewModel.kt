package vn.fonesi.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.fonesi.app.storage.CloneMetadataRow
import vn.fonesi.app.storage.LocalServices
import vn.fonesi.core.update.UpdateTask

data class AppsState(
    val apps: List<DeviceApp> = emptyList(),
    val loading: Boolean = false,
    val scanned: Boolean = false,
    val unreadableCount: Int = 0,
    val error: String? = null,
)

data class ManagementState(
    val clones: List<CloneMetadataRow> = emptyList(),
    val jobs: List<UpdateTask> = emptyList(),
    val paused: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val feedback: String? = null,
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = InstalledApps(application)
    private val services = LocalServices.get(application)
    val engine = services.engine
    private val mutableState = MutableStateFlow(AppsState())
    val state = mutableState.asStateFlow()
    private var scanJob: Job? = null
    private val mutableManagement = MutableStateFlow(ManagementState())
    val management = mutableManagement.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(services.database.clones().observe(), services.tasks.observe(), services.tasks.observePaused()) { clones, jobs, paused ->
                    ManagementState(clones, jobs, paused, loaded = true)
                }.collect { updated ->
                    mutableManagement.value = updated.copy(feedback = mutableManagement.value.feedback)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableManagement.value = mutableManagement.value.copy(error = "Không đọc được dữ liệu quản lý. Cần kiểm tra lỗi trước khi thực hiện cập nhật; app không tự xóa cơ sở dữ liệu để bỏ qua lỗi.")
            }
        }
    }

    fun setQueuePaused(paused: Boolean) = mutate {
        services.tasks.setPaused(paused)
        if (paused) "Đã lưu trạng thái tạm dừng." else "Đã cho phép hàng đợi. Chưa có engine nên chưa chạy cập nhật."
    }

    fun cancelTask(id: String) = mutate {
        check(services.queue.cancel(id))
        "Đã hủy yêu cầu chưa thực thi."
    }

    fun retryDeferred(id: String) = mutate {
        check(services.queue.retryDeferred(id))
        "Đã đưa yêu cầu về trạng thái chờ kiểm tra lại."
    }

    fun renameClone(engineKey: String, id: String, label: String, group: String?) = mutate {
        services.metadata(engineKey).rename(id, label, group)
        "Đã lưu tên và nhóm."
    }

    private fun mutate(action: () -> String) {
        viewModelScope.launch {
            try {
                val feedback = withContext(Dispatchers.IO) { action() }
                mutableManagement.value = mutableManagement.value.copy(feedback = feedback)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableManagement.value = mutableManagement.value.copy(feedback = "Thao tác chưa được xác nhận. Không xóa hoặc tạo lại dữ liệu để xử lý lỗi.")
            }
        }
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { catalog.scan() }
                mutableState.value = AppsState(result.apps, false, true, result.unreadableCount)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = "Chưa đọc được danh sách ứng dụng. Hãy thử quét lại.",
                )
            }
        }
    }
}
