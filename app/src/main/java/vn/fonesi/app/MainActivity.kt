package vn.fonesi.app

import android.os.Bundle
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
import android.app.ActivityManager
import android.os.Process
import vn.fonesi.app.runtime.RuntimeBindings
import vn.fonesi.app.security.*
import vn.fonesi.app.configuration.*
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import vn.fonesi.app.update.UpdatePreviewDialog
import vn.fonesi.app.update.UpdateJournalRecoveryScheduler
import vn.fonesi.core.*
import vn.fonesi.app.update.AutomaticUpdatesCard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : LockedActivity() {
    private val model: AppsViewModel by viewModels()
    private val configuration: ConfigurationViewModel by viewModels()
    private val exportConfiguration = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        configuration.documentSelected(DocumentAction.EXPORT, uri)
    }
    private val importConfiguration = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        configuration.documentSelected(DocumentAction.IMPORT, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val gate by access.state.collectAsStateWithLifecycle()
            AccessGate(gate, { authenticate(AuthPurpose.UNLOCK) }, { authenticate(AuthPurpose.UNLOCK, credentialOnly = true) }) {
            LaunchedEffect(gate.unlocked) {
                // SDK activation is allowed only while the manager is visible and access is unlocked.
                // Background WorkManager recovery never calls RuntimeBindings.attach().
                if (gate.unlocked) {
                    UpdateJournalRecoveryScheduler.ensureScheduled(applicationContext)
                    RuntimeBindings.attach(applicationContext)
                }
            }
            LaunchedEffect(Unit) { model.refresh() }
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive) { model.automaticTick(); delay(60_000) }
                }
            }
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFF9CD8BD), secondary = Color(0xFFB0CCC0),
            ) else lightColorScheme(
                primary = Color(0xFF126B55), secondary = Color(0xFF49665A),
                background = Color(0xFFF7FAF6), surface = Color(0xFFF7FAF6),
            )
            MaterialTheme(colorScheme = scheme) {
                val state by model.state.collectAsStateWithLifecycle()
                val management by model.management.collectAsStateWithLifecycle()
                val runtime by model.runtime.collectAsStateWithLifecycle()
                FonesiScreen(state, management, runtime, model, gate, configuration,
                    export = { exportConfiguration.launch("fonesi-configuration.json") },
                    openConfiguration = { importConfiguration.launch(arrayOf("application/json", "text/plain")) },
                    securityChange = { authenticate(if (it) AuthPurpose.ENABLE else AuthPurpose.DISABLE) }) { enabled ->
                    if (model.configureEngine(enabled)) {
                        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses.orEmpty()
                            .filter { it.uid == Process.myUid() && it.pid != Process.myPid() }
                            .forEach { Process.killProcess(it.pid) }
                        finishAndRemoveTask()
                        Process.killProcess(Process.myPid())
                    }
                }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (access.state.value.unlocked) model.refresh()
    }
}

@Composable
private fun FonesiScreen(state: AppsState, management: ManagementState, runtime: RuntimeState, model: AppsViewModel,
    gate: GateState, configuration: ConfigurationViewModel, export: () -> Unit, openConfiguration: () -> Unit,
    securityChange: (Boolean) -> Unit,
    engineChange: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var priorityOnly by rememberSaveable { mutableStateOf(true) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var cloneQuery by rememberSaveable { mutableStateOf("") }
    var cloneGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var cloneStatus by rememberSaveable { mutableStateOf(CloneStatusFilter.ALL) }
    var selectedClones by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val preview by model.updatePreview.collectAsStateWithLifecycle()
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val automatic by model.automatic.collectAsStateWithLifecycle()
    val autoPolicy by model.autoPolicy.collectAsStateWithLifecycle()
    val busy = management.busy || automatic.running
    val cloneItems = remember(management.clones, runtime, model.engineKey) {
        management.clones.map { row ->
            val live = runtime.clones[row.cloneId]?.takeIf { row.engineKey == model.engineKey && it.packageName == row.packageName }
            val status = when (live?.running) { true -> CloneStatusFilter.RUNNING; false -> CloneStatusFilter.STOPPED; null -> CloneStatusFilter.UNKNOWN }
            CloneListItem(row.launchToken, row.packageName, row.label, row.groupName, status)
        }
    }
    val visibleClones = remember(cloneItems, cloneQuery, cloneGroup, cloneStatus) { CloneListPolicy.filter(cloneItems, cloneQuery, cloneGroup, cloneStatus).map { it.token }.toSet() }
    val eligible = remember(management.clones, runtime, model.engineKey) {
        management.clones.filter { row -> runtime.ready && row.engineKey == model.engineKey && runtime.clones[row.cloneId]?.packageName == row.packageName }.map { it.launchToken }.toSet()
    }
    val retained = CloneListPolicy.retain(selectedClones.toSet(), eligible)
    LaunchedEffect(retained, selectedClones) { if (retained != selectedClones.toSet()) selectedClones = retained.toList() }
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
                        Text(if (RuntimeBindings.candidate) "Bản thử tích hợp engine · chưa nghiệm thu" else "Bản cloud · không chứa engine", fontWeight = FontWeight.SemiBold)
                        Text(runtime.description)
                        if (RuntimeBindings.candidate) Text("Chưa chứng nhận đăng nhập/cách ly trên máy thật. Engine ứng viên chỉ nhận một APK đầy đủ, chưa nhận bộ split APK.")
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Ứng dụng", "Bản clone", "Cập nhật", "Thiết lập").forEachIndexed { index, title ->
                        FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) })
                    }
                }
                management.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                management.feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                    OutlinedButton(onClick = model::reconcileClones, enabled = runtime.ready && !busy) { Text("Đối chiếu với engine") }
                    CloneListControls(cloneQuery, { cloneQuery = it }, management.clones.mapNotNull { it.groupName }.distinct().sorted(),
                        cloneGroup, { cloneGroup = it }, cloneStatus, { cloneStatus = it }, retained.size, visibleClones.size, (visibleClones intersect eligible).size,
                        selectVisible = { selectedClones = CloneListPolicy.selectVisible(retained, visibleClones, eligible).toList() },
                        clear = { selectedClones = emptyList() }, update = { model.previewUpdates(retained.toList()) },
                        enabled = runtime.ready && management.error == null && !busy)
                }
                items(management.clones.filter { it.launchToken in visibleClones }, key = { "clone:${it.engineKey.length}:${it.engineKey}:${it.cloneId}" }) { row ->
                    SavedCloneCard(row, enabled = management.error == null && !busy,
                        operational = runtime.ready && row.engineKey == model.engineKey,
                        selected = row.launchToken in retained,
                        toggle = { selectedClones = CloneListPolicy.toggle(retained, row.launchToken, eligible).toList() },
                        live = runtime.clones[row.cloneId]?.takeIf { row.engineKey == model.engineKey && it.packageName == row.packageName },
                        open = { model.openClone(row.launchToken) }, pin = { model.pinClone(row.launchToken) },
                        stop = { model.stopClone(row.launchToken) }, delete = { model.deleteClone(row.launchToken) },
                        duplicate = { label -> model.duplicateClone(row.launchToken, label) },
                        update = { model.previewUpdates(listOf(row.launchToken)) },
                        saveStyle = { accent, badge -> model.styleClone(row.engineKey, row.cloneId, accent, badge) }) { label, group ->
                        model.renameClone(row.engineKey, row.cloneId, label, group)
                    }
                }
            } else if (tab == 2) {
                item {
                    Text("Hàng đợi cập nhật", style = MaterialTheme.typography.titleLarge)
                    Text(if (management.paused) "Đã lưu: tạm dừng hàng đợi." else "Đã lưu: cho phép hàng đợi.")
                    Text("Cập nhật từ app gốc đang cài. Với engine dùng chung mã, một tác vụ ảnh hưởng mọi bản cùng ứng dụng. Chỉ chạy tuần tự; hoãn bản đang dùng hoặc chưa rõ trạng thái.")
                    OutlinedButton(onClick = { model.setQueuePaused(!management.paused) }, enabled = management.loaded && management.error == null) {
                        Text(if (management.paused) "Cho phép chạy" else "Tạm dừng")
                    }
                    OutlinedButton(onClick = { model.previewUpdates(eligible.toList()) },
                        enabled = runtime.ready && eligible.isNotEmpty() && !busy) { Text("Kiểm tra cập nhật tất cả") }
                    Button(onClick = model::runQueue, enabled = runtime.ready && !busy && !management.paused) { Text("Chạy hàng đợi") }
                    AutomaticUpdatesCard(automatic, autoPolicy, runtime.ready && !management.busy,
                        model::setAutoMode, model::automaticTick, model::showAutomaticPreview)
                    Text("Giữ Fonesi mở khi cập nhật. Nếu Android đóng tiến trình, mở lại để đối chiếu journal; không tự áp dụng lại kết quả chưa rõ.")
                    if (management.loaded && management.jobs.isEmpty()) Text("Chưa có yêu cầu cập nhật. Không có tác vụ mẫu hoặc kết quả giả.")
                }
                items(management.jobs.asReversed(), key = { "task:${it.id}" }) { task ->
                    UpdateTaskCard(task, management.error == null, { model.cancelTask(task.id) }, { model.retryDeferred(task.id) })
                }
            } else {
                item {
                    Text("Khóa truy cập", style = MaterialTheme.typography.titleLarge)
                    Text(if (gate.required) "Đang bật: rời Fonesi sẽ khóa lại, gồm cả lối tắt bản clone." else "Đang tắt khóa giao diện.")
                    Text("Dùng sinh trắc học hoặc mã khóa máy. Đây là khóa giao diện quản lý/mở mới, không mã hóa hay khóa ứng dụng khách đã chạy.")
                    OutlinedButton(onClick = { securityChange(!gate.required) }, enabled = !gate.authenticating && !busy) {
                        Text(if (gate.required) "Xác thực và tắt khóa" else "Xác thực và bật khóa")
                    }
                    gate.message?.let { Text(it) }
                    ConfigurationCard(configuration, management.clones.filter {
                        it.engineKey == model.engineKey && runtime.clones.containsKey(it.cloneId)
                    }, runtime.ready, !busy, export, openConfiguration)
                    RuntimeSettingsCard(engineChange, enabled = !busy)
                    DeviceDiagnosticsCard(diagnostics, model::refreshDiagnostics)
                }
            }
            item {
                Text(if (RuntimeBindings.candidate) "Danh sách được quét trên máy. Bản thử có Internet để ứng dụng khách kết nối; SDK mã đóng chưa được audit toàn bộ."
                    else "Danh sách được xử lý trên máy. Bản cloud này không có quyền Internet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (selected != null) {
        var cloneLabel by remember(selected.packageName) { mutableStateOf("${selected.label.take(60)} phụ") }
        AlertDialog(
            onDismissRequest = { selectedPackage = null },
            title = { Text(selected.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selected.packageName)
                    Text("Phiên bản gốc: ${selected.versionName} (${selected.source.versionCode})")
                    Text("Bộ cài đang có: ${selected.source.apkPaths.size} tệp APK")
                    Text("Tạo bản sạch và tự đăng nhập; không sao chép phiên đăng nhập của bản gốc.")
                    if (RuntimeBindings.candidate && selected.source.apkPaths.size != 1) Text("Bộ cài có split APK: engine ứng viên chưa hỗ trợ nhập đầy đủ. Không thể tạo bằng cách bỏ split.", color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(cloneLabel, { cloneLabel = it }, label = { Text("Tên bản clone") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { model.createClone(selected.packageName, cloneLabel); selectedPackage = null },
                enabled = runtime.ready && !busy && cloneLabel.trim().length in 1..80 &&
                    (!RuntimeBindings.candidate || selected.source.apkPaths.size == 1)) { Text("Tạo bản sạch") } },
            dismissButton = { TextButton(onClick = { selectedPackage = null }) { Text("Đóng") } },
        )
    }
    UpdatePreviewDialog(preview, !busy, model::dismissUpdatePreview, model::confirmUpdates)
}

@Composable
private fun AppCard(app: DeviceApp, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text("Bản gốc ${app.versionName}", style = MaterialTheme.typography.bodySmall)
                Text(if (RuntimeBindings.candidate && app.source.apkPaths.size > 1) "Bộ split APK · engine ứng viên chưa hỗ trợ" else "Chưa kiểm chứng clone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text("Chi tiết", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
