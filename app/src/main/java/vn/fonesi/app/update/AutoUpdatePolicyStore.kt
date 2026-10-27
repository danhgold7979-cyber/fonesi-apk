package vn.fonesi.app.update

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

enum class AutoUpdateMode { OFF, CHECK_ON_OPEN, APPLY_WHILE_OPEN }
data class AutoUpdatePolicy(val mode: AutoUpdateMode, val unreadable: Boolean = false)

/** Automatic behavior is never imported from a configuration file; default/corrupt mode is OFF. */
class AutoUpdatePolicyStore(context: Context) {
    private val base = File(context.noBackupFilesDir, "auto-update-policy.json")
    private val file = AtomicFile(base)
    @Synchronized fun read(): AutoUpdatePolicy = try {
        if (!base.exists() && !File(base.path + ".bak").exists()) AutoUpdatePolicy(AutoUpdateMode.OFF)
        else {
            check(base.length() <= 1024 && File(base.path + ".bak").length() <= 1024)
            val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
            check(json.keys().asSequence().toSet() == setOf("version", "mode") && json.get("version") == 1 && json.get("mode") is String)
            AutoUpdatePolicy(AutoUpdateMode.valueOf(json.getString("mode")))
        }
    } catch (_: Exception) { AutoUpdatePolicy(AutoUpdateMode.OFF, true) }
    @Synchronized fun write(mode: AutoUpdateMode) {
        val bytes = JSONObject().put("version", 1).put("mode", mode.name).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        check(read() == AutoUpdatePolicy(mode))
    }
}
