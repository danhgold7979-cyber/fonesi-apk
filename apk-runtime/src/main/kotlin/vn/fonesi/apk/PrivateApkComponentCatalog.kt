package vn.fonesi.apk

import com.android.apksig.internal.apk.AndroidBinXmlParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/**
 * Read-only metadata from a fully re-audited PRIVATE base/split cluster.
 * This is NOT Android PackageManager registration, guest execution, component routing,
 * exported permission enforcement or independent Android UID/account isolation.
 */
enum class PrivateComponentKind { ACTIVITY, ACTIVITY_ALIAS, SERVICE, RECEIVER, PROVIDER }

data class VerifiedPrivateComponent(
    val kind: PrivateComponentKind,
    val className: String,
    val sourceSplit: String?,
    /** Null = no explicit declaration; do NOT infer exported from an intent-filter. */
    val exported: Boolean?,
    /** Null = no explicit declaration; not a runtime enabled-state observation. */
    val enabled: Boolean?,
    val permission: String?,
    val processName: String?,
    val authorities: List<String>,
    val aliasTarget: String?,
)

data class VerifiedPrivateComponentInventory(
    val clusterId: String,
    val clusterDigest: String,
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
    val splitNames: List<String>,
    val applicationClass: String?,
    val components: List<VerifiedPrivateComponent>,
)

/**
 * Generic signed private binary-manifest inventory. Unlike synthetic test-only catalogs,
 * this accepts any valid signed package without consulting its ORIGINAL installed package.
 * It does not install/activate an app and never turns on production split support.
 */
object PrivateApkComponentCatalog {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    private const val MAX_COMPONENTS = 4096
    private val CLASS = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    private val AUTHORITY = Regex("[A-Za-z_][A-Za-z0-9_.]*")
    private val KINDS = mapOf(
        "activity" to PrivateComponentKind.ACTIVITY,
        "activity-alias" to PrivateComponentKind.ACTIVITY_ALIAS,
        "service" to PrivateComponentKind.SERVICE,
        "receiver" to PrivateComponentKind.RECEIVER,
        "provider" to PrivateComponentKind.PROVIDER,
    )

    fun inspect(store: PrivateApkStore, clusterId: String, platformApi: Int): VerifiedPrivateComponentInventory {
        val signed = store.open(clusterId, platformApi)
        val base = signed.apks.base
        val members = listOf(base) + signed.apks.splits.sortedBy { it.splitName }
        val parsed = members.map { member ->
            val manifest = parseBinaryManifest(member.file, base.packageName, member.splitName)
            // Configuration-only splits must never inject their own Java components.
            require(member.feature || member.splitName == null ||
                (manifest.components.isEmpty() && manifest.applicationClass == null)) {
                "Configuration APK declares executable components"
            }
            require(member.splitName == null || manifest.applicationClass == null) {
                "Split APK cannot redefine the base Application"
            }
            manifest
        }
        val all = parsed.flatMap { it.components }
        require(all.size <= MAX_COMPONENTS) { "Signed component inventory exceeds budget" }
        require(all.map { it.className }.distinct().size == all.size) {
            "Duplicate executable component identities across base/splits"
        }
        val activities = all.filter { it.kind == PrivateComponentKind.ACTIVITY }.map { it.className }.toSet()
        require(all.filter { it.kind == PrivateComponentKind.ACTIVITY_ALIAS }.all { it.aliasTarget in activities }) {
            "Activity alias has no signed Activity target"
        }
        val authorities = all.filter { it.kind == PrivateComponentKind.PROVIDER }.flatMap { it.authorities }
        require(authorities.distinct().size == authorities.size) { "Duplicate provider authority" }
        // Read back the exact signed PRIVATE APKs after manifest parsing; no original-app fallback.
        require(store.open(clusterId, platformApi) == signed) {
            "Private signed APK image changed during component catalog preflight"
        }
        return VerifiedPrivateComponentInventory(
            signed.id, signed.clusterDigest, base.packageName, base.version,
            base.signers.toSet(), signed.apks.splits.map { checkNotNull(it.splitName) }.sorted(),
            parsed.first().applicationClass, all.toList(),
        )
    }

    private data class Parsed(val applicationClass: String?, val components: List<VerifiedPrivateComponent>)

    private fun parseBinaryManifest(file: File, expectedPackage: String, splitName: String?): Parsed {
        val binary = ZipFile(file).use { zip ->
            require(zip.entries().asSequence().count { it.name == "AndroidManifest.xml" } == 1) {
                "Private APK must have exactly one AndroidManifest.xml"
            }
            val entry = checkNotNull(zip.getEntry("AndroidManifest.xml"))
            require(entry.size in 1..MAX_MANIFEST_BYTES.toLong()) { "Unsafe manifest byte budget" }
            zip.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count <= MAX_MANIFEST_BYTES - output.size()) { "Manifest exceeds budget" }
                    output.write(buffer, 0, count)
                }
                require(output.size().toLong() == entry.size) { "Manifest length changed while reading" }
                output.toByteArray()
            }
        }
        val parser = AndroidBinXmlParser(ByteBuffer.wrap(binary))
        var manifestSeen = false
        var applicationSeen = false
        var appClass: String? = null
        val components = mutableListOf<VerifiedPrivateComponent>()
        while (parser.next() != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (parser.eventType != AndroidBinXmlParser.EVENT_START_ELEMENT) continue
            val attributes = (0 until parser.attributeCount).associateBy {
                parser.getAttributeNamespace(it).orEmpty() to parser.getAttributeName(it)
            }
            require(attributes.size == parser.attributeCount) { "Duplicate binary manifest attribute" }
            fun string(name: String): String? = attributes[ANDROID to name]?.let { position ->
                require(parser.getAttributeValueType(position) == AndroidBinXmlParser.VALUE_TYPE_STRING) {
                    "Unresolved/unsafe component attribute: " + name
                }
                checkNotNull(parser.getAttributeStringValue(position))
            }
            fun declaredBoolean(name: String): Boolean? = attributes[ANDROID to name]?.let { position ->
                require(parser.getAttributeValueType(position) == AndroidBinXmlParser.VALUE_TYPE_BOOLEAN) {
                    "Unsafe Boolean component attribute: " + name
                }
                parser.getAttributeBooleanValue(position)
            }
            fun optionalText(raw: String?, field: String): String? = raw?.also { value ->
                require(value.length in 1..255 && value.none { it.isWhitespace() || it.isISOControl() } &&
                    !value.startsWith("@")) { "Unsafe " + field }
            }
            fun className(raw: String?): String {
                require(!raw.isNullOrBlank() && raw.length <= 255) { "Missing/unsafe class" }
                val qualified = when {
                    raw.startsWith(".") -> expectedPackage + raw
                    '.' !in raw -> expectedPackage + "." + raw
                    else -> raw
                }
                require(qualified.length <= 255 && CLASS.matches(qualified)) { "Unsafe component class name" }
                return qualified
            }
            val tag = parser.name
            require(parser.namespace.orEmpty().isEmpty() || parser.depth > 1) {
                "Manifest elements must not use a foreign namespace"
            }
            when {
                parser.depth == 1 -> {
                    require(!manifestSeen && tag == "manifest")
                    manifestSeen = true
                }
                parser.depth == 2 && tag == "application" -> {
                    require(manifestSeen && !applicationSeen) { "Duplicate/misplaced Application" }
                    applicationSeen = true
                    appClass = string("name")?.let(::className)
                }
                tag in KINDS -> {
                    require(applicationSeen && parser.depth == 3) { "Misplaced executable component" }
                    require(components.size < MAX_COMPONENTS) { "Component count exceeds budget" }
                    val kind = KINDS.getValue(tag)
                    val authorityText = string("authorities")
                    val authorityList = if (kind == PrivateComponentKind.PROVIDER) {
                        require(!authorityText.isNullOrBlank()) { "Provider lacks explicit authorities" }
                        authorityText.split(';').also { parts ->
                            require(parts.isNotEmpty() && parts.distinct().size == parts.size &&
                                parts.all { it.length <= 255 && AUTHORITY.matches(it) }) {
                                "Invalid provider authorities"
                            }
                        }
                    } else {
                        require(authorityText == null) { "Non-provider declares authorities" }
                        emptyList()
                    }
                    val aliasRaw = string("targetActivity")
                    val target = if (kind == PrivateComponentKind.ACTIVITY_ALIAS) className(aliasRaw) else {
                        require(aliasRaw == null) { "Unexpected alias target" }
                        null
                    }
                    components += VerifiedPrivateComponent(
                        kind = kind,
                        className = className(string("name")),
                        sourceSplit = splitName,
                        exported = declaredBoolean("exported"),
                        enabled = declaredBoolean("enabled"),
                        permission = optionalText(string("permission"), "permission"),
                        processName = optionalText(string("process"), "process"),
                        authorities = authorityList,
                        aliasTarget = target,
                    )
                }
            }
        }
        require(manifestSeen && applicationSeen) { "Signed APK manifest lacks an Application" }
        return Parsed(appClass, components)
    }
}
