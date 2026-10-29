#!/usr/bin/env python3
"""E47: never enqueue original-app jobs that conflict with active retained private sources."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
code = (ROOT / "app/src/main/java/vn/fonesi/app/update/UpdateRequestService.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/update/UpdateRequestServiceTest.kt").read_text()
policy = (ROOT / "app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt").read_text()
assert "RoomLegacyUpdateSourcePolicy(db)" in code
assert "private fun rejectPrivateSourceConflicts(targets: List<UpdateTarget>)" in code
assert "!task.state.terminal && !originalSourcePolicy.allows(task)" in code
assert code.index("rejectPrivateSourceConflicts(current.plan.targets)") < code.index("val jobs = UpdateQueue(tasks).enqueue(")
assert "db.runInTransaction(Callable {" in code
for token in ("!task.retainedSource", "ClusterOwnerType.UPDATE_TASK.name", "ClusterOwnerType.REVIEW_REQUIRED.name"):
    assert token in policy, "Missing source authority constraint: " + token
for test in (
    "activeRetainedMarkerBlocksDifferentOriginalVersionBeforeQueueMutation",
    "preMarkerOwnerReferencesBlockOriginalSourceConsent",
    "mixedPrivateAndOriginalGroupConsentIsAllOrNothing",
    "unrelatedPrivateSourceAndUnownedLegacyWorkStillAllowConsent",
    "enqueueFailureInSecondGroupRollsBackFirstGroupToo",
    "confirmedPreviewEnqueuesOnceAndRepeatedConsentDeduplicates",
):
    assert test in tests, "E47 native-SQLite regression absent: " + test
print("PASS: E47 original-source confirmation preflights active private authority transactionally.")
print("PASS: E47 new native-SQLite tests and prior consent/rollback regressions retained.")
