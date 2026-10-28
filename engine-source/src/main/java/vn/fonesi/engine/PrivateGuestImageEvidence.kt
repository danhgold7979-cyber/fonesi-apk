package vn.fonesi.engine

import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.GuestImageRef

/**
 * Resolve guest code identity only from cryptographically re-audited private APK bytes.
 * This is evidence of stored code, never evidence of Android runtime or account isolation.
 */
class PrivateGuestImageEvidence(
    private val store: PrivateApkStore,
    private val platformApi: Int,
) {
    fun resolve(clusterId: String): GuestImageRef {
        val cluster = store.open(clusterId, platformApi)
        val base = cluster.apks.base
        return GuestImageRef(cluster.id, base.packageName, base.version, base.signers)
    }

    /** Failure to read an image is not a successful verification or a fallback to the original app. */
    fun matches(expected: GuestImageRef): Boolean = resolve(expected.clusterId) == expected
}
