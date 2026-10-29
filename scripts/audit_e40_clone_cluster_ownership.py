#!/usr/bin/env python3
"""Fail-closed source audit for E40 clone metadata/private APK ownership."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
database = (ROOT / "app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt").read_text()
ownership = (ROOT / "app/src/main/java/vn/fonesi/app/storage/CloneClusterOwnershipRepository.kt").read_text()
metadata = (ROOT / "app/src/main/java/vn/fonesi/app/storage/CloneMetadataRepository.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/CloneClusterOwnershipRepositoryTest.kt").read_text()
migration_tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/ClusterReferenceRepositoryTest.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()

for required in (
    "val clusterId: String? = null",
    "version = 6, exportSchema = true",
    "val MIGRATION_5_6 = object : Migration(5, 6)",
    "ALTER TABLE clone_metadata ADD COLUMN clusterId TEXT",
    "MIGRATION_4_5, MIGRATION_5_6",
):
    if required not in database:
        raise SystemExit(f"Room v6 migration/metadata requirement missing: {required}")

for required in (
    "class CloneClusterOwnershipRepository(",
    "store.open(clusterId, platformApi)",
    "associateReadBackVerified(",
    "db.runInTransaction(Callable",
    "ClusterOwnerType.CLONE.name",
    "db.clones().associateCluster(",
    "deleteMetadataAfterProviderConfirmed(",
    "fun preflightDeletion(",
    "checkCloneReferences(clone)",
    "metadata and CLONE references disagree",
):
    if required not in ownership:
        raise SystemExit(f"Clone/cluster ownership guard missing: {required}")

if "ownership.preflightDeletion(engineKey, id, saved.launchToken)" not in metadata:
    raise SystemExit("Clone deletion must preflight Room ownership before provider mutation")
if "deleteMetadataAfterProviderConfirmed(engineKey, id, saved.launchToken)" not in metadata:
    raise SystemExit("Clone deletion must use the atomic metadata/reference transaction after provider readback")
if "db.clones().delete(engineKey, id)" in metadata:
    raise SystemExit("Clone metadata must not be deleted outside the ownership transaction")

for required in (
    "associationIsIdempotentAndGenerationSwitchRetainsOtherOwners",
    "providerConfirmedDeleteAtomicallyRemovesMetadataAndOnlyCloneOwner",
    "staleDeleteTokenPreservesMetadataAndReference",
    "missingCloneOrPackageMismatchCannotAcquireReference",
    "inconsistentNullMetadataWithCloneReferenceFailsClosed",
    "inconsistentMultipleCloneOwnersRejectSwitchAndDeleteWithoutLosingData",
    "committedAssociationSurvivesDatabaseReopenAndKeepsLaunchToken",
    "unpublishedOrMalformedPrivateClusterIsRejectedBeforeRoomMutation",
    "wrongEngineOrCloneNamespaceCannotAcquirePrivateCluster",
    "sqliteAbortDuringAssociationRollsBackNewOwnerReference",
):
    if required not in tests:
        raise SystemExit(f"E40 ownership regression test missing: {required}")

for required in (
    "migration5To6AddsNullableClusterIdAndPreservesExistingRowsAndReferences",
    "migration4To6PreservesExistingRowsAndCreatesEmptyReferenceTable",
):
    if required not in migration_tests:
        raise SystemExit(f"Room migration regression test missing: {required}")

storage_tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/StorageIntegrationTest.kt").read_text()
for required in (
    "inconsistentClusterOwnershipBlocksProviderDeletionBeforeGuestIsTouched",
    "providerFailurePreservesAssociatedMetadataAndOtherOwners",
):
    if required not in storage_tests:
        raise SystemExit(f"E40 provider deletion guard test missing: {required}")

if "const val supportsSplits = false" not in runtime:
    raise SystemExit("E40 must not enable split support")
if "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("E40 must retain the single-APK provider guard")
if "fun collectGarbage(" in ownership:
    raise SystemExit("E40 must not add cluster garbage collection")

print("PASS: Room v6 nullable cluster metadata and non-destructive migration are declared.")
print("PASS: clone association/switch/delete keep metadata and CLONE references transactional.")
print("PASS: migration/ownership tests and split-disabled provider boundaries are present.")
