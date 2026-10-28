package vn.fonesi.engine

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import vn.fonesi.apk.PrivateApkStore

/**
 * E23 TEST ONLY: one signed alpha-targeted, resources-only French config APK.
 * This cannot virtualize third-party application Android components or separate UIDs.
 */
class ConfigurationSplitProbeLoader(context: Context, private val apks: PrivateApkStore) {
    private val host = context.applicationContext as? Application
        ?: error("E23 requires the project-owned host Application")

    fun verify(clusterId: String, nonce: String) {
        require(host.packageName == HOST_PACKAGE)
        check(Build.VERSION.SDK_INT >= 28 &&
            Application.getProcessName() == "$HOST_PACKAGE:fonesi_guest_split")
        NativeRuntime.requireGuestProcess(host)
        check(Looper.myLooper() != Looper.getMainLooper()) { "Cannot load private DEX on UI thread" }
        check(Process.is64Bit() && NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Native file routing must be enabled first"
        }
        require(NONCE.matches(nonce))
        check(attempted.compareAndSet(false, true)) { "One guest code attempt per test process" }

        val owned = host.dataDir.canonicalFile
        val layout = GuestCodeLayout.readForGraphOnly(
            apks, clusterId, Build.VERSION.SDK_INT, owned, host.packageName
        )
        val set = layout.cluster.apks
        check(set.base.packageName == PACKAGE && set.base.version == 1L && set.base.isolatedSplits)
        check(set.splits.size == 3 && set.splits.map { it.splitName }.toSet() ==
            setOf(ALPHA, BETA, CONFIG))
        val alphaMember = set.splits.single { it.splitName == ALPHA }
        val betaMember = set.splits.single { it.splitName == BETA }
        val configMember = set.splits.single { it.splitName == CONFIG }
        check(alphaMember.feature && betaMember.feature && !configMember.feature &&
            configMember.configFor == ALPHA &&
            betaMember.dependencies == setOf(ALPHA) && alphaMember.dependencies.isEmpty() &&
            configMember.dependencies.isEmpty())
        ZipFile(configMember.file).use { zip ->
            check(zip.getEntry("resources.arsc") != null &&
                zip.getEntry("classes.dex") == null &&
                zip.entries().asSequence().none { it.name.startsWith("lib/") })
        }
        val graph = GuestSplitGraphPlanner.plan(layout)
        check(graph.isolated && graph.nodes.size == 3 && graph.allApks.size == 4)
        val root = graph.node(GuestSplitGraphPlanner.BASE)
        val alpha = graph.node(ALPHA)
        val beta = graph.node(BETA)
        check(root.parentId == null && root.apks.size == 1 && !root.isFeature)
        check(alpha.parentId == root.id && alpha.isFeature && alpha.apks.size == 2)
        check(beta.parentId == alpha.id && beta.isFeature && beta.apks.size == 1)
        check(alpha.apks == listOf(alphaMember.file, configMember.file)) {
            "Signed alpha configuration split was not scoped to its owning feature node"
        }
        check(graph.nodes.map { it.id } == listOf(root.id, ALPHA, BETA) &&
            graph.allApks.toSet() == layout.allApks.toSet() &&
            graph.allApks.all { it.canonicalFile == it &&
                it.path.startsWith(owned.path + File.separator) })

        val abi = Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: error("No 64-bit ABI")
        check(abi == "x86_64" || abi == "arm64-v8a")
        val page = Os.sysconf(OsConstants._SC_PAGESIZE).toInt()
        check(page == 4096 || page == 16384)
        val preparer = NativeLibraryPreparer(
            apks, hostOwnedDirectory(File(host.noBackupFilesDir, "e23-native"), owned).toPath()
        )
        val native = preparer.prepare(clusterId, abi, page, Build.VERSION.SDK_INT)
        val framework = host.classLoader.parent ?: error("No Android framework classloader")
        fun loader(node: GuestSplitLoaderNode, parent: ClassLoader) =
            DexClassLoader(node.apks.joinToString(File.pathSeparator) { it.path },
                hostOwnedDirectory(File(host.codeCacheDir, "e23-$clusterId-" + node.id), owned).path,
                native.directory.toString(), parent)
        val baseLoader = loader(root, framework)
        val alphaLoader = loader(alpha, baseLoader)
        val betaLoader = loader(beta, alphaLoader)
        val baseName = "$PACKAGE.base.BaseEntrypoint"
        val alphaName = "$PACKAGE.feature.FeatureEntrypoint"
        val betaName = "$PACKAGE.dependent.DependentEntrypoint"
        for (name in listOf(baseName, alphaName, betaName)) requireAbsent(host.classLoader, name)
        requireAbsent(baseLoader, alphaName)
        requireAbsent(baseLoader, betaName)
        requireAbsent(alphaLoader, betaName)
        val baseClass = baseLoader.loadClass(baseName)
        val alphaClass = alphaLoader.loadClass(alphaName)
        val betaClass = betaLoader.loadClass(betaName)
        check(baseClass.classLoader === baseLoader &&
            alphaClass.classLoader === alphaLoader &&
            betaClass.classLoader === betaLoader &&
            betaLoader.loadClass(baseName) === baseClass &&
            betaLoader.loadClass(alphaName) === alphaClass)
        val baseResult = baseClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val alphaResult = alphaClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val betaResult = betaClass.getMethod("challenge", String::class.java).invoke(null, nonce)
        val alphaDigest = sha("FONESI-E21-FEATURE:$nonce")
        check(baseResult == sha("FONESI-E21-BASE:$nonce") &&
            alphaResult == alphaDigest &&
            betaResult == alphaDigest + ":" + sha("FONESI-E22-BETA:$nonce"))

        @Suppress("DEPRECATION")
        val archive = host.packageManager.getPackageArchiveInfo(layout.base.path, 0)
            ?: error("Signed private base manifest unavailable")
        check(archive.packageName == PACKAGE)
        val original = checkNotNull(archive.applicationInfo)
        fun resources(includeConfig: Boolean): Resources {
            val names = if (includeConfig) listOf(ALPHA, CONFIG, BETA) else listOf(ALPHA, BETA)
            val members = names.map { name ->
                when (name) {
                    ALPHA -> alphaMember.file
                    BETA -> betaMember.file
                    CONFIG -> configMember.file
                    else -> error("Untrusted config name")
                }
            }
            val info = ApplicationInfo(original).apply {
                uid = Process.myUid()
                sourceDir = root.apks.single().path
                publicSourceDir = root.apks.single().path
                splitNames = names.toTypedArray()
                val paths = members.map { it.path }.toTypedArray()
                splitSourceDirs = paths
                splitPublicSourceDirs = paths.copyOf()
                nativeLibraryDir = native.directory.toString()
            }
            check(members.all { it in graph.allApks } && info.sourceDir == layout.base.path)
            return host.packageManager.getResourcesForApplication(info)
        }
        val noConfig = resources(false)
        val config = resources(true)
        fun translated(source: Resources, locale: Locale): String {
            val configuration = Configuration(source.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            val localized = Resources(source.assets, source.displayMetrics, configuration)
            val id = localized.getIdentifier("fonesi_feature_marker", "string", PACKAGE)
            check(id != 0) { "Private alpha feature resource ID disappeared" }
            return localized.getString(id)
        }
        val en = "Fonesi signed isolated split feature"
        val fr = "Fonesi signed alpha French config"
        check(translated(noConfig, Locale.ENGLISH) == en)
        check(translated(noConfig, Locale.FRENCH) == en) {
            "Feature alpha without config cannot contain a French override"
        }
        check(translated(config, Locale.ENGLISH) == en) {
            "Config split unexpectedly changed default English resources"
        }
        check(translated(config, Locale.FRENCH) == fr) {
            "Signed private alpha config did not override locale-specific resources"
        }
        for (resource in listOf(noConfig, config)) {
            val id = resource.getIdentifier("fonesi_beta_marker", "string", PACKAGE)
            check(id != 0 && resource.getString(id) == "Fonesi signed dependent beta feature")
        }
        check(apks.open(clusterId, Build.VERSION.SDK_INT) == layout.cluster &&
            preparer.verify(native) && NativeRuntime.state() == NativeRuntime.ENABLED) {
            "Signed private configuration APK/native image changed during execution"
        }
    }

    private fun hostOwnedDirectory(requested: File, owned: File): File {
        val directory = requested.canonicalFile
        check(directory.path.startsWith(owned.path + File.separator))
        check(directory.mkdirs() || directory.isDirectory)
        check(directory.canonicalFile == directory)
        return directory
    }
    private fun requireAbsent(loader: ClassLoader, className: String) {
        try { loader.loadClass(className); error("Untrusted classloader visibility: $className") }
        catch (_: ClassNotFoundException) { /* Missing is the required result. */ }
    }
    private fun sha(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val HOST_PACKAGE = "vn.fonesi.nativeprobe"
        const val PACKAGE = "vn.fonesi.splitfixture"
        const val ALPHA = "feature.alpha"
        const val BETA = "feature.beta"
        const val CONFIG = "feature.alpha.fr"
        val NONCE = Regex("[0-9a-f]{32}")
        val attempted = AtomicBoolean(false)
    }
}
