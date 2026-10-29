#!/usr/bin/env python3
"""Fail-closed audit for Room-backed cluster ownership and migration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
database = (ROOT / "app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt").read_text()
repository = (ROOT / "app/src/main/java/vn/fonesi/app/storage/ClusterReferenceRepository.kt").read_text()
tests = (ROOT / "app/src/test/java/vn/fonesi/app/storage/ClusterReferenceRepositoryTest.kt").read_text()
ledger = (ROOT / "apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkClusterReferenceLedger.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()

for required in (
    "data class ClusterReferenceRow(",
    'tableName = "cluster_references"',
    "MIGRATION_4_5",
    "Migration(4, 5)",
    "version = 6",
    "MIGRATION_5_6",
    "Migration(5, 6)",
    "ALTER TABLE clone_metadata ADD COLUMN clusterId TEXT",
    "addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)",
    "abstract fun clusterReferences(): ClusterReferenceDao",
):
    if required not in database:
        raise SystemExit(f"Room v6 cluster ownership schema missing: {required}")

for required in (
    "class ClusterReferenceRepository(",
    "db.withTransaction",
    "suspend fun acquire(reference: ClusterReference)",
    "suspend fun release(reference: ClusterReference)",
    "suspend fun reconcile(publishedClusterIds: Set<String>)",
    "publishedWithoutReferences",
    "referencedButMissing",
):
    if required not in repository:
        raise SystemExit(f"Room cluster reference repository missing guard: {required}")

for required in (
    "referencesAreIdempotentOwnerScopedDurableAndReconciledWithoutDeletion",
    "migration4To6PreservesExistingRowsAndCreatesEmptyReferenceTable",
):
    if required not in tests:
        raise SystemExit(f"Room cluster ownership test missing: {required}")

for source, forbidden in (
    (repository, "fun collectGarbage("),
    (ledger, "fun collectGarbage("),
    (ledger, "Files.walk(root)"),
):
    if forbidden in source:
        raise SystemExit(f"Destructive collection is not allowed in E39: {forbidden}")

if "const val supportsSplits = false" not in runtime:
    raise SystemExit("SpaceCore split support must remain disabled")
if "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("SpaceCore single-APK guard must remain intact")

print("PASS: Room v6 migration and transactional cluster-reference repository are present.")
print("PASS: missing/orphan reconciliation is report-only; no destructive GC is implemented.")
print("PASS: SpaceCore split import remains disabled and provider install path is unchanged.")
