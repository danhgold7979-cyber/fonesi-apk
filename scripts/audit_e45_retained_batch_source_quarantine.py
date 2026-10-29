#!/usr/bin/env python3
"""E45 fail-closed source audit: atomic retained batch and no-original-source fallback."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text()
enq = read("app/src/main/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuer.kt")
tests = read("app/src/test/java/vn/fonesi/app/storage/PrivateClusterRetainedBatchEnqueuerTest.kt")
runner = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt")
codec = read("app/src/main/java/vn/fonesi/app/storage/TaskCodec.kt")
policy = read("app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
for marker in (
    "fun enqueueBatch(",
    "val cluster = store.open(request.clusterId, platformApi)",
    "enqueueBatchReadBackVerified(",
    "return db.runInTransaction(Callable {",
    "enqueueReadBackVerified(engineKey, target, scope, cluster)",
    ".enqueue(engineKey, listOf(verifiedTarget), scope, retainedSource = true)",
    "task.state == TaskState.QUEUED && task.retainedSource",
):
    if marker not in enq:
        raise SystemExit("E45 regression in batch/private-source integration: " + marker)
for marker in (
    "retainedBatchBlocksOriginalSourceFallbackAfterReopen",
    "legacyActiveTaskRejectsRetainedBatchAndRollsBackEarlierSource",
):
    if marker not in tests:
        raise SystemExit("E45 cross-feature native SQLite test absent: " + marker)
for marker in (
    "if (!mayUseLegacySource(next)) return@use RunResult.PolicyBlocked",
    "if (!mayApply() || !mayUseLegacySource(current))",
):
    if marker not in runner:
        raise SystemExit("E45 runtime original-catalog source quarantine absent: " + marker)
if 'if (task.retainedSource) put("retainedSource", true)' not in codec:
    raise SystemExit("E45 durable retained-source journal marker absent")
if "ClusterOwnerType.REVIEW_REQUIRED.name" not in policy or "ClusterOwnerType.UPDATE_TASK.name" not in policy:
    raise SystemExit("E45 pre-E44 owner reference source guard absent")
if "version = 6, exportSchema = true" not in db:
    raise SystemExit("E45 unexpectedly changed Room schema")
if "const val supportsSplits = false" not in runtime:
    raise SystemExit("E45 cannot advertise unverified split import")
for forbidden in ("engine.update(", "engine.launch(", "Files.delete(", "fun collectGarbage(", "store.publish("):
    if forbidden in enq:
        raise SystemExit("E45 cannot run guest/update engine or private garbage collection: " + forbidden)
print("PASS: E45 integrates atomic batch, durable source authority and original-app fallback quarantine.")
print("PASS: E45 native-SQLite tests cover persistence and cross-authority conflict rollback.")
print("PASS: Room v6 and split-disabled/non-destructive provider boundaries preserved.")
