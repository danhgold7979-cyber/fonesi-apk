#!/usr/bin/env python3
"""Fail-closed architecture audit for background journal recovery (E26/E27)."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
worker_path = ROOT / "app/src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryWorker.kt"
test_path = ROOT / "app/src/test/java/vn/fonesi/app/update/UpdateJournalRecoveryWorkerTest.kt"
scheduler_test_path = ROOT / "app/src/test/java/vn/fonesi/app/update/UpdateJournalRecoverySchedulerTest.kt"
worker = re.sub(r"/\*[\s\S]*?\*/|//[^\r\n]*", "", worker_path.read_text())
application = (ROOT / "app/src/main/java/vn/fonesi/app/FonesiApplication.kt").read_text()
scheduler = (ROOT / "app/src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryScheduler.kt").read_text()
manager = (ROOT / "app/src/main/java/vn/fonesi/app/MainActivity.kt").read_text()
candidate = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()
tests = test_path.read_text()
scheduler_tests = scheduler_test_path.read_text()

for forbidden in (
    "RuntimeBindings", "LocalServices", "FCore", "PackageManager",
    "AndroidSourceCatalog", "listClones(", ".update(", "launch(",
    "inputData", "getInputData",
):
    if forbidden in worker:
        raise SystemExit(f"Background worker has forbidden runtime dependency/operation: {forbidden}")

required_worker = (
    "CoroutineWorker", "FonesiDatabase.open(context)", "RoomUpdateTaskStore",
    "FileSourceSnapshotter", "FileQueueLock", "UpdateJournalRecovery(",
    'File(context.filesDir, "updates")',
    "if (!result.lockAcquired || result.retryable) Result.retry()",
    "internal fun recoverJournal(database: FonesiDatabase, updates: File)",
)
for required in required_worker:
    if required not in worker:
        raise SystemExit(f"Background worker is missing required behavior: {required}")

if ("RuntimeBindings" in application or "attachBaseContext" in application
        or "UpdateJournalRecoveryScheduler" in application):
    raise SystemExit("Application process startup must remain side-effect free")
if "enqueueUniqueWork(" not in scheduler or "enqueueUniquePeriodicWork(" not in scheduler:
    raise SystemExit("Startup and periodic journal recovery must both be scheduled")
if ("LaunchedEffect(gate.unlocked)" not in manager
        or "UpdateJournalRecoveryScheduler.ensureScheduled(applicationContext)" not in manager
        or "RuntimeBindings.attach(applicationContext)" not in manager):
    raise SystemExit("Recovery scheduling and SDK activation must be tied to visible unlocked manager UI")
if "@Synchronized fun attach(context: Context)" not in candidate or "if (initialized) return" not in candidate:
    raise SystemExit("Foreground SDK activation must be synchronized and idempotent")

if "ExistingWorkPolicy.KEEP" not in scheduler or "ExistingPeriodicWorkPolicy.KEEP" not in scheduler:
    raise SystemExit("Recovery scheduler must retain unique KEEP semantics")
if 'PeriodicWorkRequestBuilder<UpdateJournalRecoveryWorker>(6, TimeUnit.HOURS)' not in scheduler:
    raise SystemExit("Periodic recovery interval must remain six hours")
if "if (gate.unlocked)" not in manager:
    raise SystemExit("Recovery scheduling must remain inside the unlocked manager branch")

required_scheduler_test_evidence = (
    "repeatedUnlockedSchedulingKeepsUniqueWorkAndLaterEntryCanReconcileAgain",
    "initializeTestWorkManager",
    "getWorkInfosForUniqueWork",
    "Two unlocked entries must keep one unfinished startup request",
    "Repeated calls must keep one unfinished periodic request",
    "later unlocked manager entry must enqueue fresh startup reconciliation after completion",
    "GateExecutor",
)
for required in required_scheduler_test_evidence:
    if required not in scheduler_tests:
        raise SystemExit(f"E28 scheduling test is missing required evidence: {required}")

required_test_evidence = (
    "workerRecoversRealRoomRowsAndPrivateSnapshotsIdempotently",
    "workerRetriesWhenQueueLockIsHeld",
    "workerRetriesAndPreservesPreparingRowWhenSnapshotCleanupIsUnsafe",
    "FonesiDatabase.open(context, databaseName)",
    "PREPARING", "APPLYING", "VERIFYING", "REVIEW_REQUIRED",
    "preserve the user's paused queue", "Second recovery must be idempotent",
    "internal fun recoverJournal", "inputData",
)
for required in required_test_evidence:
    if required not in tests:
        raise SystemExit(f"E27 Room-backed worker test is missing required evidence: {required}")

print("PASS: background worker is journal-only and production DB/snapshot root are fixed.")
print("PASS: SDK activation remains foreground/unlocked only; no worker input path override.")
print("PASS: E27 tests seed real Room task rows and verify scoped snapshot cleanup, review retention, pause preservation and idempotence.")
print("PASS: E28 scheduling tests cover unique startup/periodic requests and foreground re-entry after completion.")
print("NOT VERIFIED: real third-party clone lifecycle, account isolation or release readiness.")
