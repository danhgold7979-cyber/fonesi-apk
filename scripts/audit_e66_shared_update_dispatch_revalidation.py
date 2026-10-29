#!/usr/bin/env python3
"""E66: shared native guest code update cannot use stale peer/identity readback."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda name: (root / name).read_text()
engine = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
update = engine.split("    override fun update(target: UpdateTarget)", 1)[1].split("    /** Missing native readback", 1)[0]
for phrase in (
    "val current = sharedRecords(target.source.packageName)",
    "current.map { it.id }.toSet() == target.affectedCloneIds.toSet()",
    "it.running == false",
    "it.versionCode <= target.source.versionCode",
    "it.signerDigests == target.source.signerDigests",
    "val originalPeers = active().filter { it.packageName == target.source.packageName }",
    "originalPeers.size == current.size",
    "val first = instance(current.first().id)",
    "mutationAllowed()",
    "val dispatchPeers = active().filter { it.packageName == target.source.packageName }",
    "dispatchPeers.toSet() == originalPeers.toSet()",
    "val dispatchRecords = sharedRecords(target.source.packageName)",
    "dispatchRecords.toSet() == current.toSet()",
    "instance(first.id) == first",
    "backend.install(first.userId, target.source)",
    "val observed = sharedRecords(target.source.packageName)",
):
    assert phrase in update, "E66 missing shared SDK pre-dispatch safety: " + phrase
assert update.index("val current = sharedRecords(") < update.index("mutationAllowed()")
assert update.index("mutationAllowed()") < update.index("val dispatchPeers =")
assert update.index("val dispatchPeers =") < update.index("val dispatchRecords =") < update.index("backend.install(")
assert update.index("instance(first.id) == first") < update.index("backend.install(") < update.index("val observed = sharedRecords(")
for test in (
    "sharedUpdateRejectsNativeVersionDriftAfterAuthorization",
    "sharedUpdateRejectsNativeSigningDriftAfterAuthorization",
    "sharedUpdateRejectsPeerRunningAfterAuthorization",
    "sharedUpdateRejectsNewReservedPeerAfterAuthorization",
    "sharedUpdateRejectsNativeUserIdReassignmentAfterAuthorization",
    "sharedUpdateImportsOnceAndVerifiesAllAffectedInstances",
    "sharedUpdateRejectsIncompleteScopeBusyInstancesAndDowngrade",
    "partialUpdateReadbackFailsWithoutReinstallOrDeletingAccountData",
):
    assert test in tests, "E66 missing JVM regression: " + test
assert "check(row.state == InstanceState.READY)" in engine.split("    private fun sharedRecords(", 1)[1]
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
print("PASS: E66 rejects stale native guest user/scope/version/signers/running changes before SDK shared install.")
print("PASS: confirmed shared update and post-install readback remain, split/provider/GC not activated.")
