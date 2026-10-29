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
import vn.fonesi.app.storage.CloneStopIdentityGuard
import vn.fonesi.app.storage.LocalServices
import vn.fonesi.core.update.UpdateTask
import vn.fonesi.app.shortcuts.LaunchResult
import vn.fonesi.app.shortcuts.PinResult
import vn.fonesi.app.shortcuts.message
import vn.fonesi.app.runtime.RuntimeBindings
import vn.fonesi.core.*
import vn.fonesi.core.update.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import vn.fonesi.app.update.UpdatePreviewChangedException
import vn.fonesi.app.update.UpdatePreviewState
import vn.fonesi.app.update.AutoUpdateMode
import vn.fonesi.app.update.AutoUpdatePolicy

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
    val busy: Boolean = false,
)

data class RuntimeState(
    val ready: Boolean = false,
    val description: String = RuntimeBindings.status(),
    val clones: Map<String, CloneRecord> = emptyMap(),
    val capabilities: EngineCapabilities = EngineCapabilities(),
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = InstalledApps(application)
    private val services = LocalServices.get(application)
    val engine = services.engine
    val engineKey get() = services.engineKey
    private val mutableState = MutableStateFlow(AppsState())
    val state = mutableState.asStateFlow()
    private var scanJob: Job? = null
    private val mutableManagement = MutableStateFlow(ManagementState())
    val management = mutableManagement.asStateFlow()
    private val mutableRuntime = MutableStateFlow(RuntimeState())
    val runtime = mutableRuntime.asStateFlow()
    private val mutablePreview = MutableStateFlow(UpdatePreviewState())
    val updatePreview = mutablePreview.asStateFlow()
    val automatic = services.automatic.state
    private val mutableAutoPolicy = MutableStateFlow(services.autoPolicy.read())
    val autoPolicy = mutableAutoPolicy.asStateFlow()
    private var automaticJob: Job? = null
    fun automaticTick() {
        if (automaticJob?.isActive == true || mutableManagement.value.busy) return
        val policy = services.autoPolicy.read()
        mutableAutoPolicy.value = policy
        if (policy.mode == AutoUpdateMode.OFF) return
        automaticJob = viewModelScope.launch {
            try { withContext(Dispatchers.IO) { services.automatic.check() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableManagement.value = mutableManagement.value.copy(feedback = "Chưa chạy được tự kiểm tra. Giữ nguyên hàng đợi để kiểm tra lại.") }
            finally { mutableAutoPolicy.value = services.autoPolicy.read(); refreshRuntime() }
        }
    }
    fun setAutoMode(mode: AutoUpdateMode) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { services.autoPolicy.write(mode); if (mode == AutoUpdateMode.OFF) services.automatic.check() }
                mutableAutoPolicy.value = services.autoPolicy.read()
                mutableManagement.value = mutableManagement.value.copy(feedback = "Đã lưu lựa chọn tự động; thao tác đang áp dụng vẫn phải hoàn tất xác minh.")
                if (mode != AutoUpdateMode.OFF) automaticTick()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableManagement.value = mutableManagement.value.copy(feedback = "Chưa lưu được lựa chọn tự động; không xác nhận đã đổi chế độ.") }
        }
    }
    fun showAutomaticPreview() {
        services.automatic.state.value.preview?.let { mutablePreview.value = UpdatePreviewState(preview = it) }
    }
    private var previewJob: Job? = null
    private val mutableDiagnostics = MutableStateFlow<DeviceDiagnostics?>(null)
    val diagnostics = mutableDiagnostics.asStateFlow()
    private var diagnosticsJob: Job? = null
    fun refreshDiagnostics() {
        diagnosticsJob?.cancel()
        diagnosticsJob = viewModelScope.launch {
            mutableDiagnostics.value = withContext(Dispatchers.IO) { DeviceDiagnostics.read(getApplication()) }
        }
    }

    init {
        viewModelScope.launch {
            try {
                combine(services.database.clones().observe(), services.tasks.observe(), services.tasks.observePaused()) { clones, jobs, paused ->
                    ManagementState(clones, jobs, paused, loaded = true)
                }.collect { updated ->
                    mutableManagement.value = updated.copy(feedback = mutableManagement.value.feedback, busy = mutableManagement.value.busy)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableManagement.value = mutableManagement.value.copy(error = "Không đọc được dữ liệu quản lý. Cần kiểm tra lỗi trước khi thực hiện cập nhật; app không tự xóa cơ sở dữ liệu để bỏ qua lỗi.")
            }
        }
    }

    fun setQueuePaused(paused: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { services.tasks.setPaused(paused) }
                mutableManagement.value = mutableManagement.value.copy(feedback = if (paused)
                    "Đã tạm dừng ở ranh giới an toàn; thao tác đang áp dụng vẫn cần xác minh." else "Đã cho phép hàng đợi.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableManagement.value = mutableManagement.value.copy(feedback = "Chưa lưu được lựa chọn tạm dừng.") }
        }
    }

    fun cancelTask(id: String) = mutate {
        check(services.queue.cancel(services.engineKey, id))
        "Đã hủy yêu cầu chưa thực thi."
    }

    fun retryDeferred(id: String) = mutate {
        check(services.queue.retryDeferred(services.engineKey, id))
        "Đã đưa yêu cầu về trạng thái chờ kiểm tra lại."
    }

    fun renameClone(engineKey: String, id: String, label: String, group: String?) = mutate {
        services.metadata(engineKey).rename(id, label, group)
        try {
            services.shortcuts.synchronizePinned()
            "Đã lưu tên và nhóm; đã gửi đồng bộ tên lối tắt."
        } catch (_: Exception) { "Đã lưu tên và nhóm. Launcher chưa nhận được tên mới; hãy quét lại để đồng bộ." }
    }

    fun styleClone(engineKey: String, id: String, accentKey: String, badgeText: String?) = mutate {
        services.metadata(engineKey).restyle(id, accentKey, badgeText)
        try {
            services.shortcuts.synchronizePinned(forceIcons = true)
            "Đã lưu màu và huy hiệu; đã gửi đồng bộ lối tắt."
        } catch (_: Exception) {
            "Đã lưu màu và huy hiệu. Launcher chưa cập nhật biểu tượng; hãy mở lại Fonesi để thử đồng bộ."
        }
    }

    fun openClone(token: String) = mutate {
        when (val result = services.launches.launch(token)) {
            is LaunchResult.Accepted -> "Đã gửi yêu cầu mở ${result.clone.label}; chưa xác nhận màn hình ứng dụng."
            is LaunchResult.Blocked -> result.reason.message()
        }
    }

    fun pinClone(token: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { services.launches.inspect(token) }
            val feedback = when (result) {
                is LaunchResult.Blocked -> result.reason.message()
                is LaunchResult.Accepted -> try {
                    when (services.shortcuts.requestPin(result.clone)) {
                        PinResult.REQUEST_SENT -> "Đã gửi yêu cầu tạo lối tắt. Hãy xác nhận trên màn hình hệ thống; chưa xác nhận biểu tượng đã được thêm."
                        PinResult.UNSUPPORTED -> "Launcher hiện tại không hỗ trợ yêu cầu ghim lối tắt."
                        PinResult.REJECTED -> "Launcher chưa nhận yêu cầu tạo lối tắt. Hãy kiểm tra cài đặt màn hình chính."
                    }
                } catch (_: Exception) { "Chưa gửi được yêu cầu tạo lối tắt. Hãy thử lại khi Fonesi đang mở." }
            }
            mutableManagement.value = mutableManagement.value.copy(feedback = feedback)
        }
    }

    fun configureEngine(enabled: Boolean): Boolean = try {
        RuntimeBindings.enable(getApplication(), enabled)
        true
    } catch (_: Exception) {
        mutableManagement.value = mutableManagement.value.copy(feedback = "Chưa lưu được lựa chọn engine; Fonesi vẫn mở.")
        false
    }

    fun createClone(packageName: String, label: String, group: String? = null) = mutate {
        check(packageName != getApplication<Application>().packageName)
        val source = checkNotNull(AndroidSourceCatalog(getApplication<Application>().packageManager).current(packageName))
        services.metadata(services.engineKey).create(source, label, group)
        "Đã đọc lại bản cài từ engine và lưu tên. Cần mở để kiểm tra dữ liệu/tài khoản."
    }

    fun duplicateClone(token: String, label: String) = mutate {
        val row = currentClone(token)
        val source = checkNotNull(AndroidSourceCatalog(getApplication<Application>().packageManager).current(row.packageName))
        services.metadata(services.engineKey).create(source, label, row.groupName)
        "Đã tạo bản sạch từ ứng dụng gốc; bạn tự đăng nhập tài khoản mới."
    }

    fun deleteClone(token: String) = mutate {
        val row = currentClone(token)
        services.metadata(services.engineKey).delete(row.cloneId, token)
        try { services.shortcuts.synchronizePinned() } catch (_: Exception) { /* Old URI already fails closed. */ }
        "Engine đã xác nhận không còn bản cài; đã xóa mục quản lý."
    }

    fun stopClone(token: String) = mutate {
        val row = currentClone(token)
        services.operationLock().acquire().let { checkNotNull(it) }.use {
            CloneStopIdentityGuard.requireSameGuest(
                row,
                services.database.clones().findByLaunchToken(token),
                services.engineKey,
                token,
                engine.listClones(),
            )
            // Keep the existing conservative global package/update barrier for now.
            check(services.tasks.list().none { it.packageName == row.packageName && it.state in setOf(
                TaskState.PREPARING, TaskState.APPLYING, TaskState.VERIFYING, TaskState.REVIEW_REQUIRED) })
            (engine as? StoppableCloneEngine ?: error("Stop unavailable")).stop(row.cloneId)
        }
        "Engine đã xác nhận dừng; dữ liệu không được yêu cầu xóa."
    }

    fun reconcileClones() = mutate {
        services.runner().recover()
        val count = services.metadata(services.engineKey).importDiscovered()
        "Đã đối chiếu engine; bổ sung $count mục có bản cài thật nhưng thiếu metadata."
    }

    private fun currentClone(token: String): CloneMetadataRow = checkNotNull(services.database.clones().findByLaunchToken(token)).also {
        check(it.engineKey == services.engineKey)
    }

    fun previewUpdates(tokens: List<String>) {
        previewJob?.cancel()
        mutablePreview.value = UpdatePreviewState(loading = true)
        previewJob = viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) { services.updateRequests.preview(tokens.toList()) }
                mutablePreview.value = UpdatePreviewState(preview = preview)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutablePreview.value = UpdatePreviewState(error = "Chưa đối chiếu được nguồn/engine hoặc đang có thao tác khác. Chưa xếp yêu cầu nào; hãy mở khóa/đợi rồi xem lại.") }
        }
    }
    fun dismissUpdatePreview() { previewJob?.cancel(); mutablePreview.value = UpdatePreviewState() }
    fun confirmUpdates(id: String) {
        val preview = mutablePreview.value.preview?.takeIf { it.id == id } ?: return
        mutate {
            try {
                val jobs = services.updateRequests.confirm(preview)
                mutablePreview.value = UpdatePreviewState()
                "Đã xếp/đối chiếu ${jobs.size} nhóm. Chọn Chạy hàng đợi để áp dụng tuần tự; chưa báo cập nhật thành công."
            } catch (_: UpdatePreviewChangedException) {
                mutablePreview.value = UpdatePreviewState(error = "Nguồn, trạng thái hoặc phạm vi đã đổi sau preview. Chưa xếp yêu cầu mới; đóng và xem trước lại.")
                "Thông tin cập nhật đã thay đổi; cần xem trước lại."
            } catch (error: Exception) {
                mutablePreview.value = mutablePreview.value.copy(error = "Chưa xác nhận xếp hàng. Đóng rồi đối chiếu lại trước khi tiếp tục.")
                throw error
            }
        }
    }

    fun runQueue() {
        if (mutableManagement.value.busy) return
        mutableManagement.value = mutableManagement.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    var processed = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        when (services.runner().runNext()) {
                            is RunResult.Processed -> processed++
                            else -> break
                        }
                    }
                    processed
                }
                mutableManagement.value = mutableManagement.value.copy(feedback = "Đã xử lý $count tác vụ. Xem kết quả từng tác vụ; số đã xử lý không đồng nghĩa tất cả thành công.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableManagement.value = mutableManagement.value.copy(feedback = "Hàng đợi dừng do lỗi. Giữ nguyên journal để đối chiếu khi mở lại.") }
            finally { mutableManagement.value = mutableManagement.value.copy(busy = false); refreshRuntime() }
        }
    }

    private fun refreshRuntime() {
        viewModelScope.launch {
            mutableRuntime.value = withContext(Dispatchers.IO) {
                try {
                    val lease = services.operationLock().acquire() ?: return@withContext mutableRuntime.value
                    lease.use {
                    if (engine.availability is EngineAvailability.Ready) RuntimeState(
                        ready = true,
                        description = RuntimeBindings.status(),
                        clones = engine.listClones().associateBy { it.id },
                        capabilities = engine.capabilities,
                    ) else RuntimeState(
                        ready = false,
                        description = RuntimeBindings.status(),
                        capabilities = engine.capabilities,
                    )
                    }
                } catch (_: Exception) { RuntimeState(
                    ready = false,
                    description = "Chưa đọc được trạng thái engine. Dữ liệu quản lý vẫn được giữ.",
                    capabilities = EngineCapabilities(),
                ) }
            }
        }
    }

    private fun mutate(action: () -> String) {
        if (mutableManagement.value.busy) return
        mutableManagement.value = mutableManagement.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val feedback = withContext(Dispatchers.IO) { action() }
                mutableManagement.value = mutableManagement.value.copy(feedback = feedback)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: UnsupportedSourceException) {
                mutableManagement.value = mutableManagement.value.copy(feedback = "Engine ứng viên chưa nhập được bộ split APK. Cần engine hỗ trợ đầy đủ; không bỏ split để tạo bản lỗi.")
            } catch (_: Exception) {
                mutableManagement.value = mutableManagement.value.copy(feedback = "Thao tác chưa được xác nhận. Không xóa hoặc tạo lại dữ liệu để xử lý lỗi.")
            } finally { mutableManagement.value = mutableManagement.value.copy(busy = false); refreshRuntime() }
        }
    }

    fun refresh() {
        refreshDiagnostics()
        refreshRuntime()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { catalog.scan() }
                mutableState.value = AppsState(result.apps, false, true, result.unreadableCount)
                try {
                    withContext(Dispatchers.IO) { services.shortcuts.synchronizePinned() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutableManagement.value = mutableManagement.value.copy(feedback = "Danh sách ứng dụng đã được quét; chưa đồng bộ được lối tắt màn hình chính.")
                }
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
