package vn.fonesi.engine

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

/**
 * SYNTHETIC TEST PAYLOAD ONLY. This is not a complete Android guest Context or a security sandbox:
 * arbitrary same-UID code can bypass a ContextWrapper, invoke inherited APIs or make syscalls.
 * Never expose this to a third-party APK, clone account, or product provider.
 *
 * A narrow, explicitly bounded Context prototype for verifying the data plane of a future guest
 * lifecycle layer without using host package resources/files through the approved methods.
 */
class SyntheticGuestScopedContext(
    private val host: Context,
    private val image: LoadedGuestCode,
    cloneId: String,
) : ContextWrapper(host.applicationContext) {
    private val id = UUID.fromString(cloneId).toString().also {
        require(it == cloneId) { "Clone ID must be canonical" }
    }
    private val owned = host.dataDir.canonicalFile
    private val scope by lazy { ownedDirectory(File(host.noBackupFilesDir.canonicalFile, "source-guest-scopes/$id")) }
    private val filename = Regex("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}")

    init {
        NativeRuntime.requireGuestProcess(host)
        check(NativeRuntime.state() == NativeRuntime.ENABLED) { "Native guest routing must be enabled first" }
        require(image.packageName != host.packageName && image.packageName != "android") {
            "A synthetic guest must not be the host or framework package"
        }
        check(scope.path.startsWith(owned.path + File.separator)) { "Guest scope escaped host private root" }
    }

    val scopeId: String get() = id

    override fun getPackageName(): String = image.packageName
    override fun getClassLoader(): ClassLoader = image.classLoader
    override fun getResources(): Resources = image.resources
    override fun getAssets(): AssetManager = image.resources.assets
    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = childDirectory("files")
    override fun getCacheDir(): File = childDirectory("cache")
    override fun getNoBackupFilesDir(): File = childDirectory("no-backup")
    override fun getFileStreamPath(name: String): File = checkedFile(filesDir, name)
    override fun openFileInput(name: String): FileInputStream = FileInputStream(checkedFile(filesDir, name))
    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        require(mode == Context.MODE_PRIVATE) { "Synthetic file IO only supports MODE_PRIVATE" }
        return FileOutputStream(checkedFile(filesDir, name))
    }
    override fun deleteFile(name: String): Boolean = checkedFile(filesDir, name).delete()
    override fun getDatabasePath(name: String): File = checkedFile(childDirectory("databases"), name)

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require(mode == Context.MODE_PRIVATE) { "Synthetic preferences must use MODE_PRIVATE" }
        checkName(name)
        // Persist only under a namespaced host-owned preference file. Same UID means this is
        // logical separation, not a process/UID security boundary.
        return host.getSharedPreferences("fonesi-synthetic-$id-$name", Context.MODE_PRIVATE)
    }

    /** No accidental fallback into host services, package manager, or Android component dispatch. */
    override fun getSystemService(name: String): Any = unsupported()
    override fun getPackageManager(): PackageManager = unsupported()
    override fun getContentResolver(): ContentResolver = unsupported()
    override fun getApplicationInfo(): ApplicationInfo = unsupported()
    override fun getPackageCodePath(): String = unsupported()
    override fun getPackageResourcePath(): String = unsupported()
    override fun startActivity(intent: Intent) { unsupported<Unit>() }
    override fun startActivity(intent: Intent, options: Bundle?) { unsupported<Unit>() }
    override fun startService(service: Intent): ComponentName = unsupported()
    override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean = unsupported()
    override fun sendBroadcast(intent: Intent) { unsupported<Unit>() }

    private fun childDirectory(name: String): File = ownedDirectory(File(scope, name))

    private fun ownedDirectory(requested: File): File {
        // Verify canonical location both before and after mkdir: do not follow a preexisting link.
        val absolute = requested.absoluteFile
        require(absolute.canonicalFile == absolute) { "Synthetic storage must not traverse symlinks" }
        check(absolute.path.startsWith(owned.path + File.separator)) { "Storage escaped private host data" }
        check(absolute.mkdirs() || absolute.isDirectory)
        require(!Files.isSymbolicLink(absolute.toPath()) && absolute.canonicalFile == absolute) {
            "Synthetic storage path changed"
        }
        return absolute
    }

    private fun checkedFile(parent: File, name: String): File {
        checkName(name)
        val file = File(parent, name).absoluteFile
        check(file.path.startsWith(scope.path + File.separator))
        require(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile == file) {
            "Synthetic IO must not follow a linked file"
        }
        return file
    }

    private fun checkName(name: String) {
        require(filename.matches(name) && name != "." && name != "..") { "Unsafe synthetic file/key name" }
    }

    private fun <T> unsupported(): T = throw UnsupportedOperationException(
        "Synthetic scoped Context does not implement Android package, services, components or permissions"
    )
}
