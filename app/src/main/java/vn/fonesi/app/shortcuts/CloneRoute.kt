package vn.fonesi.app.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opaque per-creation identity; package names and engine IDs never come from intent extras. */
object CloneRoute {
    private val canonicalToken = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    fun validToken(token: String) = canonicalToken.matches(token)
    fun shortcutId(token: String): String {
        require(validToken(token))
        return "clone-$token"
    }
    fun tokenFromShortcutId(id: String): String? = id.removePrefix("clone-")
        .takeIf { id.startsWith("clone-") && validToken(it) }

    fun intent(context: Context, token: String): Intent {
        require(validToken(token))
        return Intent(context, CloneLaunchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "fonesi://clone/$token".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun parse(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        val token = data.lastPathSegment?.takeIf(::validToken) ?: return null
        // Exact canonical URI rejects encoded delimiters, extra paths, ports, queries and fragments.
        return token.takeIf { data.toString() == "fonesi://clone/$token" }
    }
}
