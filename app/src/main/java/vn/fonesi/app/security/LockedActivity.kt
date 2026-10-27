package vn.fonesi.app.security

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Common enforcement for manager and shortcut. An Intent extra can never bypass this activity. */
abstract class LockedActivity : FragmentActivity() {
    protected val access: AccessGateModel by viewModels()
    private var prompt: BiometricPrompt? = null
    private var active: AuthAttempt? = null
    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val current = active ?: return@registerForActivityResult
        active = null
        access.complete(current, result.resultCode == RESULT_OK)
        updateSecureFlag()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreAttempt(savedInstanceState)
        active?.let { prompt = createPrompt(it) }
    }
    private fun createPrompt(request: AuthAttempt) = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (active != request) return
                active = null
                access.complete(request, true)
                updateSecureFlag()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (active != request) return
                if (Build.VERSION.SDK_INT < 30 && errorCode in setOf(BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT)) {
                    launchCredential(request)
                } else {
                    active = null
                    access.complete(request, false, "Xác thực chưa hoàn tất. Có thể thử lại bằng khóa máy.")
                }
            }
        })
    override fun onStart() { super.onStart(); access.foreground(); updateSecureFlag() }
    override fun onStop() {
        if (!isChangingConfigurations && !access.externalCredential) { active = null; prompt?.cancelAuthentication() }
        access.background(isChangingConfigurations)
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        active?.let { outState.putLong("auth_id", it.id); outState.putString("auth_purpose", it.purpose.name) }
        super.onSaveInstanceState(outState)
    }
    protected fun restoreAttempt(saved: Bundle?) {
        if (saved?.containsKey("auth_id") == true) {
            val purpose = saved.getString("auth_purpose")?.let { runCatching { AuthPurpose.valueOf(it) }.getOrNull() }
            if (purpose != null) active = AuthAttempt(saved.getLong("auth_id"), purpose)
        }
    }
    protected fun authenticate(purpose: AuthPurpose, credentialOnly: Boolean = false) {
        val current = access.begin(purpose) ?: return
        active = current
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val allowed = if (Build.VERSION.SDK_INT >= 30) strong or BiometricManager.Authenticators.DEVICE_CREDENTIAL else strong
        if (!credentialOnly && BiometricManager.from(this).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS) {
            val builder = BiometricPrompt.PromptInfo.Builder().setTitle("Xác thực để mở Fonesi")
                .setSubtitle("Dùng sinh trắc học hoặc mã khóa của điện thoại")
                .setAllowedAuthenticators(allowed)
            if (Build.VERSION.SDK_INT < 30) builder.setNegativeButtonText("Dùng khóa máy")
            try { prompt = createPrompt(current); prompt!!.authenticate(builder.build()) }
            catch (_: Exception) { launchCredential(current) }
        } else launchCredential(current)
    }
    @Suppress("DEPRECATION")
    private fun launchCredential(current: AuthAttempt) {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val intent = if (keyguard.isDeviceSecure) keyguard.createConfirmDeviceCredentialIntent("Mở Fonesi", "Xác thực bằng mã khóa điện thoại") else null
        if (intent == null || !access.credential(current)) {
            active = null
            access.complete(current, false, "Cần thiết lập khóa màn hình Android trước khi dùng khóa Fonesi.")
            return
        }
        try { credential.launch(intent) }
        catch (_: Exception) { active = null; access.complete(current, false, "Chưa mở được màn hình xác thực Android.") }
    }
    private fun updateSecureFlag() {
        if (AccessBroker.get(this).policy.read().required) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
