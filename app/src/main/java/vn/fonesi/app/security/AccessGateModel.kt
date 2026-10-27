package vn.fonesi.app.security

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.fonesi.core.AccessSession
import java.util.UUID

enum class AuthPurpose { UNLOCK, ENABLE, DISABLE }
data class AuthAttempt(val id: Long, val purpose: AuthPurpose)
data class GateState(val unlocked: Boolean, val required: Boolean, val authenticating: Boolean = false, val message: String? = null)

class AccessGateModel @JvmOverloads constructor(app: Application, private val broker: AccessBroker = AccessBroker.get(app)) : AndroidViewModel(app) {
    private val owner = UUID.randomUUID().toString()
    private val session = AccessSession(broker.policy.read().required, SystemClock::elapsedRealtime)
    private val mutableState = MutableStateFlow(GateState(false, broker.policy.read().required))
    val state = mutableState.asStateFlow()
    private var attempt: AuthAttempt? = null
    var externalCredential = false
        private set

    init {
        viewModelScope.launch { broker.revision.collect { session.syncPolicy(broker.policy.read().required); publish() } }
    }

    fun foreground() {
        val policy = broker.policy.read()
        session.foreground(policy.required)
        publish(if (policy.unreadable) "Không đọc được lựa chọn khóa. Hãy xác thực bằng khóa máy rồi kiểm tra Thiết lập." else null)
    }
    fun background(rotating: Boolean) {
        session.background(rotating, externalCredential)
        if (!rotating) broker.revoke(owner)
        if (!session.authenticating) { attempt = null; externalCredential = false }
        publish()
    }
    fun begin(purpose: AuthPurpose): AuthAttempt? = session.begin()?.let {
        AuthAttempt(it, purpose).also { value -> attempt = value; publish() }
    }
    fun credential(attempt: AuthAttempt): Boolean {
        if (this.attempt != attempt || !session.authenticating) return false
        externalCredential = true
        return true
    }
    fun complete(value: AuthAttempt, success: Boolean, message: String? = null) {
        if (attempt != value) return
        attempt = null
        externalCredential = false
        if (!session.complete(value.id, success)) { publish(message ?: "Chưa xác thực. Fonesi vẫn khóa khi lựa chọn khóa đang bật."); return }
        try {
            if (value.purpose != AuthPurpose.UNLOCK) {
                val required = value.purpose == AuthPurpose.ENABLE
                broker.changePolicy(required)
                session.policyAfterAuthentication(required)
            }
            publish(if (value.purpose == AuthPurpose.ENABLE) "Đã bật khóa; rời Fonesi sẽ khóa lại."
                else if (value.purpose == AuthPurpose.DISABLE) "Đã tắt khóa giao diện." else null)
        } catch (_: Exception) {
            broker.revoke(owner)
            session.background(false)
            session.foreground(broker.policy.read().required)
            publish("Chưa lưu được lựa chọn khóa. Không xác nhận thay đổi; hãy kiểm tra dung lượng và thử lại.")
        }
    }
    private fun publish(message: String? = null) {
        val policy = broker.policy.read()
        session.syncPolicy(policy.required)
        broker.visible(owner, session.isForeground)
        if (session.authorized && session.requiresAuthentication) broker.grant(owner) else broker.revoke(owner)
        mutableState.value = GateState(session.authorized, policy.required, session.authenticating, message)
    }
    override fun onCleared() { broker.leave(owner) }
}
