package vn.fonesi.app.security

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun AccessGate(state: GateState, unlock: () -> Unit, credential: () -> Unit, content: @Composable () -> Unit) {
    if (state.unlocked) content() else MaterialTheme {
        Scaffold { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Fonesi đã khóa", style = MaterialTheme.typography.headlineMedium)
                Text("Xác thực bằng sinh trắc học hoặc khóa màn hình Android để mở trình quản lý hay bản clone.")
                state.message?.let { Text(it) }
                Button(onClick = unlock, enabled = !state.authenticating) { Text("Xác thực") }
                OutlinedButton(onClick = credential, enabled = !state.authenticating) { Text("Dùng mã khóa máy") }
                if (state.authenticating) CircularProgressIndicator()
            }
        }
    }
}
