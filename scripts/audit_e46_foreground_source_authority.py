#!/usr/bin/env python3
"""E46 source authority: auto updater must never reconcile private tasks from original-app state."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
code = (ROOT / "app/src/main/java/vn/fonesi/app/update/ForegroundAutoUpdater.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/update/ForegroundAutoUpdaterTest.kt").read_text()
policy = (ROOT / "app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt").read_text()
marker = "if (!mayReconcileOriginalSource(job)) continue"
assert code.count(marker) == 2, "Must guard both superseded-source cancel and deferred retry"
assert "RoomLegacyUpdateSourcePolicy(db)" in code
assert "runCatching { originalSourcePolicy.allows(task) }.getOrDefault(false)" in code
for token in ("ClusterOwnerType.UPDATE_TASK.name", "ClusterOwnerType.REVIEW_REQUIRED.name", "!task.retainedSource"):
    assert token in policy, "Missing E45 original source authority requirement: " + token
for token in (
    "retainedPrivateQueueIsNotCancelledByOriginalAppVersionChange",
    "preMarkerPrivateOwnerTypesAreNotCancelledByOriginalAppPreview",
    "oldQueuedSourceIsSupersededWithoutTouchingAnInflightTask",
    "uncertainApplyIsNeverAutomaticallyReplayed",
):
    assert token in tests, "E46 regression coverage missing: " + token
assert "fun reconcileSafeQueuedWork" in code and "fun retrySafeDeferred" in code
print("PASS: E46 foreground original-source reconciliation skips retained/owner-protected tasks.")
print("PASS: existing legacy source handling and uncertain-result tests retained.")
