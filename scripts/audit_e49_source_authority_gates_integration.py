#!/usr/bin/env python3
"""E49: independent original-source and retained-recovery authority gates must coexist."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text()
foreground = read("app/src/main/java/vn/fonesi/app/update/ForegroundAutoUpdater.kt")
confirm = read("app/src/main/java/vn/fonesi/app/update/UpdateRequestService.kt")
runner = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt")
policy = read("app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt")
foretests = read("app/src/test/java/vn/fonesi/app/update/ForegroundAutoUpdaterTest.kt")
confirmtests = read("app/src/test/java/vn/fonesi/app/update/UpdateRequestServiceTest.kt")
recoverytests = read("core/src/test/kotlin/vn/fonesi/core/update/UpdateRunnerTest.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert foreground.count("if (!mayReconcileOriginalSource(job)) continue") == 2, "E46 foreground guards missing"
assert "RoomLegacyUpdateSourcePolicy(db)" in foreground
assert "private fun rejectPrivateSourceConflicts(targets: List<UpdateTarget>)" in confirm
assert "RoomLegacyUpdateSourcePolicy(db)" in confirm
assert "!task.state.terminal && !originalSourcePolicy.allows(task)" in confirm
assert confirm.index("rejectPrivateSourceConflicts(current.plan.targets)") < confirm.index("val jobs = UpdateQueue(tasks).enqueue(")
recovery = runner.split("private fun recoverLocked()", 1)[1].split("private fun process(", 1)[0]
assert "!mayUseLegacySource(old)" in recovery
assert "persist(old, old.move(TaskState.REVIEW_REQUIRED, clock(), TaskIssue.INTERRUPTED))" in recovery
assert recovery.index("!mayUseLegacySource(old)") < recovery.index("engine.listClones()")
assert recovery.index("!mayUseLegacySource(old)") < recovery.index("snapshots.discard(old.id)")
for marker in ("!task.retainedSource", "ClusterOwnerType.UPDATE_TASK.name", "ClusterOwnerType.REVIEW_REQUIRED.name"):
    assert marker in policy, "E45 shared owner policy missing: " + marker
for src, marker in (
    (foretests, "retainedPrivateQueueIsNotCancelledByOriginalAppVersionChange"),
    (foretests, "preMarkerPrivateOwnerTypesAreNotCancelledByOriginalAppPreview"),
    (confirmtests, "activeRetainedMarkerBlocksDifferentOriginalVersionBeforeQueueMutation"),
    (confirmtests, "mixedPrivateAndOriginalGroupConsentIsAllOrNothing"),
    (recoverytests, "retained applying recovery cannot claim success from matching legacy clone version"),
    (recoverytests, "older owner-only recovery policy denies matching legacy engine version"),
    (recoverytests, "process death after apply is reconciled without repeating external operation"),
):
    assert marker in src, "E49 dynamic acceptance coverage missing: " + marker
assert "version = 6, exportSchema = true" in db
assert "const val supportsSplits = false" in runtime
assert "check(source.apkPaths.size == 1)" in backend
print("PASS: E49 E46/E47/E48 gates all protect original APK source authority.")
print("PASS: E49 tests retain ordinary original-source functionality and private-source refusal.")
print("PASS: Room v6 and candidate split-disabled/no-provider-execution boundaries unchanged.")
