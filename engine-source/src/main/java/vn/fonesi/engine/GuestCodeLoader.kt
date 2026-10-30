package vn.fonesi.engine

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Build
import android.os.Looper
import android.os.Process
import android.system.Os
import android.system.OsConstants
import dalvik.system.DexClassLoader
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.apk.PrivateApkComponentCatalog
import vn.fonesi.apk.VerifiedPrivateComponentInventory
import vn.fonesi.apk.ExplicitActivityCaller
import vn.fonesi.apk.VerifiedPrivateActivitySelection
import vn.fonesi.apk.SignedPrivateExplicitActivityResolver
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Does not attach Application/Activity, provide a guest Context or virtualize Binder/components. */
data class LoadedGuestCode(val clusterId: String, val packageName: String, val version: Long,
    val classLoader: ClassLoader, val resources: Resources,
    val componentInventory: VerifiedPrivateComponentInventory) {
    /** Read-only signed metadata selection. No Android Activity dispatch or permission mediation. */
    fun selectExplicitActivity(
        className: String,
        caller: ExplicitActivityCaller,
    ): VerifiedPrivateActivitySelection {
        check(componentInventory.clusterId == clusterId && componentInventory.packageName == packageName &&
            componentInventory.versionCode == version) {
            "Loaded private code and component manifest have different identity"
        }
        return SignedPrivateExplicitActivityResolver.select(componentInventory, packageName, className, caller)
    }

    /** Exact signed PRIVATE Application class; never initialize, attach or call callbacks. */
    fun preflightApplicationClass(): PrivateGuestApplicationClassHandle {
        check(componentInventory.clusterId == clusterId &&
            componentInventory.packageName == packageName &&
            componentInventory.versionCode == version) {
            "Loaded private code and signed Application manifest have different identity"
        }
        val signedName = requireNotNull(componentInventory.applicationClass) {
            "Signed PRIVATE base has no explicit Application class"
        }
        val loaded = PrivateGuestApplicationClassPreflight.resolve(
            signedName, classLoader, Application::class.java)
        @Suppress("UNCHECKED_CAST")
        return PrivateGuestApplicationClassHandle(signedName, loaded as Class<out Application>)
    }

    /**
     * Class ownership/type preflight only. Never instantiate/attach the Activity or return
     * a launch-success result; actual Android lifecycle and guest UID mediation are absent.
     */
    fun preflightExplicitActivityClass(
        className: String,
        caller: ExplicitActivityCaller,
    ): PrivateGuestActivityClassHandle {
        val signed = selectExplicitActivity(className, caller)
        val loaded = PrivateGuestActivityClassPreflight.resolve(
            signed, classLoader, Activity::class.java)
        @Suppress("UNCHECKED_CAST")
        val type = loaded as Class<out Activity>
        return PrivateGuestActivityClassHandle(signed, type)
    }
}

/** Signed class proof only; not Android Application registration or execution. */
data class PrivateGuestApplicationClassHandle(
    val signedName: String,
    val applicationClass: Class<out Application>,
)

/** Class type proof only; NOT an Android guest execution/permission token. */
data class PrivateGuestActivityClassHandle(
    val selection: VerifiedPrivateActivitySelection,
    val activityClass: Class<out Activity>,
)

/**
 * Opt-in code-image component. Resources cover private APK assets; custom resource classloader
 * binding and component metadata still require the future Android context/lifecycle layer.
 */
class GuestCodeLoader(context: Context, private val apks: PrivateApkStore) {
    private val host = context.applicationContext as? Application ?: error("Host Application context required")

    fun load(clusterId: String): LoadedGuestCode {
        NativeRuntime.requireGuestProcess(host)
        check(Looper.myLooper() != Looper.getMainLooper()) { "Guest code preparation must run off the UI thread" }
        check(NativeRuntime.state() == NativeRuntime.ENABLED && Process.is64Bit()) { "Native guest routing must be enabled first" }
        check(attempted.compareAndSet(false, true)) { "One code image attempt per process; use a new process after failure" }
        // Failed attempts deliberately keep attempted=true. No process-level hot swap or fallback.
        val owned = host.dataDir.canonicalFile
        val layout = GuestCodeLayout.read(apks, clusterId, Build.VERSION.SDK_INT, owned, host.packageName)
        val components = PrivateGuestComponentPreflight.requireMatches(layout,
            PrivateApkComponentCatalog.inspect(apks, clusterId, Build.VERSION.SDK_INT))
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE).also { check(it in setOf(4096L, 16384L)) }.toInt()
        val abi = Build.SUPPORTED_64_BIT_ABIS.firstOrNull().also { check(it == "arm64-v8a" || it == "x86_64") }!!
        val nativeRoot = privateDirectory(File(host.noBackupFilesDir, "source-native"), owned)
        val preparer = NativeLibraryPreparer(apks, nativeRoot.toPath())
        val native = preparer.prepare(clusterId, abi, pageSize, Build.VERSION.SDK_INT)
        val cache = privateDirectory(File(host.codeCacheDir, "source-dex/$clusterId"), owned)
        val parent = host.classLoader.parent ?: error("Framework class loader unavailable")
        val loader = DexClassLoader(layout.allApks.joinToString(File.pathSeparator) { it.path }, cache.path, native.directory.toString(), parent)
        @Suppress("DEPRECATION")
        val archive = host.packageManager.getPackageArchiveInfo(layout.base.path, 0) ?: error("Private base manifest could not be parsed")
        check(archive.packageName == layout.cluster.apks.base.packageName)
        val info = ApplicationInfo(checkNotNull(archive.applicationInfo)).apply {
            uid = Process.myUid()
            sourceDir = layout.base.path; publicSourceDir = layout.base.path
            splitNames = layout.splitNames.toTypedArray()
            val splitPaths = layout.splits.map { it.path }.toTypedArray()
            splitSourceDirs = splitPaths
            splitPublicSourceDirs = splitPaths.copyOf()
            nativeLibraryDir = native.directory.toString()
        }
        val resources = host.packageManager.getResourcesForApplication(info)
        check(apks.open(clusterId, Build.VERSION.SDK_INT) == layout.cluster && preparer.verify(native)) { "Code image changed during loading" }
        check(PrivateGuestComponentPreflight.requireMatches(layout,
            PrivateApkComponentCatalog.inspect(apks, clusterId, Build.VERSION.SDK_INT)) == components) {
            "Private component manifest changed during guest code preparation"
        }
        check(NativeRuntime.state() == NativeRuntime.ENABLED)
        return LoadedGuestCode(clusterId, info.packageName, layout.cluster.apks.base.version,
            loader, resources, components)
    }

    private fun privateDirectory(requested: File, owned: File): File {
        val file = requested.canonicalFile
        check(file.path.startsWith(owned.path + File.separator)) { "Code output must stay inside host private data" }
        check(file.mkdirs() || file.isDirectory)
        check(file.canonicalFile == file)
        return file
    }

    private companion object { val attempted = AtomicBoolean(false) }
}
