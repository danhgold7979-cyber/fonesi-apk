#!/usr/bin/env python3
"""E72: signed PRIVATE base Application process must not authorize guest selection."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
catalog = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkComponentCatalog.kt")
resolver = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolver.kt")
catalog_tests = read("apk-runtime/src/test/kotlin/vn/fonesi/apk/PrivateApkComponentCatalogTest.kt")
selection_tests = read("apk-runtime/src/test/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolverTest.kt")
fixtures = read("scripts/build_apk_audit_fixtures.py")
source_loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")

for required in (
    "val applicationProcessName: String? = null",
    'appProcess = optionalText(string("process"), "application process")',
    "manifest.applicationProcessName == null",
    "parsed.first().applicationProcessName",
    "return Parsed(appClass, components, appPermission, appEnabled, appProcess)",
    "require(store.open(clusterId, platformApi) == signed)",
):
    assert required in catalog, "E72 lost signed private Application process policy: " + required

for required in (
    "inventory.applicationProcessName == null",
    "component.processName == null && target.processName == null",
    "inventory.applicationPermission == null",
    "component.permission == null && target.permission == null",
    "component.exported == true && component.enabled == true",
    "inventory.applicationEnabled != false",
):
    assert required in resolver, "E72 lost explicit Activity policy: " + required
select = resolver.split("fun select(", 1)[1]
assert select.index("inventory.applicationProcessName == null") < select.index("return VerifiedPrivateActivitySelection(")
assert select.index("component.processName == null") < select.index("return VerifiedPrivateActivitySelection(")
for test in (
    "signedBaseApplicationProcessBlocksExplicitActivityForEveryCaller",
    "signedFeatureApplicationProcessCannotOverrideBaseProcessPolicy",
):
    assert test in selection_tests, "Missing E72 signed Activity regression: " + test
for test in (
    "signedBaseApplicationProcessPreservedWithoutInferringGuestRuntime",
    "signedFeatureProcessOverrideCannotChangeBaseApplicationPolicy",
):
    assert test in catalog_tests, "Missing E72 signed catalog regression: " + test
for fixture in ("catalog-app-process", "catalog-split-app-process"):
    assert 'apk("' + fixture + '"' in fixtures, "Missing signed process fixture: " + fixture
assert "PrivateApkComponentCatalog.inspect(apks, clusterId, Build.VERSION.SDK_INT)" in source_loader
for forbidden in ("startActivity(", "getLaunchIntentForPackage(", "installPackage(", "Intent("):
    assert forbidden not in resolver, "E72 unexpectedly dispatches a guest: " + forbidden
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
print("PASS: E72 signed PRIVATE base Application process and split override are fail closed.")
print("PASS: no Android guest Activity dispatch, split provider activation, or UID/account claim.")
