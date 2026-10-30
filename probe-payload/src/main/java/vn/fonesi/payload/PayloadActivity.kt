package vn.fonesi.payload

import android.annotation.RequiresApi
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Synthetic data only: no credentials, network, contacts or third-party apps. */
class PayloadActivity : Activity() {
    private var e82Receipt: JSONObject? = null
    private var e82ReceiptFile: File? = null

    companion object {
        private const val E82_HOST_PACKAGE = "vn.fonesi.nativeprobe"
        private const val E82_HOST_PROCESS = "vn.fonesi.nativeprobe:fonesi_guest_ui"
        private const val E82_HOST_STUB = "vn.fonesi.nativeprobe.E82HostActivityStub"
        private const val E82_GUEST_ACTIVITY = "vn.fonesi.payload.PayloadActivity"
    }
    // Test-owned signed PRIVATE constructor proof only. Never used by the production clone engine.
    init { SyntheticActivityConstructionProbe.recordConstructor() }

    // E81: test-only explicit method delegation; the Activity framework base remains null.
    private var syntheticProbeScope: Context? = null
    private var syntheticProbeScopeId: String? = null

    fun bindSyntheticScopedContextForTestOnly(scope: Context, scopeId: String) {
        val canonical = SyntheticActivityScopedBindingRules.nextBoundScope(syntheticProbeScopeId, scopeId)
        check(baseContext == null) { "E81 cannot bind a framework-attached Activity" }
        check(scope.packageName == "vn.fonesi.payload" &&
            scope.classLoader === javaClass.classLoader) { "E81 scope loader/package mismatch" }
        val marker = scope.resources.getIdentifier(
            "fonesi_guest_code_probe", "string", "vn.fonesi.payload")
        check(marker != 0 && scope.resources.getString(marker) == "Fonesi signed private payload") {
            "E81 scope resources are not signed PRIVATE payload"
        }
        syntheticProbeScope = scope
        syntheticProbeScopeId = canonical
    }

    fun boundSyntheticScopeIdForTestOnly(): String? = syntheticProbeScopeId

    fun readBoundSyntheticScopeForTestOnly(): String {
        val scoped = syntheticProbeScope ?: error("E81 scoped Activity method called before binding")
        check(baseContext == null) { "E81 Activity unexpectedly framework-attached" }
        return ScopedProbeEntrypoint.read(scoped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra("proof_level") == "e82" ||
            intent.getBooleanExtra("e82_factory_authorized", false)) {
            beginE82HostStubLifecycle()
            return
        }
        val preferences = getSharedPreferences("isolation_probe", MODE_PRIVATE)
        val file = File(filesDir, "probe-marker.txt")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        fun text(value: String) = TextView(this).apply {
            text = value; textSize = 17f
            layout.addView(this)
        }
        text("Fonesi · Dữ liệu mẫu").textSize = 25f
        text("Chỉ nhập nhãn thử nghiệm như GỐC, A hoặc B. Không nhập tài khoản hay mật khẩu.")
        val installId = preferences.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("install_id", it).commit())
        }
        text("ID vùng dữ liệu: $installId")
        val input = EditText(this).apply {
            hint = "Nhãn vùng dữ liệu"
            setText(preferences.getString("marker", ""))
            maxLines = 2
            layout.addView(this)
        }
        val result = text("")
        fun readBack() {
            result.text = "SharedPreferences: ${preferences.getString("marker", "(trống)")}\n" +
                "Tệp riêng: ${if (file.exists()) file.readText() else "(trống)"}"
        }
        layout.addView(Button(this).apply {
            text = "Lưu và đọc lại"
            setOnClickListener {
                val marker = input.text.toString().take(100)
                try {
                    check(preferences.edit().putString("marker", marker).commit())
                    file.writeText(marker)
                    readBack()
                } catch (_: Exception) {
                    result.text = "Lỗi ghi/đọc dữ liệu mẫu. Bài kiểm tra chưa đạt."
                }
            }
        })
        readBack()
        val scroll = ScrollView(this).apply { addView(layout) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    /** E82 records only the host-stub lifecycle; this is not a guest package/UID lifecycle. */
    @RequiresApi(28)
    private fun beginE82HostStubLifecycle() {
        check(Build.VERSION.SDK_INT >= 28) { "E82 requires public AppComponentFactory API 28+" }
        check(intent.getBooleanExtra("e82_factory_authorized", false)) {
            "E82 Activity was not returned by the armed host AppComponentFactory"
        }
        val component = intent.component ?: error("E82 framework Intent has no ComponentName")
        check(component == ComponentName(E82_HOST_PACKAGE, E82_HOST_STUB)) {
            "E82 framework ActivityInfo does not identify the non-exported host stub"
        }
        check(packageName == E82_HOST_PACKAGE && applicationInfo.uid == Process.myUid() &&
            Application.getProcessName() == E82_HOST_PROCESS) {
            "E82 Activity did not receive the expected host package/UID/process Context"
        }
        check(baseContext != null && window != null) {
            "E82 framework did not attach a non-null host Context and Window"
        }
        check(javaClass.name == intent.getStringExtra("e82_factory_authorized_class") &&
            javaClass.name == intent.getStringExtra("e82_private_activity_class") &&
            javaClass.name == E82_GUEST_ACTIVITY && javaClass.classLoader !== classLoader) {
            "E82 factory did not return the exact signed PRIVATE Activity classloader instance"
        }

        val runId = intent.getStringExtra("e82_run_id") ?: error("E82 run UUID missing")
        check(UUID.fromString(runId).toString() == runId) { "E82 run UUID is not canonical" }
        val nonce = intent.getStringExtra("e82_nonce") ?: error("E82 nonce missing")
        check(Regex("^[0-9a-f]{32}$").matches(nonce)) { "E82 nonce is malformed" }
        val sourceCommit = intent.getStringExtra("e82_source_commit") ?: error("E82 source SHA missing")
        val hostApkSha = intent.getStringExtra("e82_host_apk_sha256") ?: error("E82 host APK SHA missing")
        val payloadApkSha = intent.getStringExtra("e82_payload_apk_sha256") ?: error("E82 payload APK SHA missing")
        val clusterId = intent.getStringExtra("e82_private_cluster_id") ?: error("E82 private cluster ID missing")
        check(Regex("^[0-9a-f]{40}$").matches(sourceCommit) &&
            Regex("^[0-9a-f]{64}$").matches(hostApkSha) &&
            Regex("^[0-9a-f]{64}$").matches(payloadApkSha) &&
            UUID.fromString(clusterId).toString() == clusterId) {
            "E82 source/APK/cluster identity is malformed"
        }

        val packageManager = packageManager
        val stubInfo = packageManager.getActivityInfo(ComponentName(E82_HOST_PACKAGE, E82_HOST_STUB), 0)
        check(stubInfo.packageName == E82_HOST_PACKAGE && stubInfo.name == E82_HOST_STUB &&
            stubInfo.processName == E82_HOST_PROCESS && !stubInfo.exported &&
            stubInfo.applicationInfo.uid == Process.myUid()) {
            "E82 PackageManager host-stub metadata does not match the actual host component"
        }
        val guestComponentInHost = ComponentName(E82_HOST_PACKAGE, E82_GUEST_ACTIVITY)
        val guestComponentRegisteredInHost = try {
            packageManager.getActivityInfo(guestComponentInHost, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        check(!guestComponentRegisteredInHost) {
            "E82 private payload Activity was unexpectedly registered as a host package component"
        }
        packageManager.getPackageInfo("vn.fonesi.payload", 0) // Installed source fixture is expected and is not guest registration.

        val receiptDir = File(filesDir, "e82-factory-receipts").canonicalFile
        check((receiptDir.isDirectory || receiptDir.mkdirs()) &&
            receiptDir.path.startsWith(filesDir.canonicalPath + File.separator)) {
            "E82 receipt directory escaped host-private storage"
        }
        e82ReceiptFile = File(receiptDir, "$runId.json")
        e82Receipt = JSONObject()
            .put("complete", false)
            .put("passed", false)
            .put("proof_level", "e82")
            .put("run_id", runId)
            .put("nonce", nonce)
            .put("source_commit", sourceCommit)
            .put("probe_apk_sha256", hostApkSha)
            .put("payload_apk_sha256", payloadApkSha)
            .put("private_cluster_id", clusterId)
            .put("private_activity_class_name", javaClass.name)
            .put("private_activity_classloader_distinct_from_host", true)
            .put("factory_authorization_consumed", true)
            .put("framework_on_create_called", true)
            .put("framework_on_resume_called", false)
            .put("base_context_non_null", baseContext != null)
            .put("window_non_null", window != null)
            .put("api", Build.VERSION.SDK_INT)
            .put("pid", Process.myPid())
            .put("uid", Process.myUid())
            .put("process_name", Application.getProcessName())
            .put("host_package", packageName)
            .put("host_uid", applicationInfo.uid)
            .put("host_stub_component", component.flattenToString())
            .put("host_stub_activity_info_package", stubInfo.packageName)
            .put("host_stub_activity_info_name", stubInfo.name)
            .put("host_stub_activity_info_process", stubInfo.processName)
            .put("host_stub_activity_info_exported", stubInfo.exported)
            .put("host_stub_activity_info_uid", stubInfo.applicationInfo.uid)
            .put("guest_component_registered_with_host_package_manager", false)
            .put("payload_source_fixture_package_installed", true)
            .put("guest_uid_isolation", false)
            .put("third_party_component_executed", false)
            .put("social_accounts_tested", false)
            .put("production_clone_accepted", false)
            .put("physical_device_tested", false)
        writeE82Receipt()
    }

    override fun onResume() {
        super.onResume()
        val receipt = e82Receipt ?: return
        receipt.put("framework_on_resume_called", true)
            .put("complete", true)
            .put("passed", true)
        if (Build.VERSION.SDK_INT >= 28) writeE82Receipt()
        finish()
    }

    @RequiresApi(28)
    private fun writeE82Receipt() {
        val file = e82ReceiptFile ?: error("E82 receipt path was not initialized")
        val receipt = e82Receipt ?: error("E82 receipt data was not initialized")
        file.writeText(receipt.toString())
    }
}
