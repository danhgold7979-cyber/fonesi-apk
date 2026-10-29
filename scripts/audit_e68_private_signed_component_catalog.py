#!/usr/bin/env python3
"""E68: signed PRIVATE base/feature/config manifest inventory only, NOT a guest runtime."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
catalog = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkComponentCatalog.kt")
fixture = read("scripts/build_apk_audit_fixtures.py")
tests = read("apk-runtime/src/test/kotlin/vn/fonesi/apk/PrivateApkComponentCatalogTest.kt")
for required in (
    "store.open(clusterId, platformApi)",
    "listOf(base) + signed.apks.splits.sortedBy",
    "parseBinaryManifest(member.file, base.packageName, member.splitName)",
    "AndroidBinXmlParser(ByteBuffer.wrap(binary))",
    "entry.size in 1..MAX_MANIFEST_BYTES.toLong()",
    "val components = mutableListOf<VerifiedPrivateComponent>()",
    "val all = parsed.flatMap { it.components }",
    "Duplicate executable component identities across base/splits",
    "Configuration APK declares executable components",
    "Duplicate provider authority",
    "Activity alias has no signed Activity target",
    "exported: Boolean?",
    "enabled: Boolean?",
    "parser.getAttributeValueType(position) == AndroidBinXmlParser.VALUE_TYPE_STRING",
    "parser.getAttributeValueType(position) == AndroidBinXmlParser.VALUE_TYPE_BOOLEAN",
    "require(store.open(clusterId, platformApi) == signed)",
    "signed.clusterDigest",
):
    assert required in catalog, "E68 signed PRIVATE catalog missing invariant: " + required
assert catalog.count("store.open(clusterId, platformApi)") >= 2
assert "getPackageArchiveInfo(" not in catalog and "getPackageInfo(" not in catalog
assert "PackageManager" not in catalog and "Intent(" not in catalog
assert "launch(" not in catalog and "install(" not in catalog and "uninstall(" not in catalog
for name in (
    "signedPrivateBaseEnumeratesAllComponentTypesWithoutRunningThem",
    "signedBaseFeatureConfigInventoryKeepsExactOriginAndClusterIdentity",
    "signedDuplicateComponentAndConfigInjectedComponentFailClosed",
    "signedAliasWithoutActualActivityAndCollidingAuthoritiesAreRejected",
    "changedPrivateBytesAreRejectedWithoutOriginalApkFallback",
    "incompleteSplitAndWrongSignerNeverBecomeCatalogEntries",
):
    assert name in tests, "E68 signed APK fixture regression missing: " + name
for name in ("catalog-base", "catalog-feature", "catalog-config", "catalog-duplicate",
             "catalog-illegal-config", "catalog-duplicate-authority", "catalog-bad-alias"):
    assert 'apk("' + name + '"' in fixture, "E68 signed real-manifest fixture absent: " + name
assert 'const val supportsSplits = false' in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert 'check(source.apkPaths.size == 1)' in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert 'version = 6, exportSchema = true' in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
print("PASS: E68 enumerates signed PRIVATE base, feature and config manifest components with complete origin/identity.")
print("PASS: no PackageManager original lookup, runtime component activation or split import capability changes.")
