#!/usr/bin/env python3
"""E58: private-cluster CLONE owner association shares the real OS lease with stop."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text()
owner = read("app/src/main/java/vn/fonesi/app/storage/CloneClusterOwnershipRepository.kt")
metadata = read("app/src/main/java/vn/fonesi/app/storage/CloneMetadataRepository.kt")
tests = read("app/src/test/java/vn/fonesi/app/storage/CloneClusterOwnershipRepositoryTest.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")

assert "private val operationLock: QueueLock," in owner
public = owner.split("    fun associate(", 1)[1].split("    /** Package-visible test seam", 1)[0]
assert "requireOwnerIdentity(engineKey, cloneId)" in public
assert "requireCanonicalUuid(clusterId)" in public
assert "return withAssociationLock {" in public
assert "store.open(clusterId, platformApi)" in public
assert public.index("return withAssociationLock {") < public.index("store.open(clusterId, platformApi)")
assert "associateReadBackVerifiedLocked(engineKey, cloneId, verified)" in public
seam = owner.split("    internal fun associateReadBackVerified(", 1)[1].split("    private fun <T> withAssociationLock", 1)[0]
assert "return withAssociationLock { associateReadBackVerifiedLocked(engineKey, cloneId, cluster) }" in seam
lock = owner.split("    private fun <T> withAssociationLock(", 1)[1].split("    /** Call only while owning", 1)[0]
for token in ("checkNotNull(operationLock.acquire())", "return lease.use { action() }"):
    assert token in lock, f"E58 fail-closed lease guard missing: {token}"
locked = owner.split("    private fun associateReadBackVerifiedLocked(", 1)[1].split("    /**\n     * Do not reacquire operationLock", 1)[0]
for token in ("validateCluster(cluster)", "db.runInTransaction(Callable {",
              "db.clusterReferences().insert(", "db.clones().associateCluster(",
              "db.clusterReferences().delete(oldId, ownerType, cloneId, engineKey)"):
    assert token in locked, f"E58 must retain atomic E40 reference checks: {token}"
delete = owner.split("    fun preflightDeletion(", 1)[1].split("    private fun checkCloneReferences(", 1)[0]
assert "operationLock.acquire()" not in delete, "Delete caller already holds the lease: no deadlock"
assert "CloneClusterOwnershipRepository(db, operationLock, clock)" in metadata
assert "val lease = checkNotNull(operationLock.acquire())" in metadata
assert "ownership.preflightDeletion(engineKey, id, saved.launchToken)" in metadata
assert "ownership.deleteMetadataAfterProviderConfirmed(engineKey, id, saved.launchToken)" in metadata
assert "private fun operationLock()" in tests and "FileQueueLock(" in tests
for name in (
    "heldStopLeaseRejectsAssociationWithoutChangingMetadataOrReferences",
    "publicAssociationChecksOperationLeaseBeforeUnpublishedPrivateStoreReadback",
    "validationFailureReleasesLeaseAndGenerationSwitchPreservesOtherOwners",
    "concurrentStopCriticalSectionRefusesAssociationUntilLeaseReleased",
    "sqliteAbortDuringAssociationRollsBackNewOwnerReference",
):
    assert name in tests, f"Missing E58 native SQLite regression: {name}"
assert "version = 6, exportSchema = true" in db
assert "const val supportsSplits = false" in runtime
assert "check(source.apkPaths.size == 1)" in backend
for forbidden in ("engine.launch(", "engine.update(", "fun collectGarbage(", "fun deleteCluster(", "Files.delete("):
    assert forbidden not in owner, f"E58 must not enable provider or cluster deletion: {forbidden}"
print("PASS: E58 public store-readback and internal association share an OS lock with clone stop.")
print("PASS: failed/across-engine/lease/contention test coverage, atomic Room and disabled split boundaries.")
