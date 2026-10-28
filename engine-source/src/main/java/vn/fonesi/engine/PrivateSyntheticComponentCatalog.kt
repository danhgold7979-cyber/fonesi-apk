package vn.fonesi.engine

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.Process
import vn.fonesi.apk.PrivateApkStore
import java.io.File

/**
 * Result of manifest-only inspection of a project-owned, signed PRIVATE single-APK image.
 * This is not a PackageManager registration, component dispatcher, security sandbox,
 * guest Activity instance or evidence of third-party app compatibility.
 */
data class VerifiedPrivateSyntheticComponents(
    val clusterId: String,
    val packageName: String,
    val version: Long,
    val applicationName: String,
    val activityName: String,
    val activityExported: Boolean,
    val privatePath: String,
)

/**
 * E25 TEST ONLY: a fail-closed private APK component-catalog preflight.
 *
 * Reject any source other than the exact signed project-controlled payload fixture.
 * Never ask PackageManager about the installed package name; inspect the verified PRIVATE
 * base path using getPackageArchiveInfo(), then cryptographically re-open the snapshot.
 */
class PrivateSyntheticComponentCatalog(context: Context, private val store: PrivateApkStore) {
    private val host = context.applicationContext as? Application
        ?: error("E25 catalog requires host Application")

    @Suppress("DEPRECATION")
    fun inspect(clusterId: String): VerifiedPrivateSyntheticComponents {
        check(host.packageName == HOST_PACKAGE) { "Only the synthetic probe host may inspect a guest" }
        check(Build.VERSION.SDK_INT >= 28 &&
            Application.getProcessName() == "$HOST_PACKAGE:fonesi_guest_ui") {
            "E25 catalog runs only in the dedicated private guest UI process"
        }
        NativeRuntime.requireGuestProcess(host)
        check(NativeRuntime.state() == NativeRuntime.ENABLED && Process.is64Bit()) {
            "Source native routing must be enabled before catalog preflight"
        }
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Do signed private APK parsing off the main/UI thread"
        }
        val owned = host.dataDir.canonicalFile
        val layout = GuestCodeLayout.read(
            store, clusterId, Build.VERSION.SDK_INT, owned, host.packageName
        )
        val cluster = layout.cluster
        check(layout.splits.isEmpty() && cluster.apks.splits.isEmpty()) {
            "E25 synthetic catalog requires exactly one signed private base APK"
        }
        val base = cluster.apks.base
        check(base.packageName == EXPECTED_PACKAGE && base.version == 1L && !base.isolatedSplits)
        check(base.signers.size == 1 && base.signers.all { it.matches(Regex("[a-f0-9]{64}")) })
        check(layout.base.canonicalFile == layout.base &&
            layout.base.path.startsWith(owned.path + File.separator)) {
            "Private manifest lookup may not use installed guest code"
        }

        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
            PackageManager.GET_PERMISSIONS
        val metadata = host.packageManager.getPackageArchiveInfo(layout.base.path, flags)
            ?: error("Private signed synthetic AndroidManifest could not be read")
        check(metadata.packageName == EXPECTED_PACKAGE &&
            (if (Build.VERSION.SDK_INT >= 28) metadata.longVersionCode
             else metadata.versionCode.toLong()) == base.version) {
            "Archive package or version identity differs from cryptographically signed private bytes"
        }
        val application = checkNotNull(metadata.applicationInfo) {
            "Private archive lacks Application metadata"
        }
        check(application.packageName == EXPECTED_PACKAGE &&
            normalized(application.name) == "$EXPECTED_PACKAGE.SyntheticGuestApplication" &&
            application.enabled) {
            "Synthetic signed private Application manifest identity is not approved"
        }

        // Activity can be exported in the signed fixture because this is also the separately
        // installed test app. An exported guest manifest declaration is NOT an Android
        // host-proxy permission or evidence that the guest component was registered here.
        val activities = metadata.activities.orEmpty()
        check(activities.size == 1) { "Only one synthetic guest Activity may appear in this fixture" }
        val activity = activities.single()
        check(activity.packageName == EXPECTED_PACKAGE &&
            normalized(activity.name) == "$EXPECTED_PACKAGE.PayloadActivity" &&
            activity.enabled && activity.exported &&
            activity.permission.isNullOrEmpty()) {
            "Unrecognized, disabled or protected guest Activity metadata"
        }
        check(metadata.services.isNullOrEmpty() && metadata.receivers.isNullOrEmpty() &&
            metadata.providers.isNullOrEmpty() && metadata.requestedPermissions.isNullOrEmpty()) {
            "Synthetic manifest unexpectedly declares a Service, Receiver, Provider or permission"
        }
        check(store.open(clusterId, Build.VERSION.SDK_INT) == cluster) {
            "Signed private APK snapshot changed while reading component metadata"
        }
        check(NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Native routing changed during synthetic component catalog inspection"
        }
        return VerifiedPrivateSyntheticComponents(
            clusterId, EXPECTED_PACKAGE, base.version,
            "$EXPECTED_PACKAGE.SyntheticGuestApplication",
            "$EXPECTED_PACKAGE.PayloadActivity",
            true,
            layout.base.path,
        )
    }

    private fun normalized(raw: String?): String {
        require(!raw.isNullOrBlank() && raw.length <= 255) { "Missing/unsafe guest component name" }
        return when {
            raw.startsWith(".") -> EXPECTED_PACKAGE + raw
            "." !in raw -> "$EXPECTED_PACKAGE.$raw"
            else -> raw
        }
    }

    private companion object {
        const val HOST_PACKAGE = "vn.fonesi.nativeprobe"
        const val EXPECTED_PACKAGE = "vn.fonesi.payload"
    }
}
