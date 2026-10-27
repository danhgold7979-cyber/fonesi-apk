package vn.fonesi.engine

import vn.fonesi.apk.PrivateApkStore
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

data class PreparedNativeLibrary(val name: String, val bytes: Long, val sha256: String)
/** An immutable extraction receipt. It does not activate code or prove Android linker execution. */
data class PreparedNativeImage(val id: String, val clusterId: String, val abi: String, val pageSize: Int,
    val directory: Path, val libraries: List<PreparedNativeLibrary>)

/** Caller supplies a host-private root; no original/system library fallback or APK modification. */
class NativeLibraryPreparer(private val apks: PrivateApkStore, root: Path) {
    private val root: Path
    init {
        val absolute = root.toAbsolutePath().normalize()
        require(absolute.toFile().canonicalFile.toPath() == absolute)
        Files.createDirectories(absolute)
        require(Files.isDirectory(absolute, NOFOLLOW_LINKS) && absolute.toRealPath() == absolute)
        this.root = absolute
    }

    fun prepare(clusterId: String, abi: String, pageSize: Int, platformApi: Int): PreparedNativeImage {
        validateTarget(abi, pageSize); checkRoot()
        val cluster = apks.open(clusterId, platformApi)
        val id = UUID.randomUUID().toString()
        val stage = root.resolve(".stage-$id"); val final = root.resolve(id)
        Files.createDirectory(stage)
        var published = false
        try {
            val output = linkedMapOf<String, PreparedNativeLibrary>()
            var hasNative = false
            var expanded = 0L
            for (apk in listOf(cluster.apks.base) + cluster.apks.splits) ZipFile(apk.file).use { zip ->
                val seen = mutableSetOf<String>()
                for (entry in zip.entries().asSequence()) {
                    if (!entry.name.startsWith("lib/") || entry.isDirectory) continue
                    val parts = entry.name.split('/')
                    require(parts.size == 3 && parts[1] in ANDROID_ABIS && LIBRARY.matches(parts[2]) && parts[2].length <= 255) {
                        "Invalid native library archive path"
                    }
                    require(seen.add(entry.name)) { "Duplicate native library archive entry" }
                    hasNative = true
                    if (parts[1] != abi) continue
                    require(entry.size in 1..MAX_LIBRARY_BYTES) { "Native library exceeds extraction budget" }
                    expanded = Math.addExact(expanded, entry.size)
                    require(expanded <= MAX_IMAGE_BYTES) { "Native image exceeds extraction budget" }
                    val name = parts[2]
                    val prior = output[name]
                    val destination = stage.resolve(if (prior == null) name else ".duplicate")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    zip.getInputStream(entry).use { input -> FileOutputStream(destination.toFile()).use { file ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val size = input.read(buffer); if (size < 0) break
                            count = Math.addExact(count, size.toLong())
                            require(count <= entry.size)
                            file.write(buffer, 0, size); digest.update(buffer, 0, size)
                        }
                        file.fd.sync()
                    } }
                    require(count == entry.size)
                    val library = PreparedNativeLibrary(name, count, hex(digest.digest()))
                    if (prior != null) {
                        require(prior == library) { "Conflicting split libraries require separate native namespaces" }
                        Files.delete(destination)
                    } else {
                        checkElf(destination, abi, pageSize)
                        check(destination.toFile().setReadOnly()) { "Cannot protect extracted native library" }
                        output[name] = library
                    }
                }
            }
            require(!hasNative || output.isNotEmpty()) { "APK cluster has no native library for selected ABI" }
            // Revalidate the complete code snapshot after streaming all native entries.
            check(apks.open(clusterId, platformApi) == cluster) { "Private APK cluster changed during extraction" }
            sync(stage); checkRoot(); Files.move(stage, final, ATOMIC_MOVE); published = true; sync(root)
            return PreparedNativeImage(id, clusterId, abi, pageSize, final, output.values.sortedBy { it.name }).also {
                check(verify(it)) { "Native image readback failed" }
            }
        } finally {
            if (!published && Files.exists(stage, NOFOLLOW_LINKS)) Files.walk(stage).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
            }
        }
    }

    /** Verify a trusted receipt supplied by the engine registry, not arbitrary external JSON. */
    fun verify(image: PreparedNativeImage): Boolean = try {
        validateTarget(image.abi, image.pageSize); checkRoot()
        require(UUID.fromString(image.id).toString() == image.id && UUID.fromString(image.clusterId).toString() == image.clusterId)
        require(image.directory == root.resolve(image.id) && Files.isDirectory(image.directory, NOFOLLOW_LINKS) && image.directory.toRealPath() == image.directory)
        require(image.libraries.map { it.name }.distinct().size == image.libraries.size)
        val names = Files.list(image.directory).use { it.iterator().asSequence().map { path -> path.fileName.toString() }.toSet() }
        require(names == image.libraries.map { it.name }.toSet())
        image.libraries.forEach { library ->
            require(LIBRARY.matches(library.name) && library.name.length <= 255 && DIGEST.matches(library.sha256))
            val path = image.directory.resolve(library.name)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS) && path.toRealPath() == path && Files.size(path) == library.bytes)
            require(hash(path) == library.sha256)
            checkElf(path, image.abi, image.pageSize)
        }
        true
    } catch (_: Exception) { false }

    private fun checkRoot() { check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root) }

    private fun checkElf(path: Path, abi: String, pageSize: Int) {
        RandomAccessFile(path.toFile(), "r").use { file ->
            require(file.length() >= 64)
            fun read(offset: Long, count: Int): ByteBuffer {
                require(offset >= 0 && Math.addExact(offset, count.toLong()) <= file.length())
                val bytes = ByteArray(count); file.seek(offset); file.readFully(bytes)
                return ByteBuffer.wrap(bytes).order(LITTLE_ENDIAN)
            }
            val elf = read(0, 64)
            require(elf.getInt(0) == 0x464c457f && elf.get(4).toInt() == 2 && elf.get(5).toInt() == 1 && elf.get(6).toInt() == 1)
            require(elf.getShort(16).toInt() == 3 && elf.getShort(18).toInt() == if (abi == "arm64-v8a") 183 else 62)
            require(elf.getInt(20) == 1 && elf.getShort(52).toInt() == 64 && elf.getShort(54).toInt() == 56)
            val offset = elf.getLong(32); val count = elf.getShort(56).toInt() and 65535
            require(count in 1..1024 && offset >= 64 && Math.addExact(offset, count * 56L) <= file.length())
            var loads = 0
            repeat(count) { index ->
                val header = read(offset + index * 56L, 56)
                val type = header.getInt(0); val flags = header.getInt(4)
                if (type == 1) {
                    loads++
                    val position = header.getLong(8); val address = header.getLong(16)
                    val bytes = header.getLong(32); val memory = header.getLong(40); val alignment = header.getLong(48)
                    require(position >= 0 && address >= 0 && bytes >= 0 && memory >= bytes && Math.addExact(position, bytes) <= file.length())
                    require(Math.addExact(address, memory) >= address)
                    require(alignment >= pageSize && alignment and (alignment - 1) == 0L &&
                        position % alignment == address % alignment && position % pageSize == address % pageSize) {
                        "Guest native library does not support selected memory page size"
                    }
                    require(flags and 3 != 3) { "Writable executable ELF segment" }
                }
                if (type == 0x6474e551) require(flags and 1 == 0) { "Executable ELF stack" }
            }
            require(loads > 0)
        }
    }

    private companion object {
        val LIBRARY = Regex("[A-Za-z0-9_][A-Za-z0-9_.+\\-]*\\.so")
        val DIGEST = Regex("[0-9a-f]{64}")
        val ANDROID_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        const val MAX_LIBRARY_BYTES = 512 * 1024 * 1024L
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024 * 1024L
        fun validateTarget(abi: String, pageSize: Int) { require(abi in setOf("arm64-v8a", "x86_64") && pageSize in setOf(4096, 16384)) }
        fun sync(path: Path) { FileChannel.open(path, READ).use { it.force(true) } }
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        fun hash(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return hex(digest.digest())
        }
    }
}
