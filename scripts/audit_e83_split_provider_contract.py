#!/usr/bin/env python3
"""Static E83 guard: typed split provider contract must not enable unsupported SpaceCore imports."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

engine = read("core/src/main/kotlin/vn/fonesi/core/CloneEngine.kt")
backend = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/EngineCapabilitiesTest.kt")
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
    "splitProviderCapabilitiesRequirePreflightDispatchAndCompleteReadback",
    "splitClusterIdentityRequiresOneBaseUniqueMembersAndAcyclicDependencies",
    "splitClusterIdentityRequiresCanonicalNamesAndFeatureConfigRelationships",
    "splitClusterReadbackRequiresExactIdentityAndCompleteSdkOwnedMemberSet",
    "engineCannotClaimSplitImportWithoutCompleteProviderEvidence",
    "splitSourceIsRejectedBeforeSharedUpdateMutation",
    "backendCannotAdvertiseSplitCapabilityWithoutTypedProvider",
):
    assert marker in tests, "E83 regression test missing: " + marker

assert "audit_e83_split_provider_contract.py" in workflow, "E83 audit is not wired into exact-source CI"
print("PASS E83 contract audit: split import remains fail-closed; pinned SpaceCore remains single-APK only.")
print("NOT VERIFIED: no production split-capable SDK/provider or guest split execution is accepted by this audit.")
