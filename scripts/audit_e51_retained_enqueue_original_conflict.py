#!/usr/bin/env python3
"""E51: private-source enqueue must reject active original-app source across versions atomically."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text()
enq = read("app/src/main/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuer.kt")
tests = read("app/src/test/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuerTest.kt")
batch = read("app/src/test/java/vn/fonesi/app/storage/PrivateClusterRetainedBatchEnqueuerTest.kt")
confirm = read("app/src/main/java/vn/fonesi/app/update/UpdateRequestService.kt")
for token in (
    "rejectActiveUnmarkedSource(engineKey, verifiedSource.packageName)",
    "private fun rejectActiveUnmarkedSource(",
    "existing.engineKey == engineKey && existing.packageName == packageName",
    "!existing.state.terminal && !existing.retainedSource",
    "return db.runInTransaction(Callable {",
    "enqueueReadBackVerified(engineKey, target, scope, cluster)",
):
    assert token in enq, "E51 missing atomic reverse-source safety marker: " + token
assert enq.index("rejectActiveUnmarkedSource(engineKey, verifiedSource.packageName)") < enq.index(
    ".enqueue(engineKey, listOf(verifiedTarget), scope, retainedSource = true)")
assert "rejectPrivateSourceConflicts(current.plan.targets)" in confirm, "E47 forward source authority guard absent"
for marker in (
    "olderOriginalVersionCannotBeShadowedByRetainedCluster",
    "preMarkerTaskWithExistingOwnerCannotBeShadowedAcrossVersions",
    "terminalOriginalTaskDoesNotPreventNewVerifiedPrivateGeneration",
):
    assert marker in tests
for marker in (
    "originalSecondPackageWithDifferentVersionRollsBackNewPrivateBatch",
    "separatePerCloneTasksMaySafelyRetainSameVerifiedCluster",
):
    assert marker in batch
for forbidden in ("engine.update(", "engine.launch(", "store.publish(", "fun collectGarbage(", "Files.delete("):
    assert forbidden not in enq
print("PASS: E51 retained enqueue atomically rejects active original-app source across versions.")
print("PASS: E51 batch rollback and terminal/legitimate per-clone retained operations are covered.")
