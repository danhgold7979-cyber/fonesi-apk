package vn.fonesi.apk

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

enum class ClusterOwnerType { CLONE, UPDATE_TASK, REVIEW_REQUIRED, ROLLBACK }

data class ClusterReference(
    val clusterId: String,
    val ownerType: ClusterOwnerType,
    val ownerId: String,
    val engineKey: String,
    val createdAtEpochMs: Long,
)

/**
 * Durable ownership ledger for immutable private cluster generations.
 *
 * This ledger is deliberately non-destructive: it can identify unreferenced candidates but never
 * deletes a cluster. The caller must reconcile ledger owners with clone metadata and update-journal
 * state before a future collector may remove data.
 */
class PrivateApkClusterReferenceLedger(root: Path) {
    private val root: Path
    private val indexName = "cluster.references"
    private val lockName = ".cluster-references.lock"

    init {
        val absolute = root.toAbsolutePath().normalize()
        Files.createDirectories(absolute)
        require(absolute.toFile().canonicalFile.toPath() == absolute && absolute.toRealPath() == absolute &&
            Files.isDirectory(absolute, NOFOLLOW_LINKS)) {
            "Reference ledger root must be canonical and cannot contain symlink parents"
        }
        this.root = absolute
    }

    fun acquire(reference: ClusterReference): Boolean = withLockedIndex { current ->
        validate(reference)
        require(isPublishedCluster(reference.clusterId)) { "Cannot reference a missing or unsafe cluster" }
        if (current.any { sameOwner(it, reference) }) current to false else (current + reference) to true
    }

    fun release(reference: ClusterReference): Boolean = withLockedIndex { current ->
        validate(reference)
        val next = current.filterNot { sameOwner(it, reference) }.toSet()
        if (next == current) current to false else next to true
    }

    fun references(clusterId: String): Set<ClusterReference> = withLockedIndex { current ->
        validateClusterId(clusterId)
        current to current.filterTo(linkedSetOf()) { it.clusterId == clusterId }
    }.second

    fun referencesForOwner(ownerType: ClusterOwnerType, ownerId: String, engineKey: String): Set<ClusterReference> =
        withLockedIndex { current ->
            require(ownerId.isNotBlank() && ownerId.length <= MAX_FIELD)
            require(engineKey.isNotBlank() && engineKey.length <= MAX_FIELD)
            current to current.filterTo(linkedSetOf()) {
                it.ownerType == ownerType && it.ownerId == ownerId && it.engineKey == engineKey
            }
        }.second

    /** Dry-run only. Results are candidates for reconciliation, never authorization to delete. */
    fun unreferencedClusterIds(): Set<String> = withLockedIndex { current ->
        val referenced = current.mapTo(mutableSetOf()) { it.clusterId }
        val candidates = Files.list(root).use { paths ->
            paths.iterator().asSequence()
                .filter { path ->
                    val name = path.fileName.toString()
                    runCatching { validateClusterId(name); true }.getOrDefault(false) &&
                        Files.isDirectory(path, NOFOLLOW_LINKS) && path.toRealPath() == path &&
                        Files.isRegularFile(path.resolve("cluster.index"), NOFOLLOW_LINKS) &&
                        path.resolve("cluster.index").toRealPath() == path.resolve("cluster.index")
                }
                .map { it.fileName.toString() }
                .filter { it !in referenced }
                .toCollection(sortedSetOf())
        }
        current to candidates
    }.second

    private fun isPublishedCluster(id: String): Boolean {
        val path = root.resolve(id)
        return Files.isDirectory(path, NOFOLLOW_LINKS) && path.toRealPath() == path &&
            Files.isRegularFile(path.resolve("cluster.index"), NOFOLLOW_LINKS)
    }

    private fun sameOwner(left: ClusterReference, right: ClusterReference): Boolean =
        left.clusterId == right.clusterId && left.ownerType == right.ownerType &&
            left.ownerId == right.ownerId && left.engineKey == right.engineKey

    private fun validate(reference: ClusterReference) {
        validateClusterId(reference.clusterId)
        require(reference.ownerId.isNotBlank() && reference.ownerId.length <= MAX_FIELD)
        require(reference.engineKey.isNotBlank() && reference.engineKey.length <= MAX_FIELD)
        require(reference.createdAtEpochMs >= 0)
    }

    private fun validateClusterId(id: String) {
        require(UUID.fromString(id).toString() == id) { "Invalid cluster ID" }
    }

    private fun <T> withLockedIndex(block: (Set<ClusterReference>) -> Pair<Set<ClusterReference>, T>): T =
        synchronized(PROCESS_LOCK) {
            val lockPath = root.resolve(lockName)
            FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root) {
                        "Reference ledger root changed"
                    }
                    val current = readIndex()
                    val (next, result) = block(current)
                    if (next != current) writeIndex(next)
                    result
                }
            }
        }

    private fun readIndex(): Set<ClusterReference> {
        val path = root.resolve(indexName)
        if (!Files.exists(path, NOFOLLOW_LINKS)) return emptySet()
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && path.toRealPath() == path)
        require(Files.size(path) in 1..MAX_INDEX_BYTES.toLong()) { "Reference index exceeds size budget" }
        return DataInputStream(BufferedInputStream(Files.newInputStream(path))).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "Unknown reference index format" }
            val count = input.readInt()
            require(count in 0..MAX_REFERENCES) { "Invalid reference count" }
            val refs = (0 until count).map {
                val id = input.readUTF()
                val owner = runCatching { ClusterOwnerType.valueOf(input.readUTF()) }
                    .getOrElse { throw IllegalArgumentException("Unknown cluster owner type", it) }
                val ownerId = input.readUTF()
                val engine = input.readUTF()
                val created = input.readLong()
                ClusterReference(id, owner, ownerId, engine, created).also(::validate)
            }
            require(refs.distinct().size == refs.size && input.read() == -1) {
                "Duplicate reference or trailing index bytes"
            }
            refs.toSet()
        }
    }

    private fun writeIndex(references: Set<ClusterReference>) {
        require(references.size <= MAX_REFERENCES) { "Reference count exceeds budget" }
        val temp = root.resolve(".cluster-references-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp.toFile()).use { file ->
                DataOutputStream(file).apply {
                    writeInt(MAGIC); writeInt(VERSION); writeInt(references.size)
                    references.sortedWith(compareBy<ClusterReference>(
                        { it.clusterId }, { it.ownerType.name }, { it.engineKey }, { it.ownerId }, { it.createdAtEpochMs },
                    )).forEach { ref ->
                        writeUTF(ref.clusterId)
                        writeUTF(ref.ownerType.name)
                        writeUTF(ref.ownerId)
                        writeUTF(ref.engineKey)
                        writeLong(ref.createdAtEpochMs)
                    }
                    flush()
                }
                file.fd.sync()
            }
            require(Files.size(temp) <= MAX_INDEX_BYTES) { "Reference index exceeds size budget" }
            Files.move(temp, root.resolve(indexName), ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(root, READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private companion object {
        const val MAGIC = 0x46435246 // FCRF
        const val VERSION = 1
        const val MAX_FIELD = 512
        const val MAX_REFERENCES = 100_000
        const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        val PROCESS_LOCK = Any()
    }
}
