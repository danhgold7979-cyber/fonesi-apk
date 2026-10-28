#!/usr/bin/env python3
"""Fail-closed architecture audit for E26 background journal recovery."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "app/src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryWorker.kt").read_text()
worker = re.sub(r"/\*[\s\S]*?\*/|//[^\r\n]*", "", worker)
application = (ROOT / "app/src/main/java/vn/fonesi/app/FonesiApplication.kt").read_text()
manager = (ROOT / "app/src/main/java/vn/fonesi/app/MainActivity.kt").read_text()
candidate = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()

for forbidden in (
    "RuntimeBindings", "LocalServices", "FCore", "PackageManager",
    "AndroidSourceCatalog", "listClones(", ".update(", "launch(",
):
    if forbidden in worker:
        raise SystemExit(f"E26 worker has forbidden runtime dependency/operation: {forbidden}")

required_worker = (
    "CoroutineWorker", "FonesiDatabase.open(context)", "RoomUpdateTaskStore",
    "FileSourceSnapshotter", "FileQueueLock", "UpdateJournalRecovery(",
    "if (!result.lockAcquired || result.retryable) Result.retry()",
)
for required in required_worker:
    if required not in worker:
        raise SystemExit(f"E26 worker is missing required journal-only behavior: {required}")

if "RuntimeBindings.attach" in application or "attachBaseContext" in application:
    raise SystemExit("Application startup must not initialize the native clone SDK")
if "enqueueUniqueWork(" not in application or "enqueueUniquePeriodicWork(" not in application:
    raise SystemExit("Startup and periodic journal recovery must both be scheduled")
if "LaunchedEffect(gate.unlocked)" not in manager or "if (gate.unlocked) RuntimeBindings.attach(applicationContext)" not in manager:
    raise SystemExit("SDK activation must be tied to visible unlocked manager UI")
if "@Synchronized fun attach(context: Context)" not in candidate or "if (initialized) return" not in candidate:
    raise SystemExit("Foreground SDK activation must be synchronized and idempotent")

print("PASS: E26 background worker is journal-only; SDK activation is foreground/unlocked only.")
print("PASS: startup/periodic recovery uses unique WorkManager requests.")
print("NOT VERIFIED: real third-party clone lifecycle, account isolation or release readiness.")
