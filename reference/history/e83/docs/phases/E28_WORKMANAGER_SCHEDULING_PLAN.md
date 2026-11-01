# E28 — WorkManager scheduling idempotence and lifecycle safety (plan before code)

Date: 2026-10-28. Continue on the cumulative branch `phase/e21-android-split-code-proof-20261028`; do not create another product branch. E26 proves the journal-only recovery state machine and candidate/cloud build boundary. E27 adds Room-backed worker reconciliation, scoped snapshot cleanup, lock contention and cleanup-failure tests; E27 must first complete its full CI gate before this phase is treated as unblocked.

## Purpose

E27 validates what the worker does once invoked. E28 will validate the durable scheduling contract: the visible/unlocked manager may register one startup recovery and one periodic recovery, repeated lifecycle entry must not create duplicate pending work, and scheduling must not initialize the clone engine or weaken the worker's fixed database/snapshot roots.

## Pre-code inspection

1. Read `UpdateJournalRecoveryScheduler.ensureScheduled`, its MainActivity call site, WorkManager dependency/version, merged-manifest audit, and the existing E26/E27 workflows.
2. Preserve the current policy: schedule only after the manager is visible and unlocked; startup recovery uses unique work; periodic recovery uses a six-hour interval; the worker only repairs journal metadata.
3. Confirm whether repeated `WorkManager.initialize` calls can race when the fallback path is used. If synchronization is needed, serialize initialization and scheduling without introducing Application-startup engine activation.
4. Keep the cloud build free of network permission and SDK activation. Do not add exported components or permissions for tests.

## Implementation sequence

1. Add Robolectric/WorkManager testing for `UpdateJournalRecoveryScheduler.ensureScheduled` using a test WorkManager instance and a disposable work database.
2. Call the scheduler twice while startup work remains pending. Assert exactly one startup unique-work record and exactly one periodic unique-work record; verify both point to `UpdateJournalRecoveryWorker`, have no caller-supplied input data, and retain the intended periodic interval/KEEP semantics where exposed by WorkManager test APIs.
3. Exercise repeated unlocked-lifecycle scheduling and verify it does not create duplicate pending requests. Test that a completed startup request can be scheduled again on a later manager entry, because a fresh foreground entry should reconcile journal state again.
4. If pre-code inspection confirms a race in the WorkManager fallback, make initialization idempotent/synchronized. Do not catch and suppress arbitrary WorkManager errors; only handle the documented already-initialized race if it can be distinguished safely.
5. Extend the fail-closed architecture audit to assert the scheduling call remains in the visible/unlocked manager path, no scheduler is invoked from `Application.attachBaseContext`, and unique work names/policies stay stable.
6. Run core/APK tests, app Robolectric tests, cloud build and exact APK audit, candidate build and exact APK audit. Preserve the explicit `engine_runtime_verified == false` boundary.
7. Record exact commit SHA, test totals, build/audit results, and any remaining test-environment limitations in E28 results. Do not update product readiness percentage for scheduling tests alone.

## Acceptance criteria

- Repeated calls do not create multiple pending startup or periodic recovery requests.
- A later foreground/unlocked manager entry can enqueue a fresh startup reconciliation after a prior startup request has completed.
- Worker input cannot select database names, snapshot roots, packages, or clone IDs.
- No scheduler call activates the SDK or engine in Application startup or from a locked/background manager.
- Cloud and candidate builds still pass their exact manifest/signature audits.
- All existing E26/E27 tests remain green on the same source SHA.

## Explicitly not claimed

E28 is scheduling reliability only. It does not prove the Android process will always run a periodic task on every OEM, real guest Activity/Service/Provider registration, split APK production import, kernel/UID account isolation, third-party social login, Samsung ARM64 compatibility, session-preserving updates, release signing, or product completion.
