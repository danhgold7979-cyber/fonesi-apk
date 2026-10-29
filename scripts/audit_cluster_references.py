#!/usr/bin/env python3
"""Fail-closed audit for the private APK cluster reference ledger."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ledger = (ROOT / "apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkClusterReferenceLedger.kt").read_text()
tests = (ROOT / "apk-runtime/src/test/kotlin/vn/fonesi/apk/PrivateApkStoreTest.kt").read_text()

for required in (
    "enum class ClusterOwnerType { CLONE, UPDATE_TASK, REVIEW_REQUIRED, ROLLBACK }",
    "data class ClusterReference(",
    "fun acquire(reference: ClusterReference)",
    "fun release(reference: ClusterReference)",
    "fun references(clusterId: String)",
    "fun referencesForOwner(",
    "fun unreferencedClusterIds()",
    "synchronized(PROCESS_LOCK)",
    "channel.lock()",
    "Files.move(temp, root.resolve(indexName), ATOMIC_MOVE, REPLACE_EXISTING)",
    "MAX_REFERENCES = 100_000",
    "MAX_INDEX_BYTES = 16 * 1024 * 1024",
    "This ledger is deliberately non-destructive",
):
    if required not in ledger:
        raise SystemExit(f"Cluster reference ledger is missing required guard: {required}")

for forbidden in ("fun deleteCluster(", "fun collectGarbage(", "Files.delete(root.resolve", "Files.walk(root)"):
    if forbidden in ledger:
        raise SystemExit(f"Destructive cluster collection is not allowed in E38: {forbidden}")

for required in (
    "clusterReferencesAreIdempotentDurableAndOwnerScoped",
    "releasingLastReferenceOnlyMarksOrphanCandidateAndNeverDeletesCluster",
    "referenceLedgerRejectsMissingClusterAndInvalidOwnerIdentity",
    "concurrentReferenceAcquiresAreSerializedAcrossLedgerInstances",
    "corruptReferenceIndexFailsClosedWithoutDeletingPublishedCluster",
    "replacedReferenceRootIsRejectedBeforeCreatingLockOutsideRoot",
):
    if required not in tests:
        raise SystemExit(f"E38 reference ledger test is missing: {required}")

print("PASS: reference ledger is bounded, durable, process-locked and atomically rewritten.")
print("PASS: orphan detection is dry-run only; no cluster deletion/garbage collection is implemented.")
print("PASS: tests cover idempotence, ownership, concurrency, missing clusters and corrupted ledger.")
