package vn.fonesi.apk

/**
 * Caller policy for a future guest proxy. No caller is given Android permission mediation.
 * EXTERNAL_UNTRUSTED must never infer the platform's exported/enabled defaults.
 */
enum class ExplicitActivityCaller { TRUSTED_INTERNAL, EXTERNAL_UNTRUSTED }

/**
 * Result of an explicit, signed PRIVATE Activity/alias metadata selection. This is NOT an
 * Android Intent, a registered Activity, a permission grant or a running guest component.
 */
data class VerifiedPrivateActivitySelection(
    val clusterId: String,
    val packageName: String,
    val requestedName: String,
    val targetActivityName: String,
    val requestedSplit: String?,
    val targetSplit: String?,
    val throughAlias: Boolean,
)

/**
 * No implicit launcher selection or installed-original-app PackageManager fallback.
 * The caller MUST have obtained inventory through PrivateApkComponentCatalog.inspect and
 * bound it to the exact loaded image before using this read-only helper.
 */
object SignedPrivateExplicitActivityResolver {
    fun select(
        inventory: VerifiedPrivateComponentInventory,
        requestedPackage: String,
        explicitClass: String,
        caller: ExplicitActivityCaller,
    ): VerifiedPrivateActivitySelection {
        require(requestedPackage == inventory.packageName) { "Guest package identity differs from signed image" }
        require(explicitClass.isNotBlank() && explicitClass.length <= 255) {
            "Only an explicit signed guest Activity is selectable"
        }
        val component = inventory.components.singleOrNull { it.className == explicitClass }
            ?: throw IllegalArgumentException("Explicit Activity is absent or ambiguous in signed manifest")
        require(component.kind == PrivateComponentKind.ACTIVITY ||
            component.kind == PrivateComponentKind.ACTIVITY_ALIAS) {
            "Signed component is not an Activity or Activity-alias"
        }
        val throughAlias = component.kind == PrivateComponentKind.ACTIVITY_ALIAS
        val target = if (throughAlias) {
            inventory.components.singleOrNull {
                it.className == component.aliasTarget && it.kind == PrivateComponentKind.ACTIVITY
            } ?: throw IllegalArgumentException("Signed Activity-alias target is absent")
        } else component
        require(inventory.applicationEnabled != false) {
            "Disabled signed Application cannot authorize guest Activity selection"
        }
        require(component.enabled != false && target.enabled != false) {
            "Disabled signed guest Activity or alias cannot be selected"
        }
        // This static selector has NO Android permission broker. Never authorize protected
        // components by assuming the host, its UID or a future guest possesses permissions.
        require(inventory.applicationPermission == null &&
            component.permission == null && target.permission == null) {
            "Permission-protected signed Application or Activity needs Android permission mediation"
        }
        if (caller == ExplicitActivityCaller.EXTERNAL_UNTRUSTED) {
            require(component.exported == true && component.enabled == true &&
                target.enabled == true) {
                "External guest Activity requires explicitly exported and enabled declarations"
            }
        }
        require(component.sourceSplit == null || component.sourceSplit in inventory.splitNames)
        require(target.sourceSplit == null || target.sourceSplit in inventory.splitNames)
        return VerifiedPrivateActivitySelection(
            inventory.clusterId, inventory.packageName, component.className, target.className,
            component.sourceSplit, target.sourceSplit, throughAlias,
        )
    }
}
