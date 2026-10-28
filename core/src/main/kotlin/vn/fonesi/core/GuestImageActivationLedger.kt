package vn.fonesi.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.UUID

/** A verified code image reference, not a running guest or evidence of account isolation. */
class GuestImageRef(
    val clusterId: String,
    val packageName: String,
    val versionCode: Long,
    signerSha256: Set<String>,
) {
    /** Snapshot the caller's collection: a later mutation cannot change a staged identity. */
    val signerSha256: Set<String> = java.util.Collections.unmodifiableSet(LinkedHashSet(signerSha256))

    init {
        require(UUID.fromString(clusterId).toString() == clusterId) { "Invalid image ID" }
        require(PACKAGE.matches(packageName) && packageName.length <= 255) { "Invalid package" }
        require(versionCode >= 0) { "Invalid version" }
        require(this.signerSha256.size in 1..32 && this.signerSha256.all { DIGEST.matches(it) }) { "Invalid signing identity" }
    }

    fun copy(
        clusterId: String = this.clusterId,
        packageName: String = this.packageName,
        versionCode: Long = this.versionCode,
        signerSha256: Set<String> = this.signerSha256,
    ) = GuestImageRef(clusterId, packageName, versionCode, signerSha256)

    override fun equals(other: Any?): Boolean = this === other || (other is GuestImageRef &&
        clusterId == other.clusterId && packageName == other.packageName &&
        versionCode == other.versionCode && signerSha256 == other.signerSha256)
    override fun hashCode(): Int = (((clusterId.hashCode() * 31 + packageName.hashCode()) * 31 +
        versionCode.hashCode()) * 31 + signerSha256.hashCode())
    override fun toString() = "GuestImageRef(clusterId=" + clusterId + ", packageName=" + packageName +
        ", versionCode=" + versionCode + ", signers=" + signerSha256.size + ")"

    companion object {
        private val PACKAGE = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
        private val DIGEST = Regex("[0-9a-f]{64}")
    }
}

/** A pending image MUST NOT be treated as installed, executed, or safe to retry automatically. */
data class GuestImageActivationState(
    val revision: Long = 0L,
    val active: GuestImageRef? = null,
    val pending: GuestImageRef? = null,
)

/**
 * Durable per-clone code-image pointer ledger. The caller provides a verifier backed by
 * PrivateApkStore.open, and real runtime readback is required before confirmFromRuntimeReadback.
 * This class never loads code, executes a guest, or touches a clone's account data.
 *
 * The SHA-256 checksum detects corrupt records; it is NOT authentication against malicious
 * code already running under the same host UID. Android process/UID isolation is a separate gate.
 */
class GuestImageActivationLedger(
    requestedRoot: Path,
    private val verifyStoredImage: (GuestImageRef) -> Boolean,
) {
    private val root: Path

    init {
        val absolute = requestedRoot.toAbsolutePath().normalize()
        require(absolute.toFile().canonicalFile.toPath() == absolute) { "Ledger root cannot traverse a symlink" }
        Files.createDirectories(absolute)
        require(Files.isDirectory(absolute, NOFOLLOW_LINKS) && absolute.toRealPath() == absolute)
        root = absolute
    }

    fun read(cloneId: String): GuestImageActivationState = locked { readState(path(cloneId)) }

    /** Read with cryptographic private-cluster revalidation, not a guest-runtime observation. */
    fun readVerified(cloneId: String): GuestImageActivationState = locked {
        readState(path(cloneId)).also { state ->
            check(state.active == null || verifyStoredImage(state.active)) { "Active private APK image failed re-verification" }
            check(state.pending == null || verifyStoredImage(state.pending)) { "Pending private APK image failed re-verification" }
        }
    }

    /** Staging records intent only. Active image and existing guest data are unchanged. */
    fun stage(cloneId: String, expectedRevision: Long, image: GuestImageRef): GuestImageActivationState = locked {
        val file = path(cloneId)
        val current = readState(file)
        require(current.revision == expectedRevision) { "Stale activation revision" }
        check(current.pending == null) { "Unresolved code-image activation needs reconciliation" }
        current.active?.let { active ->
            check(verifyStoredImage(active)) { "Active private APK image failed re-verification" }
            require(image.packageName == active.packageName && image.signerSha256 == active.signerSha256) {
                "Cannot switch package or signer of an existing clone"
            }
            require(image.versionCode >= active.versionCode) { "Code-image downgrade is not allowed" }
            require(image != active) { "Image is already active" }
        }
        check(verifyStoredImage(image)) { "Private APK image could not be verified" }
        val next = current.copy(revision = Math.addExact(current.revision, 1L), pending = image)
        writeState(file, next)
        next
    }

    /**
     * Only call after a real guest provider has read back the effective image for this clone.
     * Equality checks do not independently certify that the provider or Android guest is functional.
     */
    fun confirmFromRuntimeReadback(
        cloneId: String,
        expectedRevision: Long,
        observedImage: GuestImageRef,
    ): GuestImageActivationState = locked {
        val file = path(cloneId)
        val current = readState(file)
        require(current.revision == expectedRevision) { "Stale activation revision" }
        val pending = checkNotNull(current.pending) { "No pending image to confirm" }
        require(observedImage == pending) { "Guest runtime readback does not match pending image" }
        current.active?.let { check(verifyStoredImage(it)) { "Active private APK image failed re-verification" } }
        check(verifyStoredImage(pending)) { "Pending private APK image failed re-verification" }
        val next = GuestImageActivationState(Math.addExact(current.revision, 1L), pending, null)
        writeState(file, next)
        next
    }

    /** Caller MUST establish that no SDK/native mutation started; otherwise preserve pending. */
    fun abortBeforeSdk(cloneId: String, expectedRevision: Long, sdkMutationNeverStarted: Boolean): GuestImageActivationState = locked {
        require(sdkMutationNeverStarted) { "Uncertain native mutation: retain pending for review" }
        val file = path(cloneId)
        val current = readState(file)
        require(current.revision == expectedRevision) { "Stale activation revision" }
        check(current.pending != null) { "No pending activation" }
        val next = current.copy(revision = Math.addExact(current.revision, 1L), pending = null)
        writeState(file, next)
        next
    }

    private fun path(id: String): Path {
        require(UUID.fromString(id).toString() == id) { "Clone ID must be a canonical UUID" }
        return root.resolve("$id.state")
    }

    private fun checkRoot() {
        check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root) { "Ledger root changed" }
    }

    private fun <T> locked(block: () -> T): T = synchronized(IN_PROCESS_LOCK) {
        checkRoot()
        val lockFile = root.resolve(".activation.lock")
        FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            require(Files.isRegularFile(lockFile, NOFOLLOW_LINKS) && lockFile.toRealPath() == lockFile)
            channel.lock().use {
                checkRoot()
                block()
            }
        }
    }

    private fun readState(file: Path): GuestImageActivationState {
        if (!Files.exists(file, NOFOLLOW_LINKS)) return GuestImageActivationState()
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file) { "Unsafe activation record" }
        require(Files.size(file) in 1..MAX_RECORD.toLong()) { "Activation record exceeds size budget" }
        val encoded = Files.readAllBytes(file)
        DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == FORMAT) { "Unknown activation format" }
            val size = input.readInt()
            require(size in 1..MAX_PAYLOAD && size.toLong() + HEADER + HASH_BYTES == encoded.size.toLong()) {
                "Invalid activation length"
            }
            val payload = ByteArray(size)
            input.readFully(payload)
            val expected = ByteArray(HASH_BYTES)
            input.readFully(expected)
            require(input.read() == -1 && MessageDigest.isEqual(expected, sha256(payload))) {
                "Activation record checksum mismatch"
            }
            DataInputStream(ByteArrayInputStream(payload)).use { state ->
                val revision = state.readLong()
                require(revision >= 0)
                val active = if (state.readBoolean()) readImage(state) else null
                val pending = if (state.readBoolean()) readImage(state) else null
                require(state.read() == -1) { "Unexpected trailing activation data" }
                require(revision != 0L || (active == null && pending == null))
                require(active == null || pending == null || (active.packageName == pending.packageName &&
                    active.signerSha256 == pending.signerSha256 && pending.versionCode >= active.versionCode))
                return GuestImageActivationState(revision, active, pending)
            }
        }
    }

    private fun readImage(input: DataInputStream): GuestImageRef {
        val id = input.readUTF()
        val pkg = input.readUTF()
        val version = input.readLong()
        val count = input.readInt()
        require(count in 1..32)
        val signers = (0 until count).map { input.readUTF() }
        require(signers.toSet().size == count)
        return GuestImageRef(id, pkg, version, signers.toSet())
    }

    private fun writeState(file: Path, state: GuestImageActivationState) {
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeLong(state.revision)
                output.writeBoolean(state.active != null)
                state.active?.let { writeImage(output, it) }
                output.writeBoolean(state.pending != null)
                state.pending?.let { writeImage(output, it) }
            }
        }.toByteArray()
        require(payload.size in 1..MAX_PAYLOAD)
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT)
                output.writeInt(payload.size)
                output.write(payload)
                output.write(sha256(payload))
            }
        }.toByteArray()
        val staging = root.resolve("." + file.fileName + "." + UUID.randomUUID() + ".tmp")
        try {
            FileChannel.open(staging, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
                val data = ByteBuffer.wrap(bytes)
                while (data.hasRemaining()) channel.write(data)
                channel.force(true)
            }
            checkRoot()
            if (Files.exists(file, NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file) {
                    "Unsafe previous activation record"
                }
            }
            Files.move(staging, file, ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(root, READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(staging)
        }
    }

    private fun writeImage(output: DataOutputStream, image: GuestImageRef) {
        output.writeUTF(image.clusterId)
        output.writeUTF(image.packageName)
        output.writeLong(image.versionCode)
        output.writeInt(image.signerSha256.size)
        image.signerSha256.sorted().forEach { output.writeUTF(it) }
    }

    private companion object {
        const val MAGIC = 0x4641494d // FAIM
        const val FORMAT = 1
        const val HEADER = 12L
        const val HASH_BYTES = 32
        const val MAX_PAYLOAD = 8192
        const val MAX_RECORD = MAX_PAYLOAD + 12 + 32
        val IN_PROCESS_LOCK = Any()
        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
