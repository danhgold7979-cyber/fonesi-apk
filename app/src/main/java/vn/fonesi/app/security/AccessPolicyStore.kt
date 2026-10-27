package vn.fonesi.app.security

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

data class AccessPolicy(val required: Boolean, val unreadable: Boolean = false)

/** No credential/session is saved. Corrupt/unknown state requires authentication to repair. */
class AccessPolicyStore(context: Context) {
    private val base = File(context.noBackupFilesDir, "access-policy.json")
    private val file = AtomicFile(base)
    @Synchronized fun read(): AccessPolicy {
        return try {
            val parent = checkNotNull(base.parentFile)
            check(parent.isDirectory && parent.canRead() && parent.canExecute())
            if (!base.exists() && !File(base.path + ".bak").exists()) return AccessPolicy(false)
            check(base.length() <= 1024 && File(base.path + ".bak").length() <= 1024)
            val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
            check(json.keys().asSequence().toSet() == setOf("version", "required"))
            check(json.get("version") == 1 && json.get("required") is Boolean)
            AccessPolicy(json.getBoolean("required"))
        } catch (_: Exception) { AccessPolicy(true, unreadable = true) }
    }
    @Synchronized fun write(required: Boolean) {
        val content = JSONObject().put("version", 1).put("required", required).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(content); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        check(read() == AccessPolicy(required))
    }
}
