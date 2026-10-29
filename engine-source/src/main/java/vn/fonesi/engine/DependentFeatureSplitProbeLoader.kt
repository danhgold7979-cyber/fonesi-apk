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
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import vn.fonesi.apk.PrivateApkStore

/**
 * E22 SYNTHETIC PROBE ONLY. Base -> feature.alpha -> feature.beta, with a real
 * beta method reference to alpha's DEX class. NOT a production app/provider/UID sandbox.
 */
class DependentFeatureSplitProbeLoader(
    context: Context,
    private val apks: PrivateApkStore,
) {
    private val host = context.applicationContext as? Application
        ?: error("Synthetic split proof requires host Application")

    fun verify(clusterId: String, nonce: String) {
        require(host.packageName == HOST) { "Only Fonesi synthetic host is eligible" }
        check(Build.VERSION.SDK_INT >= 28 &&
            Application.getProcessName() == "$HOST:fonesi_guest_split") {
            "E22 only runs in the dedicated guest-named test process"
        }
        NativeRuntime.requireGuestProcess(host)
        check(Looper.myLooper() != Looper.getMainLooper()) { "Private DEX must load off main thread" }
        check(Process.is64Bit() && NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Native routing must be enabled before the private code proof"
        }
        require(Regex("[0-9a-f]{32}").matches(nonce))
        check(attempted.compareAndSet(false, true)) { "Only one code load attempt per process" }

        val owned = host.dataDir.canonicalFile
        val layout = GuestCodeLayout.readForGraphOnly(
            apks, clusterId, Build.VERSION.SDK_INT, owned, host.packageName
        )
        val set = layout.cluster.apks
        check(set.base.packageName == PACKAGE && set.base.version == 1L && set.base.isolatedSplits)
        check(set.splits.size == 2 && set.splits.all { it.feature } &&
            set.splits.map { it.splitName }.toSet() == setOf(ALPHA, BETA))
        val graph = GuestSplitGraphPlanner.plan(layout)
        check(graph.isolated && graph.nodes.size == 3 && graph.allApks.size == 3)
        val root = graph.node(GuestSplitGraphPlanner.BASE)
        val alpha = graph.node(ALPHA)
        val beta = graph.node(BETA)
        check(root.parentId == null && !root.isFeature && root.apks.size == 1)
        check(alpha.parentId == root.id && alpha.isFeature && alpha.apks.size == 1)
        check(beta.parentId == alpha.id && beta.isFeature && beta.apks.size == 1)
        check(graph.nodes.map { it.id } == listOf(root.id, ALPHA, BETA)) {
            "Invalid private feature dependency order"
        }
        check(graph.allApks.toSet() == layout.allApks.toSet() &&
            graph.allApks.all { it.canonicalFile == it && it.path.startsWith(owned.path + File.separator) })

        val abi = Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: error("No 64-bit ABI")
        check(abi == "arm64-v8a" || abi == "x86_64")
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE).toInt()
        check(pageSize == 4096 || pageSize == 16384)
        val nativeRoot = ownedDirectory(File(host.noBackupFilesDir, "e22-native"), owned)
        val preparer = NativeLibraryPreparer(apks, nativeRoot.toPath())
        val native = preparer.prepare(clusterId, abi, pageSize, Build.VERSION.SDK_INT)
        val framework = host.classLoader.parent ?: error("Framework loader missing")
        fun loader(node: GuestSplitLoaderNode, parent: ClassLoader): DexClassLoader =
            DexClassLoader(node.apks.single().path,
                ownedDirectory(File(host.codeCacheDir, "e22-$clusterId-" + node.id), owned).path,
                native.directory.toString(), parent)
        val rootLoader = loader(root, framework)
        val alphaLoader = loader(alpha, rootLoader)
        val betaLoader = loader(beta, alphaLoader)
        val rootName = "$PACKAGE.base.BaseEntrypoint"
        val alphaName = "$PACKAGE.feature.FeatureEntrypoint"
        val betaName = "$PACKAGE.dependent.DependentEntrypoint"
        for (name in listOf(rootName, alphaName, betaName)) checkAbsent(host.classLoader, name)
        checkAbsent(rootLoader, alphaName)
        checkAbsent(rootLoader, betaName)
        checkAbsent(alphaLoader, betaName)
        val rootClass = rootLoader.loadClass(rootName)
        val alphaClass = alphaLoader.loadClass(alphaName)
        val betaClass = betaLoader.loadClass(betaName)
        check(rootClass.classLoader === rootLoader && alphaClass.classLoader === alphaLoader &&
            betaClass.classLoader === betaLoader)
        check(betaLoader.loadClass(alphaName) === alphaClass &&
            betaLoader.loadClass(rootName) === rootClass &&
            alphaLoader.loadClass(rootName) === rootClass)
        val rootReply = rootClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val alphaReply = alphaClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val betaReply = betaClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val alphaDigest = digest("FONESI-E21-FEATURE:$nonce")
        check(rootReply == digest("FONESI-E21-BASE:$nonce"))
        check(alphaReply == alphaDigest)
        check(betaReply == alphaDigest + ":" + digest("FONESI-E22-BETA:$nonce")) {
            "Beta did not execute alpha method through the verified private parent classloader"
        }

        @Suppress("DEPRECATION")
        val archive = host.packageManager.getPackageArchiveInfo(layout.base.path, 0)
            ?: error("Private base manifest unreadable")
        check(archive.packageName == PACKAGE)
        val original = checkNotNull(archive.applicationInfo)
        fun privateResources(names: List<String>): Resources {
            val files = names.map { graph.node(it).apks.single() }
            val info = ApplicationInfo(original).apply {
                uid = Process.myUid()
                sourceDir = root.apks.single().path
                publicSourceDir = root.apks.single().path
                splitNames = names.toTypedArray()
                val paths = files.map { it.path }.toTypedArray()
                splitSourceDirs = paths
                splitPublicSourceDirs = paths.copyOf()
                nativeLibraryDir = native.directory.toString()
            }
            check(info.sourceDir == layout.base.path && files.all { it in graph.allApks })
            return host.packageManager.getResourcesForApplication(info)
        }
        val onlyBase = privateResources(emptyList())
        val withAlpha = privateResources(listOf(ALPHA))
        val withBeta = privateResources(listOf(ALPHA, BETA))
        checkMarker(onlyBase, "fonesi_base_marker", "Fonesi signed isolated split base")
        check(onlyBase.getIdentifier("fonesi_feature_marker", "string", PACKAGE) == 0 &&
            onlyBase.getIdentifier("fonesi_beta_marker", "string", PACKAGE) == 0)
        checkMarker(withAlpha, "fonesi_feature_marker", "Fonesi signed isolated split feature")
        check(withAlpha.getIdentifier("fonesi_beta_marker", "string", PACKAGE) == 0)
        checkMarker(withBeta, "fonesi_base_marker", "Fonesi signed isolated split base")
        checkMarker(withBeta, "fonesi_feature_marker", "Fonesi signed isolated split feature")
        checkMarker(withBeta, "fonesi_beta_marker", "Fonesi signed dependent beta feature")
        check(apks.open(clusterId, Build.VERSION.SDK_INT) == layout.cluster &&
            preparer.verify(native) && NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Signed private APK/native image changed during dependent execution"
        }
    }

    private fun checkMarker(resources: Resources, name: String, expected: String) {
        val id = resources.getIdentifier(name, "string", PACKAGE)
        check(id != 0 && resources.getString(id) == expected) {
            "Private synthetic resource mismatch: $name"
        }
    }
    private fun checkAbsent(loader: ClassLoader, name: String) {
        try { loader.loadClass(name); error("Unexpected parent/host DEX class exposure: $name") }
        catch (_: ClassNotFoundException) { /* Only an actual missing DEX is accepted. */ }
    }
    private fun ownedDirectory(request: File, owned: File): File {
        val file = request.canonicalFile
        check(file.path.startsWith(owned.path + File.separator))
        check(file.mkdirs() || file.isDirectory)
        check(file.canonicalFile == file)
        return file
    }
    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val HOST = "vn.fonesi.nativeprobe"
        const val PACKAGE = "vn.fonesi.splitfixture"
        const val ALPHA = "feature.alpha"
        const val BETA = "feature.beta"
        val attempted = AtomicBoolean(false)
    }
}
