#!/usr/bin/env python3
"""Static E83 guard: typed split provider contract must not enable unsupported SpaceCore imports."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

engine = read("core/src/main/kotlin/vn/fonesi/core/CloneEngine.kt")
backend = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/EngineCapabilitiesTest.kt")
managed_tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
store = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkStore.kt")
store_tests = read("apk-runtime/src/test/kotlin/vn/fonesi/apk/PrivateApkStoreTest.kt")
parent_verifier = read("scripts/verify_e78_parent_artifact.py")
spacecore = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
bindings = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
workflow = read(".github/workflows/e82-public-component-factory.yml")

for marker in (
    "splitClusterPreflightSupported: Boolean = false",
    "splitClusterInstallDispatchSupported: Boolean = false",
    "splitClusterReadbackVerified: Boolean = false",
    "splitClusterImportSupported: Boolean = false",
    "splitClusterCodeExecutionVerified: Boolean = false",
    "splitClusterAtomicUpdateVerified: Boolean = false",
    "Split import cannot be accepted without preflight, dispatch and complete readback",
    "Split code execution requires an accepted split import provider",
    "Atomic split update requires accepted import and code-execution proof",
):
    assert marker in engine, "E83 engine capability invariant missing: " + marker

for marker in (
    "data class GuestBackendCapabilities(",
    "interface SplitClusterGuestBackend",
    "fun preflightCluster(source: InstalledSource): GuestApkClusterIdentity",
    "fun installCluster(userId: Int, source: InstalledSource, expected: GuestApkClusterIdentity): GuestApkClusterReadback",
    "fun inspectCluster(userId: Int, packageName: String): GuestApkClusterReadback?",
    "data class GuestApkClusterIdentity(",
    "fun requireStructurallyValid(): GuestApkClusterIdentity",
    "fun computeCanonicalDigest(): String",
    "clusterDigest == computeCanonicalDigest()",
    "val byteLength: Long",
    "val signerDigests: Set<String>",
    "val splitRequired: Boolean = false",
    "val requiredTypes: Set<String> = emptySet()",
    "val providedTypes: Set<String> = emptySet()",
    "val isolatedSplits: Boolean = false",
    "member.byteLength > 0",
    "member.signerDigests == signerDigests",
    "packageName.length <= 255",
    "member.configForSplit?.takeIf { it.isNotEmpty() }",
    "Split dependency graph contains a cycle",
    "splitClusterPreflightSupported = false",
    "splitClusterInstallDispatchSupported = false",
    "splitClusterReadbackVerified = false",
    "splitClusterImportSupported = false",
    "splitClusterCodeExecutionVerified = false",
    "splitClusterAtomicUpdateVerified = false",
    "reported.splitClusterPreflightSupported ||",
    "backend is SplitClusterGuestBackend",
    "fun requireMatches(expected: GuestApkClusterIdentity): GuestApkClusterReadback",
    "identity == expected",
    "sdkOwnedPrivateStorageVerified",
    "completeMemberSetVerified",
    "interface SplitClusterGuestBackend : GuestBackend",
    "if (source.apkPaths.size != 1) throw UnsupportedSourceException()",
):
    assert marker in backend, "E83 typed backend contract missing: " + marker

for marker in (
    "splitClusterPreflightSupported = false",
    "splitClusterInstallDispatchSupported = false",
    "splitClusterReadbackVerified = false",
    "splitClusterCodeExecutionVerified = false",
    "splitClusterAtomicUpdateVerified = false",
    "check(source.apkPaths.size == 1)",
    "core.installPackageAsUser(input, userId)",
):
    assert marker in spacecore, "Pinned SpaceCore single-APK guard missing: " + marker
assert "const val supportsSplits = false" in bindings

for marker in (
    "val identity: GuestApkClusterIdentity",
    "byteLength = member.file.length()",
    "val clusterDigest: String get() = identity.clusterDigest",
):
    assert marker in store, "E83 private-store identity parity missing: " + marker

for marker in (
    "splitProviderCapabilitiesRequirePreflightDispatchAndCompleteReadback",
    "splitClusterIdentityRequiresOneBaseUniqueMembersAndAcyclicDependencies",
    "splitIdentityMatchesAuditorConfigDependencyAndSplitTypeRules",
    "splitIdentityDigestCommitsEveryMetadataFieldAndMemberLength",
    "splitClusterIdentityRequiresCanonicalNamesAndFeatureConfigRelationships",
    "splitClusterReadbackRequiresExactIdentityAndCompleteSdkOwnedMemberSet",
    "engineCannotClaimSplitImportWithoutCompleteProviderEvidence",
):
    assert marker in tests, "E83 regression test missing: " + marker

for marker in (
    "splitSourceIsRejectedBeforeSharedUpdateMutation",
    "backendCannotAdvertiseSplitCapabilityWithoutTypedProvider",
):
    assert marker in managed_tests, "E83 engine regression test missing: " + marker

for marker in (
    "identityMatchesAllAuditorMetadataAndPreservesLegacyDigestBytes",
    "requiredTypesSplitRequiredAndIsolatedFlagsSurvivePrivateReadback",
    "legacyClusterDigest",
):
    assert marker in store_tests, "E83 private-store parity regression missing: " + marker

for marker in (
    "EXPECTED_SOURCE_SHA = \"57d60cb80d6cece692b2898afd15d8668fd14883\"",
    "EXPECTED_RESULT = \"PASS_E78_SIGNED_SYNTHETIC_APPLICATION_ATTACH_AND_SCOPED_DATA_ONLY\"",
    "guest_application_registered_with_package_manager",
    "kernel_uid_isolation",
):
    assert marker in parent_verifier, "Pinned E78 parent receipt validation missing: " + marker

for marker in (
    "actions: read",
    "Download and verify pinned E78 parent artifact",
    "12952618461",
    "40998462184",
    "57d60cb80d6cece692b2898afd15d8668fd14883",
    "app/build/reports/e78-parent/e11-evidence.json",
):
    assert marker in workflow, "E82 parent evidence packaging missing: " + marker
assert "Upload E78 parent receipt and sanitized test receipts" not in workflow
assert "verify_e78_parent_artifact.py" in workflow
print("PASS E83 contract audit: canonical audited identity/digest, fail-closed split import and pinned E78 parent evidence verified.")
print("NOT VERIFIED: no production split-capable SDK/provider or guest split execution is accepted by this audit.")
