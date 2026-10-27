package vn.fonesi.app.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground grants exist only in this process and are invalidated on background/policy changes. */
class AccessBroker internal constructor(context: Context) {
    val policy = AccessPolicyStore(context)
    private val grants = mutableSetOf<String>()
    private val visible = mutableSetOf<String>()
    @Synchronized fun visible(owner: String, value: Boolean) { if (value) visible += owner else visible -= owner }
    @Synchronized fun leave(owner: String) { visible -= owner; grants -= owner }
    @Synchronized fun hasForegroundAccess(): Boolean = visible.isNotEmpty() && hasAccess()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    @Synchronized fun changePolicy(required: Boolean) {
        policy.write(required)
        grants.clear()
        mutableRevision.value++
    }
    @Synchronized fun grant(owner: String) { grants += owner }
    @Synchronized fun revoke(owner: String) { grants -= owner }
    @Synchronized fun revokeAll() { grants.clear() }
    @Synchronized fun hasAccess(): Boolean = !policy.read().required || grants.isNotEmpty()
    companion object {
        @Volatile private var instance: AccessBroker? = null
        fun get(context: Context): AccessBroker = instance ?: synchronized(this) {
            instance ?: AccessBroker(context.applicationContext).also { instance = it }
        }
    }
}
