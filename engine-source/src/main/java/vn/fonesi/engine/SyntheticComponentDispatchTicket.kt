package vn.fonesi.engine

import org.json.JSONArray
import org.json.JSONObject
import vn.fonesi.apk.PrivateApkCluster
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID

/**
 * E29 test-only ticket binding a verified private synthetic manifest record to exact immutable
 * APK bytes and one emulator-run UUID. This ticket is not an Android guest-component registration.
 */
data class SyntheticComponentDispatchTicket(
    val attemptId: String,
    val clusterId: String,
    val packageName: String,
    val version: Long,
    val signerDigests: List<String>,
    val applicationName: String,
    val activityName: String,
    val privateApkPath: String,
    val privateApkSha256: String,
) {
    fun toJson(): String = JSONObject()
        .put("format", FORMAT)
        .put("attempt_id", attemptId)
        .put("cluster_id", clusterId)
        .put("package_name", packageName)
        .put("version", version)
        .put("signer_digests", JSONArray(signerDigests))
        .put("application_name", applicationName)
        .put("activity_name", activityName)
        .put("private_apk_path", privateApkPath)
        .put("private_apk_sha256", privateApkSha256)
        .toString().also { require(it.length <= MAX_JSON_CHARS) { "Dispatch ticket exceeds size budget" } }

    companion object {
        private const val FORMAT = 1
        private const val MAX_JSON_CHARS = 4096
        private val DIGEST = Regex("[a-f0-9]{64}")

        /** Create only after the caller has obtained a verified catalog record and reopened store. */
        fun issue(
            catalog: VerifiedPrivateSyntheticComponents,
            cluster: PrivateApkCluster,
            attemptId: UUID,
            privateRoot: File,
        ): SyntheticComponentDispatchTicket {
            validateIdentity(catalog, cluster, privateRoot)
            val base = cluster.apks.base
            val actualHash = sha256(base.file)
            require(actualHash == base.sha256) { "Private APK bytes differ from the audited cluster" }
            return SyntheticComponentDispatchTicket(
                attemptId = attemptId.toString(),
                clusterId = cluster.id,
                packageName = base.packageName,
                version = base.version,
                signerDigests = base.signers.sorted(),
                applicationName = catalog.applicationName,
                activityName = catalog.activityName,
                privateApkPath = base.file.canonicalPath,
                privateApkSha256 = actualHash,
            ).also { require(it.signerDigests.isNotEmpty() && it.signerDigests.all(DIGEST::matches)) }
        }

        /** Revalidate every identity field against a fresh private-store readback immediately before dispatch. */
        fun validate(
            ticket: SyntheticComponentDispatchTicket,
            catalog: VerifiedPrivateSyntheticComponents,
            cluster: PrivateApkCluster,
            expectedAttemptId: UUID,
            privateRoot: File,
        ) {
            require(UUID.fromString(ticket.attemptId).toString() == ticket.attemptId &&
                ticket.attemptId == expectedAttemptId.toString()) { "Stale or malformed dispatch UUID" }
            validateIdentity(catalog, cluster, privateRoot)
            val base = cluster.apks.base
            require(ticket.clusterId == cluster.id &&
                ticket.packageName == base.packageName &&
                ticket.version == base.version &&
                ticket.signerDigests == base.signers.sorted() &&
                ticket.applicationName == catalog.applicationName &&
                ticket.activityName == catalog.activityName &&
                ticket.privateApkPath == base.file.canonicalPath &&
                ticket.privateApkSha256 == base.sha256) {
                "Dispatch ticket identity differs from the verified private catalog"
            }
            require(DIGEST.matches(ticket.privateApkSha256) && sha256(base.file) == ticket.privateApkSha256) {
                "Private APK bytes changed after ticket issuance"
            }
        }

        fun fromJson(raw: String): SyntheticComponentDispatchTicket {
            require(raw.length in 1..MAX_JSON_CHARS) { "Missing or oversized dispatch ticket" }
            val json = JSONObject(raw)
            val expectedKeys = setOf(
                "format", "attempt_id", "cluster_id", "package_name", "version", "signer_digests",
                "application_name", "activity_name", "private_apk_path", "private_apk_sha256",
            )
            val keys = json.keys().asSequence().toSet()
            require(keys == expectedKeys && json.getInt("format") == FORMAT) {
                "Unknown dispatch ticket fields or format"
            }
            val signers = json.getJSONArray("signer_digests").let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
            require(signers.isNotEmpty() && signers.distinct().size == signers.size &&
                signers.all(DIGEST::matches)) { "Invalid dispatch ticket signer set" }
            val ticket = SyntheticComponentDispatchTicket(
                attemptId = json.getString("attempt_id"),
                clusterId = json.getString("cluster_id"),
                packageName = json.getString("package_name"),
                version = json.getLong("version"),
                signerDigests = signers,
                applicationName = json.getString("application_name"),
                activityName = json.getString("activity_name"),
                privateApkPath = json.getString("private_apk_path"),
                privateApkSha256 = json.getString("private_apk_sha256"),
            )
            require(DIGEST.matches(ticket.privateApkSha256))
            require(UUID.fromString(ticket.attemptId).toString() == ticket.attemptId)
            require(UUID.fromString(ticket.clusterId).toString() == ticket.clusterId)
            require(ticket.packageName.isNotBlank() && ticket.packageName.length <= 255)
            require(ticket.applicationName.isNotBlank() && ticket.applicationName.length <= 255)
            require(ticket.activityName.isNotBlank() && ticket.activityName.length <= 255)
            require(ticket.privateApkPath.isNotBlank() && ticket.privateApkPath.length <= 2048)
            return ticket
        }

        private fun validateIdentity(
            catalog: VerifiedPrivateSyntheticComponents,
            cluster: PrivateApkCluster,
            privateRoot: File,
        ) {
            val root = privateRoot.canonicalFile
            val base = cluster.apks.base
            val file = base.file
            require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
            require(cluster.id == catalog.clusterId &&
                base.packageName == catalog.packageName &&
                base.version == catalog.version &&
                base.file.canonicalPath == catalog.privatePath &&
                cluster.apks.splits.isEmpty()) { "Catalog and private APK cluster do not match" }
            require(catalog.activityExported &&
                catalog.applicationName.isNotBlank() && catalog.activityName.isNotBlank()) {
                "Synthetic component catalog is incomplete"
            }
            require(file.isFile && !Files.isSymbolicLink(file.toPath()) &&
                file.canonicalFile == file &&
                file.path.startsWith(root.path + File.separator) &&
                Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "Dispatch source must remain inside the private code root"
            }
            require(base.signers.isNotEmpty() && base.signers.all(DIGEST::matches)) {
                "Private APK signer set is missing or malformed"
            }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
