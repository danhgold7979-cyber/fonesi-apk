package vn.fonesi.payload

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.UUID

/** Synthetic data only: no credentials, network, contacts or third-party apps. */
class PayloadActivity : Activity() {
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
}
