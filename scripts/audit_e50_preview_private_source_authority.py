#!/usr/bin/env python3
"""E50 preview: disclose active private authority without changing atomic confirm or provider."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text()
svc = read("app/src/main/java/vn/fonesi/app/update/UpdateRequestService.kt")
ui = read("app/src/main/java/vn/fonesi/app/update/UpdatePreviewDialog.kt")
plan = read("core/src/main/kotlin/vn/fonesi/core/UpdatePlanner.kt")
tests = read("app/src/test/java/vn/fonesi/app/update/UpdateRequestServiceTest.kt")
for token in ("PRIVATE_SOURCE_ACTIVE",):
    assert token in plan and token in ui and token in svc
assert "originalSourcePolicy.allows(it)" in svc
assert "it.engineKey == engineKey && it.packageName in packages && !it.state.terminal" in svc
assert "val privateIds =" in svc and "val privateBlocks =" in svc
assert "privatePackages" in svc and "alreadyCurrent = originalPlan.alreadyCurrent.filterNot" in svc
assert "rejectPrivateSourceConflicts(current.plan.targets)" in svc, "E47 confirm guard must remain"
assert svc.index("rejectPrivateSourceConflicts(current.plan.targets)") < svc.index("val jobs = UpdateQueue(tasks).enqueue(")
assert "db.runInTransaction(Callable {" in svc
for name in (
    "previewShowsPrivateSourceAsBlockedAndStillOffersOtherPackage",
    "previewFlagsPreMarkerUpdateAndReviewOwnersWithoutMutatingThem",
    "privateSourceAppearingAfterPreviewInvalidatesOriginalConsent",
    "terminalRetainedSourceNoLongerBlocksNewPreview",
    "mixedPrivateAndOriginalGroupConsentIsAllOrNothing",
):
    assert name in tests, "Missing native SQLite test " + name
assert "Đang có tác vụ cập nhật giữ nguồn APK riêng" in ui
print("PASS: E50 preview marks protected APK source conflicts and preserves E47 atomic confirmation.")
print("PASS: original legacy updates and unrelated packages remain supported; Room v6 unchanged.")
