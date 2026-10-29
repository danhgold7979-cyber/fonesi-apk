#!/usr/bin/env python3
"""E69: signed private component manifest bound to source Android code loader only."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda name: (root / name).read_text()
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
preflight = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestComponentPreflight.kt")
tests = read("engine-source/src/test/java/vn/fonesi/engine/PrivateGuestComponentPreflightTest.kt")
catalog = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkComponentCatalog.kt")
for phrase in (
    "val layout = GuestCodeLayout.read(apks, clusterId, Build.VERSION.SDK_INT, owned, host.packageName)",
    "PrivateApkComponentCatalog.inspect(apks, clusterId, Build.VERSION.SDK_INT)",
    "PrivateGuestComponentPreflight.requireMatches(layout",
    "val componentInventory: VerifiedPrivateComponentInventory",
    "check(apks.open(clusterId, Build.VERSION.SDK_INT) == layout.cluster && preparer.verify(native))",
    "Private component manifest changed during guest code preparation",
    "loader, resources, components",
    "NativeRuntime.requireGuestProcess(host)",
    "attempted.compareAndSet(false, true)",
):
    assert phrase in loader, "E69 source loader must require: " + phrase
body = loader.split("    fun load(clusterId: String)", 1)[1].split("    private fun privateDirectory", 1)[0]
assert body.index("GuestCodeLayout.read(") < body.index("val components =") < body.index("val loader = DexClassLoader(")
assert body.count("PrivateApkComponentCatalog.inspect(") == 2
assert body.index("val resources =") < body.index("Private component manifest changed")
for phrase in (
    "inventory.clusterId == cluster.id",
    "inventory.clusterDigest == cluster.clusterDigest",
    "inventory.packageName == base.packageName",
    "inventory.versionCode == base.version",
    "inventory.signerDigests == base.signers",
    "inventory.splitNames == names",
    "layout.splitNames.toSet() == names.toSet()",
    "layout.splits.toSet() == cluster.apks.splits.map { it.file }.toSet()",
    "it.sourceSplit in names",
):
    assert phrase in preflight, "E69 code/manifest identity check missing: " + phrase
for name in (
    "singleSignedPrivateBaseCatalogMatchesExactlyItsLoadedCodeLayout",
    "signedPrivateBaseFeatureConfigManifestKeepsAllSourceSplitIdentities",
    "differentClusterEvenWithSamePackageAndSignersCannotImpersonatePrivateCodeImage",
    "alteredSignerVersionSplitOrInventedComponentOriginIsRejected",
    "isolatedSignedGraphCanBeCataloguedButProductionCodeLoaderRemainsBlocked",
    "modifiedPrivateApkFailsVerificationInsteadOfUsingOriginalCode",
):
    assert name in tests, "E69 signed private package regression missing: " + name
assert "GuestCodeLayout.read(" in loader and "allowGraphOnly = false" in read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLayout.kt")
assert "fun inspect(store: PrivateApkStore" in catalog
assert 'const val supportsSplits = false' in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert 'check(source.apkPaths.size == 1)' in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "LoadedGuestCode" in loader and "Activity(" not in loader and "Application(" not in loader
print("PASS: E69 source loader and verified component catalog bind to same signed PRIVATE base/split code identity.")
print("PASS: no guest component registration, isolated-split activation or SDK production split import.")
