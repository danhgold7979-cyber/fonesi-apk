package vn.fonesi.engine

import android.app.Application
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
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * E21 TEST ONLY: two project-owned signed, isolated base/feature APKs.
 *
 * This is NOT an engine provider and must never accept arbitrary apps or user account data.
 * It does not register guest components, grant new UID boundaries, or change SpaceCore.
 * Failed attempts are deliberately not retried inside a live guest-named process.
 */
class IsolatedSplitCodeProbeLoader(private val context: Context, private val store: PrivateApkStore) {
    private val host = context.applicationContext as? Application
        ?: error("Synthetic split probe requires host Application")

    fun verify(clusterId: String, nonce: String) {
        require(host.packageName == HOST_PACKAGE) { "Synthetic split probe host only" }
        // Explicit control-flow guard: getProcessName() was added at API 28.
        // Keep minSdk 26 and refuse this synthetic proof on older Android.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            error("Test process identity requires Android 28+")
        }
        check(Application.getProcessName() == "$HOST_PACKAGE:fonesi_guest_split") {
            "Isolated synthetic split code must use its dedicated process"
        }
        NativeRuntime.requireGuestProcess(host)
        check(Looper.myLooper() != Looper.getMainLooper()) { "Never execute private DEX on main thread" }
        check(Process.is64Bit() && NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Synthetic native routing must be enabled before private code"
        }
        require(NONCE.matches(nonce)) { "Invalid challenge" }
        check(attempted.compareAndSet(false, true)) { "One split probe attempt per process" }

        val owned = host.dataDir.canonicalFile
        val layout = GuestCodeLayout.readForGraphOnly(
            store, clusterId, Build.VERSION.SDK_INT, owned, host.packageName
        )
        val graph = GuestSplitGraphPlanner.plan(layout)
        check(layout.cluster.apks.base.packageName == PACKAGE && layout.cluster.apks.base.version == 1L)
        check(layout.cluster.apks.base.isolatedSplits && graph.isolated)
        check(layout.cluster.apks.splits.size == 1 && graph.nodes.size == 2)
        val base = graph.node(GuestSplitGraphPlanner.BASE)
        val feature = graph.node(FEATURE)
        check(base.parentId == null && feature.parentId == base.id && feature.isFeature)
        check(base.apks.size == 1 && feature.apks.size == 1)
        check(base.apks.single() == layout.base && graph.allApks.toSet() == layout.allApks.toSet())
        check(layout.cluster.apks.splits.single().feature)
        check(graph.allApks.all { it.canonicalFile == it && it.path.startsWith(owned.path + File.separator) })

        val abi = Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: error("No 64-bit guest ABI")
        check(abi == "x86_64" || abi == "arm64-v8a")
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE).toInt()
        check(pageSize == 4096 || pageSize == 16384)
        val nativeRoot = ownedDirectory(File(host.noBackupFilesDir, "e21-native"), owned)
        val preparer = NativeLibraryPreparer(store, nativeRoot.toPath())
        val native = preparer.prepare(clusterId, abi, pageSize, Build.VERSION.SDK_INT)
        val framework = host.classLoader.parent ?: error("Framework classloader unavailable")
        val baseCache = ownedDirectory(File(host.codeCacheDir, "e21-$clusterId-base"), owned)
        val featureCache = ownedDirectory(File(host.codeCacheDir, "e21-$clusterId-feature"), owned)
        val baseLoader = DexClassLoader(base.apks.single().path, baseCache.path,
            native.directory.toString(), framework)
        val featureLoader = DexClassLoader(feature.apks.single().path, featureCache.path,
            native.directory.toString(), baseLoader)
        val baseName = "$PACKAGE.base.BaseEntrypoint"
        val featureName = "$PACKAGE.feature.FeatureEntrypoint"
        checkAbsent(host.classLoader, baseName)
        checkAbsent(host.classLoader, featureName)
        checkAbsent(baseLoader, featureName)
        val baseClass = baseLoader.loadClass(baseName)
        val featureClass = featureLoader.loadClass(featureName)
        check(baseClass.classLoader === baseLoader && featureClass.classLoader === featureLoader) {
            "Signed base and feature bytecode must originate from their separate private classloaders"
        }
        check(featureLoader.loadClass(baseName) === baseClass) {
            "Feature classloader must inherit the private base, not define a second copy"
        }
        val baseReply = baseClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val featureReply = featureClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        check(baseReply == digest("FONESI-E21-BASE:$nonce"))
        check(featureReply == digest("FONESI-E21-FEATURE:$nonce"))
        check(baseReply != featureReply)

        // Public PackageManager API, with *only* verified, host-private APK paths.
        // No getResourcesForApplication(PACKAGE) or installed APK fallback is allowed.
        @Suppress("DEPRECATION")
        val archive = host.packageManager.getPackageArchiveInfo(layout.base.path, 0)
            ?: error("Private signed base manifest not readable")
        check(archive.packageName == PACKAGE)
        val originalInfo = checkNotNull(archive.applicationInfo)
        fun resourceSet(includeFeature: Boolean): Resources {
            val info = ApplicationInfo(originalInfo).apply {
                uid = Process.myUid()
                sourceDir = base.apks.single().path
                publicSourceDir = base.apks.single().path
                val paths = if (includeFeature) arrayOf(feature.apks.single().path) else emptyArray()
                splitNames = if (includeFeature) arrayOf(FEATURE) else emptyArray()
                splitSourceDirs = paths
                splitPublicSourceDirs = paths.copyOf()
                nativeLibraryDir = native.directory.toString()
            }
            check(info.sourceDir == layout.base.path)
            check(info.splitSourceDirs.orEmpty().all { path ->
                graph.allApks.any { it.path == path }
            })
            return host.packageManager.getResourcesForApplication(info)
        }
        val onlyBase = resourceSet(false)
        val withFeature = resourceSet(true)
        checkMarker(onlyBase, "fonesi_base_marker", "Fonesi signed isolated split base")
        check(onlyBase.getIdentifier("fonesi_feature_marker", "string", PACKAGE) == 0) {
            "Base-only private Resources must not implicitly load an installed feature"
        }
        checkMarker(withFeature, "fonesi_base_marker", "Fonesi signed isolated split base")
        checkMarker(withFeature, "fonesi_feature_marker", "Fonesi signed isolated split feature")

        check(store.open(clusterId, Build.VERSION.SDK_INT) == layout.cluster) {
            "Signed private APK cluster changed during split code/resource execution"
        }
        check(preparer.verify(native) && NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Synthetic native image changed or routing stopped"
        }
    }

    private fun checkMarker(resources: Resources, name: String, expected: String) {
        val id = resources.getIdentifier(name, "string", PACKAGE)
        check(id != 0 && resources.getString(id) == expected) {
            "Signed private split Resources marker mismatch: $name"
        }
    }

    private fun checkAbsent(loader: ClassLoader, name: String) {
        try {
            loader.loadClass(name)
            error("Class $name must not be visible in this namespace")
        } catch (_: ClassNotFoundException) {
            // A genuine ClassNotFoundException is the only accepted outcome.
        }
    }

    private fun ownedDirectory(requested: File, owned: File): File {
        val absolute = requested.canonicalFile
        check(absolute.path.startsWith(owned.path + File.separator))
        check(absolute.mkdirs() || absolute.isDirectory)
        check(absolute.canonicalFile == absolute)
        return absolute
    }

    private fun digest(message: String): String =
        MessageDigest.getInstance("SHA-256").digest(message.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val HOST_PACKAGE = "vn.fonesi.nativeprobe"
        const val PACKAGE = "vn.fonesi.splitfixture"
        const val FEATURE = "feature.alpha"
        val NONCE = Regex("[0-9a-f]{32}")
        val attempted = AtomicBoolean(false)
    }
}
