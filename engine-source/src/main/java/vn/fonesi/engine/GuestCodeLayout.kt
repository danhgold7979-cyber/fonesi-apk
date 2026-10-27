package vn.fonesi.engine

import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import java.io.File

/** Flat, private code/resources layout only; not an Android package/component registration. */
internal data class GuestCodeLayout(val cluster: PrivateApkCluster, val base: File, val splitNames: List<String>, val splits: List<File>) {
    val allApks get() = listOf(base) + splits
    companion object {
        fun read(store: PrivateApkStore, id: String, platformApi: Int, privateDataRoot: File, hostPackage: String): GuestCodeLayout {
            val cluster = store.open(id, platformApi)
            require(cluster.apks.base.packageName != hostPackage && cluster.apks.base.packageName != "android") {
                "Framework and host packages cannot use this guest loader"
            }
            require(!cluster.apks.base.isolatedSplits) { "Isolated split class loaders are not implemented" }
            val root = privateDataRoot.canonicalFile
            require(root.isDirectory)
            val members = listOf(cluster.apks.base) + cluster.apks.splits
            require(members.all { it.file == it.file.canonicalFile && it.file.path.startsWith(root.path + File.separator) }) {
                "Every APK must be in the host private code store"
            }
            val sorted = cluster.apks.splits.sortedBy { it.splitName }
            return GuestCodeLayout(cluster, cluster.apks.base.file, sorted.map { checkNotNull(it.splitName) }, sorted.map { it.file })
        }
    }
}
