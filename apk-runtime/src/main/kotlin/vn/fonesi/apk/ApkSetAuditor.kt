package vn.fonesi.apk

import com.android.apksig.ApkVerifier
import com.android.apksig.internal.apk.AndroidBinXmlParser
import vn.fonesi.core.InstalledSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.zip.ZipFile

data class AuditedApk(val file: File, val packageName: String, val version: Long, val splitName: String?,
    val feature: Boolean, val configFor: String?, val dependencies: Set<String>, val splitRequired: Boolean,
    val requiredTypes: Set<String>, val providedTypes: Set<String>, val signers: Set<String>, val sha256: String)
data class AuditedApkSet(val base: AuditedApk, val splits: List<AuditedApk>)

/** Integrity only: does not install, execute, or assert an engine can load this APK cluster. */
object ApkSetAuditor {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val MANIFEST_BUDGET = 4 * 1024 * 1024
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val splitPattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    fun verify(source: InstalledSource, platformApi: Int): AuditedApkSet {
        require(platformApi in 26..36 && source.apkPaths.isNotEmpty() && source.signerDigests.isNotEmpty())
        val files = source.apkPaths.map { path ->
            val file = File(path).absoluteFile
            require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file.toPath()))
            require(file.toPath().normalize() == file.canonicalFile.toPath()) { "APK path must not traverse symlink directories" }
            file.canonicalFile
        }
        require(files.distinct().size == files.size) { "Duplicate APK path" }
        val members = files.map { read(it, platformApi) }
        require(members.all { it.packageName == source.packageName && it.version == source.versionCode && it.signers == source.signerDigests }) {
            "APK set package, version or verified signing identity does not match source"
        }
        val base = members.single { it.splitName == null }
        require(!base.feature && base.configFor == null && base.dependencies.isEmpty())
        val splits = members.filter { it.splitName != null }
        require(!base.splitRequired || splits.isNotEmpty()) { "Base requires split APKs" }
        val providedTypes = members.flatMap { it.providedTypes }.toSet()
        require(members.all { providedTypes.containsAll(it.requiredTypes) }) { "Missing required split type" }
        require(splits.map { it.splitName }.distinct().size == splits.size) { "Duplicate split identity" }
        val byName = splits.associateBy { checkNotNull(it.splitName) }
        val edges = splits.associate { member ->
            require(!member.feature || member.configFor == null) { "Feature cannot be a configuration split" }
            val required = member.dependencies + listOfNotNull(member.configFor?.takeIf { it.isNotEmpty() })
            required.forEach { name -> require(byName[name]?.feature == true) { "Missing feature dependency" } }
            checkNotNull(member.splitName) to required
        }
        // Iterative topological validation; a long malicious dependency chain cannot overflow stack.
        val pending = edges.mapValues { it.value.size }.toMutableMap()
        val dependants = mutableMapOf<String, MutableList<String>>()
        edges.forEach { (name, required) -> required.forEach { dependants.getOrPut(it) { mutableListOf() }.add(name) } }
        val ready = java.util.ArrayDeque(pending.filterValues { it == 0 }.keys)
        var visited = 0
        while (ready.isNotEmpty()) {
            val name = ready.removeFirst(); visited++
            dependants[name].orEmpty().forEach { dependant ->
                val left = pending.getValue(dependant) - 1; pending[dependant] = left
                if (left == 0) ready.addLast(dependant)
            }
        }
        require(visited == splits.size) { "Cyclic split dependency" }
        require(members.all { hash(it.file) == it.sha256 }) { "APK changed during set verification" }
        return AuditedApkSet(base, splits.toList())
    }

    private fun read(file: File, api: Int): AuditedApk {
        val before = hash(file)
        val bytes = ZipFile(file).use { zip ->
            require(zip.entries().asSequence().count { it.name == "AndroidManifest.xml" } == 1)
            val entry = checkNotNull(zip.getEntry("AndroidManifest.xml"))
            require(entry.size in 1..MANIFEST_BUDGET.toLong()) { "Manifest exceeds size budget" }
            zip.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    require(count <= MANIFEST_BUDGET - output.size()); output.write(buffer, 0, count)
                }
                require(output.size().toLong() == entry.size)
                output.toByteArray()
            }
        }
        val result = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(api).setMaxCheckedPlatformVersion(api).build().verify()
        require(result.isVerified && !result.containsErrors()) { "APK cryptographic signature is invalid" }
        val signers = result.signerCertificates.map { hex(MessageDigest.getInstance("SHA-256").digest(it.encoded)) }.toSet()
        require(signers.isNotEmpty())
        val parser = AndroidBinXmlParser(ByteBuffer.wrap(bytes))
        var rootSeen = false; var packageName: String? = null; var version: Long? = null; var split: String? = null
        var feature = false; var config: String? = null
        var splitRequired = false; var requiredTypes = emptySet<String>(); var providedTypes = emptySet<String>()
        val uses = mutableSetOf<String>()
        while (parser.next() != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (parser.eventType != AndroidBinXmlParser.EVENT_START_ELEMENT) continue
            val attributes = (0 until parser.attributeCount).associateBy { parser.getAttributeNamespace(it).orEmpty() to parser.getAttributeName(it) }
            require(attributes.size == parser.attributeCount) { "Duplicate manifest attribute" }
            fun string(namespace: String, name: String): String? = attributes[namespace to name]?.let { index ->
                require(parser.getAttributeValueType(index) == AndroidBinXmlParser.VALUE_TYPE_STRING)
                parser.getAttributeStringValue(index)
            }
            fun integer(name: String): Long? = attributes[ANDROID to name]?.let { index ->
                require(parser.getAttributeValueType(index) == AndroidBinXmlParser.VALUE_TYPE_INT)
                parser.getAttributeIntValue(index).toLong() and 0xffffffffL
            }
            fun bool(name: String) = attributes[ANDROID to name]?.let { index ->
                require(parser.getAttributeValueType(index) == AndroidBinXmlParser.VALUE_TYPE_BOOLEAN)
                parser.getAttributeBooleanValue(index)
            } ?: false
            fun types(name: String): Set<String> = string(ANDROID, name)?.takeIf { it.isNotEmpty() }?.split(',')?.map { it.trim() }?.also { values ->
                require(values.all { splitPattern.matches(it) } && values.distinct().size == values.size)
            }?.toSet().orEmpty()
            if (parser.depth == 1) {
                require(!rootSeen && parser.name == "manifest" && parser.namespace.orEmpty().isEmpty())
                rootSeen = true; packageName = string("", "package")
                require(packageName?.let { it == "android" || (it.length <= 255 && packagePattern.matches(it)) } == true)
                val low = requireNotNull(integer("versionCode")); val major = integer("versionCodeMajor") ?: 0
                require(major <= Int.MAX_VALUE.toLong()) { "Version cannot exceed signed long" }
                version = (major shl 32) or low
                split = string("", "split"); split?.let { require(splitPattern.matches(it)) }
                require(!(attributes.containsKey("" to "configForSplit") && attributes.containsKey(ANDROID to "configForSplit")))
                config = string("", "configForSplit") ?: string(ANDROID, "configForSplit")
                config?.let { require(it.isEmpty() || splitPattern.matches(it)) }
                feature = bool("isFeatureSplit"); splitRequired = bool("isSplitRequired")
                requiredTypes = types("requiredSplitTypes"); providedTypes = types("splitTypes")
            } else if (parser.depth == 2 && parser.name == "uses-split" && parser.namespace.orEmpty().isEmpty()) {
                val name = checkNotNull(string(ANDROID, "name")); require(uses.isEmpty() && splitPattern.matches(name) && uses.add(name))
            }
        }
        require(rootSeen && hash(file) == before) { "APK changed while auditing" }
        return AuditedApk(file, checkNotNull(packageName), checkNotNull(version), split, feature, config, uses.toSet(),
            splitRequired, requiredTypes, providedTypes, signers, before)
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
