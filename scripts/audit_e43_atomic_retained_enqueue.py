#!/usr/bin/env python3
"""Fail-closed E43 audit: verified private source task enqueue is atomic with Room owner."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
enqueuer = (ROOT / "app/src/main/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuer.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuerTest.kt").read_text()
owner = (ROOT / "app/src/main/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepository.kt").read_text()
journal = (ROOT / "app/src/main/java/vn/fonesi/app/storage/RoomUpdateTaskStore.kt").read_text()
db = (ROOT / "app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()

for marker in (
    "class PrivateClusterRetainedEnqueuer(",
    "fun enqueue(",
    "store.open(clusterId, platformApi)",
    "internal fun enqueueReadBackVerified(",
    "requireCanonicalUuid(clusterId)",
    "requireCanonicalUuid(cluster.id)",
    "val verifiedSource = cluster.source()",
    "target.source.signerDigests == verifiedSource.signerDigests",
    "val verifiedTarget = UpdateTarget(verifiedSource, target.affectedCloneIds.toList())",
    "db.runInTransaction(Callable",
    "UpdateQueue(RoomUpdateTaskStore(db), clock)",
    "task.state == TaskState.QUEUED",
    "ClusterOwnerType.REVIEW_REQUIRED.name",
    "UpdateTaskClusterOwnershipRepository(db, clock)",
    ".associateReadBackVerified(engineKey, task.id, cluster)",
):
    if marker not in enqueuer:
        raise SystemExit("E43 verified task/owner guard missing: " + marker)

for marker in (
    "queuedTaskAndOwnerAreAtomicIdempotentAndDurableAcrossReopen",
    "mismatchedRequestedSourceIdentityCannotCreateTaskOrOwner",
    "publicEnqueueRejectsUnpublishedOrMalformedClusterBeforeDatabaseMutation",
    "conflictingClusterGenerationCannotReplaceExistingQueuedSource",
    "existingInProgressTaskCannotAcquireOrSwitchOwner",
    "sqliteReferenceInsertionFailureRollsBackNewQueueRowAndRetriesCleanly",
    "queuedTaskWithUnexpectedReviewOwnerFailsClosed",
    "scopeAndEngineNamespaceAreVerifiedWithoutCreatingPartialOwner",
):
    if marker not in tests:
        raise SystemExit("E43 acceptance test missing: " + marker)

if "store.open(clusterId, platformApi)" not in owner:
    raise SystemExit("Public E41 owner API must retain cryptographic PrivateApkStore readback")
if "private val atomicOwnerTransitions: Boolean = true" not in journal:
    raise SystemExit("E42 atomic review/terminal owner journal transitions lost")
if "version = 6, exportSchema = true" not in db:
    raise SystemExit("E43 must not alter the Room schema")
if "const val supportsSplits = false" not in runtime or "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("Production provider split disabled guard missing")
for forbidden in ("engine.update(", "engine.launch(", "fun collectGarbage(", "fun deleteCluster(",
                  "Files.delete(", "store.publish(", "engine.install(", "engine.create("):
    if forbidden in enqueuer:
        raise SystemExit("E43 must not invoke a guest engine or delete/publish APK: " + forbidden)
print("PASS: E43 verifies a private source before transactional task enqueue and owner insertion.")
print("PASS: duplicate/conflicting queue state and SQL-abort recovery are required test cases.")
print("PASS: Room v6, single-APK candidate and no automatic guest/GC are preserved.")
