package vn.fonesi.core.update

import vn.fonesi.core.InstalledSource
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** Copies only installed APKs; never writes to the originals or to account data. */
class FileSourceSnapshotter(root: Path, private val catalog: SourceCatalog) : SourceSnapshotter {
    private val root: Path

    init {
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root)) { "Snapshot root must not be a symlink" }
        this.root = root.toRealPath()
    }

    override fun prepare(taskId: String, expected: InstalledSource): PreparedSource {
        val destination = directory(taskId)
        require(expected.apkPaths.isNotEmpty() && expected.signerDigests.isNotEmpty())
        if (catalog.current(expected.packageName) != expected) throw SourceChangedException()
        val names = expected.apkPaths.map { Paths.get(it).fileName.toString() }
        require(names.distinct().size == names.size && names.all { it.endsWith(".apk", ignoreCase = true) })
        discard(taskId) // PREPARING recovery only; APPLYING/unknown snapshots are retained.
        val staging = root.resolve("$taskId.tmp-${UUID.randomUUID()}")
        Files.createDirectory(staging)
        try {
            val descriptors = expected.apkPaths.mapIndexed { index, source ->
                val from = Paths.get(source)
                require(Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(from))
                val to = staging.resolve(names[index])
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                Files.newInputStream(from).use { input ->
                    FileOutputStream(to.toFile()).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            size += read
                        }
                        output.fd.sync()
                    }
                }
                check(size > 0) { "Installed APK must not be empty" }
                check(to.toFile().setReadOnly()) { "Cannot protect prepared APK" }
                SnapshotFile(names[index], size, digest.digest().hex())
            }
            if (catalog.current(expected.packageName) != expected) throw SourceChangedException()
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
            FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
            val prepared = expected.copy(apkPaths = names.map { destination.resolve(it).toString() })
            val fingerprint = fingerprintFields(buildList {
                add(expected.packageName); add(expected.versionCode.toString())
                add(expected.signerDigests.size.toString()); addAll(expected.signerDigests.sorted())
                descriptors.forEach { add(it.name); add(it.size.toString()); add(it.sha256) }
            })
            return PreparedSource(prepared, fingerprint, descriptors).also { check(verify(it)) }
        } catch (error: Exception) {
            deleteTree(staging)
            discard(taskId)
            throw error
        }
    }

    override fun verify(snapshot: PreparedSource): Boolean = try {
        snapshot.source.apkPaths.size == snapshot.files.size && snapshot.source.apkPaths.indices.all { index ->
            val path = Paths.get(snapshot.source.apkPaths[index])
            val descriptor = snapshot.files[index]
            path.normalize().startsWith(root) && path.toRealPath().startsWith(root) &&
                !Files.isSymbolicLink(path.parent) && !Files.isSymbolicLink(path) &&
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == descriptor.size &&
                Files.newInputStream(path).use { stream ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                    digest.digest().hex() == descriptor.sha256
                }
        }
    } catch (_: Exception) { false }

    override fun discard(taskId: String) { deleteTree(directory(taskId)) }

    private fun directory(taskId: String): Path {
        require(UUID.fromString(taskId).toString() == taskId) { "Invalid snapshot ID" }
        return root.resolve(taskId).also { require(!Files.isSymbolicLink(it)) }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        require(!Files.isSymbolicLink(path))
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
