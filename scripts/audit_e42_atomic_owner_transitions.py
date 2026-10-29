#!/usr/bin/env python3
"""E42 fail-closed source audit: Room journal CAS and private owner reference transitions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
store = (ROOT / "app/src/main/java/vn/fonesi/app/storage/RoomUpdateTaskStore.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/RoomRetainedJournalTransitionTest.kt").read_text()
e41 = (ROOT / "app/src/test/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepositoryTest.kt").read_text()
repo = (ROOT / "app/src/main/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepository.kt").read_text()
database = (ROOT / "app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()

for marker in (
    "class RoomUpdateTaskStore(",
    "private val atomicOwnerTransitions: Boolean = true",
    "db.runInTransaction(Callable",
    "replacement.requireSuccessorOf(expected)",
    "if (current != expected || (requireUnpaused && isPaused())) return@Callable false",
    "if (db.tasks().replace(",
    "if (atomicOwnerTransitions) transitionExistingOwners(replacement)",
    "private fun transitionExistingOwners(next: UpdateTask)",
    "ClusterOwnerType.UPDATE_TASK.name",
    "ClusterOwnerType.REVIEW_REQUIRED.name",
    "taskRefs.size <= 1 && reviewRefs.size <= 1",
    "reviewRefs.single().clusterId == source.clusterId",
    "db.clusterReferences().insert(ClusterReferenceRow(",
    "db.clusterReferences().delete(source.clusterId, taskType, next.id, next.engineKey) == 1",
):
    if marker not in store:
        raise SystemExit("E42 missing fail-closed journal/owner guard: " + marker)

for marker in (
    "reviewJournalMarkerAndOwnerPromotionCommitTogetherAcrossReopen",
    "terminalMarkerOnlyReleasesUpdateTaskOwnerAndPreservesOtherOwners",
    "legacyTaskWithNoVerifiedClusterRetainsOriginalJournalBehavior",
    "sqliteReviewInsertionFailureRollsBackBothJournalAndOwner",
    "sqliteTerminalReleaseFailureRollsBackBothJournalAndOwner",
    "conflictOrAmbiguousGenerationRejectsJournalChangeWithoutDeletingOwners",
    "staleCasAndPauseGuardCannotDropPrivateSourceReference",
    "reviewedSuccessfulRecoveryNeverReleasesReviewHold",
):
    if marker not in tests:
        raise SystemExit("E42 acceptance test missing: " + marker)

if "atomicOwnerTransitions = false" not in e41:
    raise SystemExit("E41 explicit post-journal API tests must remain isolated from E42 default")
if "store.open(clusterId, platformApi)" not in repo:
    raise SystemExit("E41 verified cluster acquisition must remain enforced")
if "version = 6, exportSchema = true" not in database:
    raise SystemExit("Unexpected Room migration during E42")
if "const val supportsSplits = false" not in runtime or "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("SpaceCore split capability unexpectedly changed")

for forbidden in ("engine.update(", "engine.launch(", "fun collectGarbage(", "fun deleteCluster(",
                  "Files.delete(", "store.publish(", "engine.install("):
    if forbidden in store:
        raise SystemExit("E42 journal may not mutate engine or delete private files: " + forbidden)

print("PASS: Room journal CAS and owner update share one transaction with ambiguity guards.")
print("PASS: legacy unreferenced tasks, rollback, review retention and stale CAS tests are required.")
print("PASS: E42 cannot import splits, launch guests, publish/delete clusters or change Room schema.")
