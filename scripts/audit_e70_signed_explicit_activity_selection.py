#!/usr/bin/env python3
"""E70: only explicitly declared signed PRIVATE Activities may be selected, never launched."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
resolver = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolver.kt")
tests = read("apk-runtime/src/test/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolverTest.kt")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
fixture = read("scripts/build_apk_audit_fixtures.py")
for token in (
    "object SignedPrivateExplicitActivityResolver",
    "fun select(",
    "requestedPackage == inventory.packageName",
    "explicitClass.isNotBlank()",
    "inventory.components.singleOrNull { it.className == explicitClass }",
    "component.kind == PrivateComponentKind.ACTIVITY",
    "component.kind == PrivateComponentKind.ACTIVITY_ALIAS",
    "it.className == component.aliasTarget && it.kind == PrivateComponentKind.ACTIVITY",
    "component.enabled != false && target.enabled != false",
    "inventory.applicationPermission == null",
    "component.permission == null && target.permission == null",
    "inventory.applicationEnabled != false",
    "caller == ExplicitActivityCaller.EXTERNAL_UNTRUSTED",
    "component.exported == true && component.enabled == true",
    "target.enabled == true",
    "component.sourceSplit in inventory.splitNames",
    "target.sourceSplit in inventory.splitNames",
    "VerifiedPrivateActivitySelection(",
):
    assert token in resolver, "E70 explicit Activity safety invariant missing: " + token
assert resolver.index("inventory.components.singleOrNull") < resolver.index("component.enabled != false")
assert resolver.index("component.permission == null") < resolver.index("VerifiedPrivateActivitySelection(",
    resolver.index("fun select("))
for forbidden in ("startActivity(", "getLaunchIntentForPackage(", "PackageManager.", "Intent(", "installPackage("):
    assert forbidden not in resolver, "Unexpected guest execution API: " + forbidden
catalog = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkComponentCatalog.kt")
assert "val applicationPermission: String?" in catalog and "val applicationEnabled: Boolean?" in catalog
assert "appPermission = optionalText(string(\"permission\"), \"application permission\")" in catalog
assert "appEnabled = declaredBoolean(\"enabled\")" in catalog
assert "manifest.applicationPermission == null" in catalog and "manifest.applicationEnabled == null" in catalog
assert "fun selectExplicitActivity(" in loader
assert "SignedPrivateExplicitActivityResolver.select(componentInventory, packageName, className, caller)" in loader
assert "componentInventory.clusterId == clusterId && componentInventory.packageName == packageName" in loader
for name in (
    "signedExplicitPublicActivityMayBeSelectedWithoutStartingAndroidActivity",
    "internalActivityMayBeNotExportedButExternalRequestFailsClosed",
    "exportedOrEnabledNotExplicitlyDeclaredIsNeverAuthorizedExternally",
    "disabledActivityAliasAndAliasTargetAreRejectedForAllCallers",
    "permissionProtectedActivityRequiresUnimplementedPermissionMediator",
    "exportedSignedAliasSelectsExactlyItsPrivateActivityTarget",
    "cannotSubstituteAnotherPackageComponentKindOrImplicitEntry",
    "featureActivityCanOnlyBeResolvedFromItsVerifiedSignedSplit",
    "inheritedSignedApplicationPermissionBlocksActivityWithoutPermissionBroker",
    "disabledSignedApplicationBlocksEvenExplicitlyEnabledPublicActivity",
    "featureSplitCannotOverrideBaseApplicationPermissionPolicy",
):
    assert name in tests, "E70 signed PRIVATE Activity JVM regression missing: " + name
for member in ("catalog-public", "catalog-public-feature", "catalog-app-protected",
               "catalog-app-disabled", "catalog-split-app-permission"):
    assert 'apk("' + member + '"' in fixture
assert 'const val supportsSplits = false' in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert 'check(source.apkPaths.size == 1)' in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "GuestCodeLayout.read(" in loader
print("PASS: E70 requires explicit signed private Activity/alias identity, enabled/exported and permission checks.")
print("PASS: resolver does not run any Android Activity, activate split runtime or fall back to original app.")
