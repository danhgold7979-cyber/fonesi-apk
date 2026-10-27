package vn.fonesi.core

/** UI access only, not guest encryption. State is never persisted as an authenticated session. */
class AccessSession(required: Boolean, private val clock: () -> Long) {
    private var required = required
    private var foreground = false
    private var authenticated = false
    private var sequence = 0L
    private var pending: Pair<Long, Long>? = null
    val authorized get() = foreground && (!required || authenticated)
    val authenticating get() = pending != null
    val requiresAuthentication get() = required
    val isForeground get() = foreground
    fun syncPolicy(required: Boolean) {
        if (this.required != required) { authenticated = false; pending = null }
        this.required = required
    }

    fun foreground(required: Boolean) {
        if (!foreground) authenticated = false
        foreground = true
        syncPolicy(required)
    }
    fun background(rotating: Boolean, externalCredential: Boolean = false) {
        if (rotating) return
        foreground = false
        authenticated = false
        if (!externalCredential) pending = null
    }
    fun begin(): Long? {
        if (!foreground || pending != null) return null
        return (++sequence).also { pending = it to clock() }
    }
    fun complete(id: Long, success: Boolean): Boolean {
        val current = pending ?: return false
        if (id != current.first) return false
        pending = null
        val elapsed = clock() - current.second
        val accepted = success && foreground && elapsed in 0..120_000
        authenticated = accepted
        return accepted
    }
    /** Called only after a successful current authentication AND a durable policy write. */
    fun policyAfterAuthentication(required: Boolean) { this.required = required; authenticated = true }
}
