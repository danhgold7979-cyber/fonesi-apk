package vn.fonesi.app.shortcuts

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.fonesi.app.MainActivity
import vn.fonesi.app.storage.LocalServices

/** Explicit shortcut destination. No browsable intent filter and no package/engine intent overrides. */
class CloneLaunchActivity : ComponentActivity() {
    private val model: CloneLaunchViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.start(intent)
        setContent {
            val result by model.result.collectAsStateWithLifecycle()
            LaunchedEffect(result) { if (result is LaunchResult.Accepted) finish() }
            MaterialTheme {
                Scaffold { insets ->
                    Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Mở bản clone", style = MaterialTheme.typography.headlineMedium)
                        val blocked = result as? LaunchResult.Blocked
                        if (blocked == null) {
                            CircularProgressIndicator()
                            Text("Đang kiểm tra bản clone…")
                        } else {
                            Text(blocked.reason.message())
                            Button(onClick = {
                                startActivity(Intent(this@CloneLaunchActivity, MainActivity::class.java))
                                finish()
                            }) { Text("Mở Fonesi") }
                            TextButton(onClick = { finish() }) { Text("Đóng") }
                        }
                    }
                }
            }
        }
    }
}

/** ViewModel retains the one dispatch across configuration changes; there is no automatic retry. */
class CloneLaunchViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableResult = MutableStateFlow<LaunchResult?>(null)
    val result = mutableResult.asStateFlow()
    private var started = false
    fun start(intent: Intent?) {
        if (started) return
        started = true
        val token = CloneRoute.parse(intent)
        if (token == null) {
            mutableResult.value = LaunchResult.Blocked(LaunchBlock.INVALID_LINK)
            return
        }
        viewModelScope.launch {
            mutableResult.value = withContext(Dispatchers.IO) { LocalServices.get(getApplication()).launches.launch(token) }
        }
    }
}

internal fun LaunchBlock.message(): String = when (this) {
    LaunchBlock.INVALID_LINK -> "Lối tắt không hợp lệ. Hãy tạo lại từ Fonesi."
    LaunchBlock.CLONE_MISSING -> "Bản clone này không còn tồn tại. Hãy chọn bản khác trong Fonesi."
    LaunchBlock.ENGINE_UNAVAILABLE -> "Chưa có bộ máy clone sẵn sàng để mở bản này."
    LaunchBlock.ENGINE_CHANGED -> "Bản này thuộc bộ máy clone khác. Cần kiểm tra lại trong Fonesi."
    LaunchBlock.IDENTITY_CHANGED -> "Thông tin bản clone không khớp. Cần kiểm tra trước khi mở."
    LaunchBlock.UPDATE_UNRESOLVED -> "Bản này đang cập nhật hoặc còn kết quả cần xác minh. Hãy kiểm tra trong Fonesi."
    LaunchBlock.OPERATION_BUSY -> "Đang có thao tác khác. Hãy đợi hoàn tất rồi mở lại."
    LaunchBlock.STORAGE_ERROR -> "Chưa đọc được dữ liệu quản lý. Hãy kiểm tra trong Fonesi."
    LaunchBlock.ENGINE_ERROR -> "Chưa xác nhận được yêu cầu mở. Hãy kiểm tra trong Fonesi trước khi thử lại."
}
