#!/usr/bin/env python3
"""E53: headless recovery must not discard or requeue retained/private PREPARING source."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
recovery = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateJournalRecovery.kt")
worker = read("app/src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryWorker.kt")
core_tests = read("core/src/test/kotlin/vn/fonesi/core/update/UpdateJournalRecoveryTest.kt")
android_tests = read("app/src/test/java/vn/fonesi/app/update/UpdateJournalRecoveryWorkerTest.kt")
policy = read("app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt")
body = recovery.split("TaskState.PREPARING -> {", 1)[1].split("TaskState.APPLYING", 1)[0]
for token in (
    "if (old.retainedSource) continue",
    "allowPreparingCleanup(old)",
    "retryable = true",
    "if (!allowCleanup) continue",
    "snapshots.discard(old.id)",
    "store.replace(old, queued)",
):
    assert token in body, "E53 missing fail-closed PREPARING recovery guard: " + token
assert body.index("if (old.retainedSource) continue") < body.index("allowPreparingCleanup(old)") < body.index("snapshots.discard(old.id)")
assert body.index("if (!allowCleanup) continue") < body.index("snapshots.discard(old.id)")
assert "allowPreparingCleanup = RoomLegacyUpdateSourcePolicy(database)::allows" in worker
assert "ClusterOwnerType.UPDATE_TASK.name" in policy and "ClusterOwnerType.REVIEW_REQUIRED.name" in policy
for blocked in ("RuntimeBindings", "LocalServices", "FCore"):
    # Comment text is non-authoritative, so check the worker imports and execution body.
    assert "import " + blocked not in worker and "import vn.fonesi.app.runtime." + blocked not in worker
for t in (
    "retainedPreparingSnapshotIsHeldWithoutAskingLegacyOwnerPolicy",
    "legacyOwnerPreparingSnapshotIsHeldWhenOriginalSourceIsDenied",
    "unreadablePreparingSourceAuthorityRetainsSnapshotAndRequestsRetry",
    "preparingRecoveryDeletesIncompleteSnapshotThenRequeuesWithClearedMarkers",
):
    assert t in core_tests, "Missing E53 JVM regression: " + t
assert "workerRetainsMarkedAndOwnerOnlyPreparingSourcesWhileResettingUnownedLegacy" in android_tests
assert "TaskState.REVIEW_REQUIRED" in android_tests and "oldReviewOwner" in android_tests
assert "SourceCatalog { null }" in worker, "Worker must not discover an installed original-app source"
assert "engine.update(" not in worker and "engine.launch(" not in worker
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E53 background PREPARING recovery holds retained and E41-owned sources before snapshot discard.")
print("PASS: unavailable Room owner authority remains retryable; legacy and unknown-outcome regressions required.")
