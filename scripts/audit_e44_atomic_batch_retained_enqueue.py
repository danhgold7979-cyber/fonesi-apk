#!/usr/bin/env python3
"""E44 fail-closed static guards for batch atomic verified private-source queue."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "app/src/main/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuer.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/PrivateClusterRetainedBatchEnqueuerTest.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()
db = (ROOT / "app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt").read_text()

for marker in (
    "data class RetainedClusterTarget(",
    "fun enqueueBatch(",
    "internal fun enqueueBatchReadBackVerified(",
    "targets.forEach { requireCanonicalUuid(it.clusterId) }",
    "val cluster = store.open(request.clusterId, platformApi)",
    "target.source.packageName == source.packageName",
    "target.source.versionCode == source.versionCode",
    "target.source.signerDigests == source.signerDigests",
    "UpdateTarget(source, target.affectedCloneIds.toList())",
    "UpdateTask.create(engineKey, target, scope, 0L).dedupKey",
    "dedupKeys.distinct().size == dedupKeys.size",
    "return db.runInTransaction(Callable {",
    "enqueueReadBackVerified(engineKey, target, scope, cluster)",
):
    if marker not in source:
        raise SystemExit("E44 batch verification/atomicity missing: " + marker)

for marker in (
    "twoSourceBatchIsIdempotentAndSurvivesDatabaseReopen",
    "secondOwnerSqlAbortRollsBackAllNewTasksAndOwners",
    "secondGenerationConflictRollsBackEarlierNewTarget",
    "secondSourceIdentityMismatchRejectsWholeBatchBeforeMutation",
    "duplicateDedupIdentityWithinBatchIsRejectedWithoutWrites",
    "separatePerCloneTasksMaySafelyRetainSameVerifiedCluster",
    "publicBatchCannotUseUnpublishedOrMalformedPrivateCluster",
    "inProgressSecondTaskCannotPartiallyAddFirstTask",
):
    if marker not in tests:
        raise SystemExit("E44 regression test is missing: " + marker)

if "version = 6, exportSchema = true" not in db:
    raise SystemExit("E44 must keep the reviewed Room v6 schema")
if "const val supportsSplits = false" not in runtime or "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("E44 cannot enable production provider split import")
for forbidden in ("engine.update(", "engine.launch(", "Files.delete(", "fun collectGarbage(", "fun deleteCluster(",
                  "store.publish(", "engine.install(", "engine.create("):
    if forbidden in source:
        raise SystemExit("E44 must not activate a guest engine or delete private APK bytes: " + forbidden)
print("PASS: E44 verifies every selected private source before atomic Room batch enqueue.")
print("PASS: rollback, idempotence, malformed sources and in-progress conflicts have tests.")
print("PASS: no provider activation, split import, Room migration or private cluster GC enabled.")
