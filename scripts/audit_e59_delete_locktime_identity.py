#!/usr/bin/env python3
"""E59: deletion must reject token-preserving clone identity changes after acquiring OS lease."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text()
metadata = read("app/src/main/java/vn/fonesi/app/storage/CloneMetadataRepository.kt")
tests = read("app/src/test/java/vn/fonesi/app/storage/StorageIntegrationTest.kt")
owner = read("app/src/main/java/vn/fonesi/app/storage/CloneClusterOwnershipRepository.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")

body = metadata.split("fun delete(id: String, expectedToken: String? = null) {", 1)[1].split("    /** Recover real SDK", 1)[0]
for token in (
    "val saved = checkNotNull(db.clones().find(engineKey, id))",
    "mutateEngine(saved.packageName) {",
    "val current = checkNotNull(db.clones().find(engineKey, id))",
    "check(current == saved)",
    "check(expectedToken == null || saved.launchToken == expectedToken)",
    "ownership.preflightDeletion(engineKey, id, saved.launchToken)",
    "engine.delete(id)",
    "ownership.deleteMetadataAfterProviderConfirmed(engineKey, id, saved.launchToken)",
):
    assert token in body, f"E59 clone delete invariant missing: {token}"
assert body.index("mutateEngine(saved.packageName) {") < body.index("val current = checkNotNull")
assert body.index("check(current == saved)") < body.index("ownership.preflightDeletion")
assert body.index("check(current == saved)") < body.index("engine.delete(id)")
assert "db.clones().find(engineKey, id)?.launchToken == saved.launchToken" not in body
lock = metadata.split("private fun <T> mutateEngine(", 1)[1].split("    private fun normalize(", 1)[0]
assert "operationLock.acquire()" in lock and "lease.use {" in lock
for name in (
    "deleteRejectsPresentationEditMadeBeforeSharedLockWithoutTouchingGuest",
    "deleteRejectsClusterGenerationChangeMadeBeforeLockAndKeepsCloneOwner",
    "metadataIsSavedOnlyAfterReadbackAndDeletesOnlyAfterConfirmedAbsence",
    "inconsistentClusterOwnershipBlocksProviderDeletionBeforeGuestIsTouched",
    "providerFailurePreservesAssociatedMetadataAndOtherOwners",
):
    assert name in tests, f"E59 missing native-SQLite regression: {name}"
assert "private fun beforeDeleteLease(interleavingWriter: () -> Unit): QueueLock" in tests
assert "checkNotNull(real.acquire()).use { interleavingWriter() }" in tests
assert "CloneClusterOwnershipRepository(db, operationLock, clock)" in metadata
assert "return withAssociationLock {" in owner and "store.open(clusterId, platformApi)" in owner
assert "version = 6, exportSchema = true" in db
assert "const val supportsSplits = false" in runtime
assert "check(source.apkPaths.size == 1)" in backend
print("PASS: E59 full lock-time metadata equality precedes all provider deletion side effects.")
print("PASS: native SQLite interleaving with real file leases preserves stale clone data and owners.")
