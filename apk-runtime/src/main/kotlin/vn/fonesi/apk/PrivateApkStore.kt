package vn.fonesi.apk

import vn.fonesi.core.InstalledSource
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ
import java.security.MessageDigest
import java.util.UUID

/** A published code snapshot, never a running/installed guest or an active-version pointer. */
data class PrivateApkCluster(val id: String, val apks: AuditedApkSet) {
    fun source() = InstalledSource(apks.base.packageName, apks.base.version, apks.base.signers,
        listOf(apks.base.file.path) + apks.splits.map { it.file.path })
}

/**
 * Immutable private APK clusters. The caller must choose a host-private root, separate from
 * account data. Publication does not activate a version; existing clusters are never overwritten.
 * No symlink, original-app fallback, APK rewrite, signing or account-session copy is supported.
 */
class PrivateApkStore(root: Path) {
    private val root: Path

    init {
        val absolute = root.toAbsolutePath().normalize()
        require(absolute.toFile().canonicalFile.toPath() == absolute) { "Code store parents must not be symlinks" }
        Files.createDirectories(absolute)
        require(absolute == absolute.toRealPath() && Files.isDirectory(absolute, NOFOLLOW_LINKS)) {
            "Code store must be a canonical directory without symlink parents"
        }
        this.root = absolute
    }

    fun publish(source: InstalledSource, platformApi: Int): PrivateApkCluster {
        checkRoot()
        val expected = source.copy(apkPaths = source.apkPaths.toList(), signerDigests = source.signerDigests.toSet())
        require(expected.apkPaths.size in 1..MAX_MEMBERS) { "APK cluster exceeds metadata budget" }
        val input = ApkSetAuditor.verify(expected, platformApi)
        val members = listOf(input.base) + input.splits.sortedBy { it.splitName }
        val id = UUID.randomUUID().toString()
        val staging = root.resolve(".stage-$id")
        val destination = root.resolve(id)
        Files.createDirectory(staging)
        var published = false
        try {
            val entries = members.map { member ->
                val name = member.splitName?.let { "split-${hash(it.toByteArray(Charsets.UTF_8))}.apk" } ?: "base.apk"
                val target = staging.resolve(name)
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                Files.newInputStream(member.file.toPath()).use { from ->
                    FileOutputStream(target.toFile()).use { to ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = from.read(buffer); if (count < 0) break
                            to.write(buffer, 0, count); digest.update(buffer, 0, count)
                            size = Math.addExact(size, count.toLong())
                        }
                        to.fd.sync()
                    }
                }
                check(size > 0 && hex(digest.digest()) == member.sha256) { "Source changed while copying APK cluster" }
                check(target.toFile().setReadOnly()) { "Cannot protect private APK" }
                Entry(name, size, member.sha256)
            }
            val privateSource = expected.copy(apkPaths = entries.map { staging.resolve(it.name).toString() })
            // The actual copies, not only the source paths, must pass cryptography and split parsing.
            ApkSetAuditor.verify(privateSource, platformApi)
            val index = Index(expected.packageName, expected.versionCode, expected.signerDigests, entries)
            writeIndex(staging.resolve(INDEX), id, index)
            syncDirectory(staging)
            checkRoot()
            Files.move(staging, destination, ATOMIC_MOVE)
            published = true
            syncDirectory(root)
            return open(id, platformApi)
        } finally {
            // A published but uncertain result remains available for readback; never delete it here.
            if (!published) removeOwnedStaging(staging)
        }
    }

    fun open(id: String, platformApi: Int): PrivateApkCluster {
        checkRoot()
        require(UUID.fromString(id).toString() == id) { "Invalid cluster ID" }
        val directory = root.resolve(id)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS) && directory.toRealPath() == directory)
        val indexPath = directory.resolve(INDEX)
        require(Files.isRegularFile(indexPath, NOFOLLOW_LINKS) && Files.size(indexPath) in 1..INDEX_BUDGET.toLong())
        val index = readIndex(indexPath, id)
        val files = Files.list(directory).use { paths -> paths.iterator().asSequence().map { it.fileName.toString() }.toSet() }
        require(files == index.entries.map { it.name }.toSet() + INDEX) { "Private cluster file set changed" }
        index.entries.forEach { entry ->
            val path = directory.resolve(entry.name)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS) && path.toRealPath() == path && Files.size(path) == entry.size)
            require(digest(path) == entry.sha256) { "Private APK bytes changed" }
        }
        val source = InstalledSource(index.pkg, index.version, index.signers, index.entries.map { directory.resolve(it.name).toString() })
        val verified = ApkSetAuditor.verify(source, platformApi)
        require(verified.base.file.name == "base.apk")
        require(verified.splits.all { it.file.name == "split-${hash(checkNotNull(it.splitName).toByteArray(Charsets.UTF_8))}.apk" })
        return PrivateApkCluster(id, verified)
    }

    private fun checkRoot() {
        check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root) { "Code store root changed" }
    }

    private fun writeIndex(path: Path, id: String, index: Index) {
        FileOutputStream(path.toFile()).use { file ->
            DataOutputStream(file).apply {
                writeInt(MAGIC); writeInt(1); writeUTF(id); writeUTF(index.pkg); writeLong(index.version)
                writeInt(index.signers.size); index.signers.sorted().forEach { writeUTF(it) }
                writeInt(index.entries.size)
                index.entries.forEach { writeUTF(it.name); writeLong(it.size); writeUTF(it.sha256) }
                flush()
            }
            file.fd.sync()
        }
        check(Files.size(path) <= INDEX_BUDGET && path.toFile().setReadOnly()) { "Cannot protect bounded cluster index" }
    }

    private fun readIndex(path: Path, id: String): Index = DataInputStream(BufferedInputStream(Files.newInputStream(path))).use { input ->
        require(input.readInt() == MAGIC && input.readInt() == 1 && input.readUTF() == id) { "Unknown or misplaced cluster index" }
        val pkg = input.readUTF(); val version = input.readLong()
        require(pkg.length <= 255 && version >= 0)
        val signerCount = input.readInt(); require(signerCount in 1..32)
        val signers = (0 until signerCount).map { input.readUTF().also { require(DIGEST.matches(it)) } }
        require(signers.distinct().size == signers.size)
        val count = input.readInt(); require(count in 1..MAX_MEMBERS)
        val entries = (0 until count).map {
            val name = input.readUTF(); val size = input.readLong(); val sha256 = input.readUTF()
            require((name == "base.apk" || SPLIT_FILE.matches(name)) && size > 0 && DIGEST.matches(sha256))
            Entry(name, size, sha256)
        }
        require(entries.first().name == "base.apk" && entries.map { it.name }.distinct().size == entries.size && input.read() == -1)
        Index(pkg, version, signers.toSet(), entries)
    }

    private fun removeOwnedStaging(staging: Path) {
        check(staging.parent == root && staging.fileName.toString().startsWith(".stage-"))
        // No FOLLOW_LINKS: a corrupt staging symlink is unlinked, never traversed.
        if (Files.exists(staging, NOFOLLOW_LINKS)) Files.walk(staging).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    private data class Entry(val name: String, val size: Long, val sha256: String)
    private data class Index(val pkg: String, val version: Long, val signers: Set<String>, val entries: List<Entry>)
    private companion object {
        const val INDEX = "cluster.index"
        const val MAGIC = 0x4641504b // FAPK
        const val MAX_MEMBERS = 512 // Per APK set, not a limit on clones or stored versions.
        const val INDEX_BUDGET = 256 * 1024
        val DIGEST = Regex("[0-9a-f]{64}")
        val SPLIT_FILE = Regex("split-[0-9a-f]{64}\\.apk")
        fun syncDirectory(path: Path) { FileChannel.open(path, READ).use { it.force(true) } }
        fun hash(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        fun digest(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { stream ->
                val buffer = ByteArray(65536)
                while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return hex(digest.digest())
        }
    }
}
