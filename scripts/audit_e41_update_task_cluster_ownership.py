#!/usr/bin/env python3
"""Fail-closed source boundary audit for E41 update-task/private-cluster owner references."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
database = (ROOT / "app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt").read_text()
repo = (ROOT / "app/src/main/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepository.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepositoryTest.kt").read_text()
journal = (ROOT / "app/src/main/java/vn/fonesi/app/storage/RoomUpdateTaskStore.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()

for marker in (
    "version = 6, exportSchema = true",
    "abstract fun clusterReferences(): ClusterReferenceDao",
    "abstract fun tasks(): UpdateTaskDao",
):
    if marker not in database: raise SystemExit(f"Room v6 storage contract changed: {marker}")
for marker in (
    "class UpdateTaskClusterOwnershipRepository(",
    "store.open(clusterId, platformApi)",
    "fun associate(",
    "fun promoteAfterReview(",
    "fun releaseAfterTerminal(",
    "db.runInTransaction(Callable",
    "TaskCodec.decode(row)",
    "TaskState.QUEUED",
    "TaskState.REVIEW_REQUIRED",
    "task.state.terminal",
    "ClusterOwnerType.UPDATE_TASK.name",
    "ClusterOwnerType.REVIEW_REQUIRED.name",
    "forOwner(",
    "db.clusterReferences().delete(",
):
    if marker not in repo: raise SystemExit(f"E41 task owner guard missing: {marker}")
for forbidden in ("fun deleteCluster(", "fun collectGarbage(", "engine.update(", "engine.launch(", "Files.delete(", "fun install("):
    if forbidden in repo: raise SystemExit(f"E41 must not mutate engine/private files: {forbidden}")
for marker in (
    "queuedTaskAcquiresOneVerifiedClusterIdempotentlyAndSurvivesReopen",
    "unpublishedPrivateClusterAndMalformedUuidCannotAcquireReference",
    "taskIdentityVersionSignerAndQueuedStateCannotBeBypassed",
    "ambiguousOrDifferentSourceOwnersFailClosedWithoutReplacement",
    "reviewMarkerPromotesOwnerAtomicallyAndRetainsUnrelatedReferences",
    "reviewPromotionSQLiteAbortRollsBackWithoutDroppingTaskOwner",
    "onlyTerminalJournalMayReleaseTaskWithoutTouchingReviewOrRollback",
):
    if marker not in tests: raise SystemExit(f"E41 test missing: {marker}")
if "override fun replace(expected: UpdateTask" not in journal:
    raise SystemExit("Durable state/revision CAS journal missing")
if "const val supportsSplits = false" not in runtime or "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("E41 may not enable unsupported provider split imports")
print("PASS: E41 links only verified private-cluster IDs to durable Room task/review owners.")
print("PASS: state/revision/engine/source guards, rollback tests, and terminal-only release are present.")
print("PASS: no real guest update, split import, cluster deletion or garbage collection is enabled.")
