#!/usr/bin/env python3
"""Fail-closed E48 audit: no original guest/source readback during retained recovery."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text()
runner = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/update/UpdateRunnerTest.kt")
room = read("app/src/main/java/vn/fonesi/app/storage/RoomUpdateTaskStore.kt")
policy = read("app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "private fun recoverLocked()" in runner and "private fun process(" in runner
recovery = runner.split("private fun recoverLocked()", 1)[1].split("private fun process(", 1)[0]
for marker in (
    "old.state in setOf(TaskState.PREPARING, TaskState.APPLYING,",
    "TaskState.VERIFYING, TaskState.REVIEW_REQUIRED)",
    "!mayUseLegacySource(old)",
    "if (old.state == TaskState.APPLYING || old.state == TaskState.VERIFYING)",
    "persist(old, old.move(TaskState.REVIEW_REQUIRED, clock(), TaskIssue.INTERRUPTED))",
    "continue",
):
    if marker not in recovery:
        raise SystemExit("E48 recovery source authority guard missing: " + marker)
guard = recovery.index("!mayUseLegacySource(old)")
for vulnerable in ("snapshots.discard(old.id)", "engine.listClones()"):
    if vulnerable not in recovery or guard >= recovery.index(vulnerable):
        raise SystemExit("E48 original source guard must precede recovery operation: " + vulnerable)
for marker in (
    "retained applying recovery cannot claim success from matching legacy clone version",
    "older owner-only recovery policy denies matching legacy engine version",
    "recovery authority readback exception keeps uncertain task in review",
    "retained preparing recovery does not discard task snapshot or rewrite task",
    "retained review recovery never converts old metadata into success",
    "process death after apply is reconciled without repeating external operation",
):
    if marker not in tests:
        raise SystemExit("E48 required JVM regression missing: " + marker)
if "transitionExistingOwners(replacement)" not in room or "ClusterOwnerType.REVIEW_REQUIRED.name" not in policy:
    raise SystemExit("E48 Room owner transition/source policy contract changed")
if "version = 6, exportSchema = true" not in db:
    raise SystemExit("E48 must retain Room v6")
if "const val supportsSplits = false" not in runtime or "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("E48 must not enable unverified provider split import")
print("PASS: E48 checks source authority before original legacy recovery and snapshot cleanup.")
print("PASS: retained and owner-only uncertain tasks cannot become legacy-version success.")
print("PASS: Room v6, atomic owner transitions and split-disabled provider preserved.")
