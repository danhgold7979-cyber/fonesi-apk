# E27 — durable Room-to-WorkManager recovery integration proof (plan before code)

Date: 2026-10-28. Continue on the existing cumulative branch \`phase/e21-android-split-code-proof-20261028\`; do not create another product/phase branch. E21–E25 Android synthetic proofs are green on \`d911ee2\` and later regression runs have passed on \`eb5158e\`; E26 journal-only recovery is in implementation/audit. Current product estimate remains ~30% (weighted rubric), not measured.

## Why E27 is needed

E26 adds a platform-independent recovery state machine and a WorkManager adapter. Core tests already prove the state machine with memory fixtures, while the current app Worker test mostly proves an idle journal and checks source text. E27 must prove that **the actual Room rows and private filesystem snapshots** are reconciled by the worker path, without engine activation, package enumeration, APK import or account access.

## Implementation sequence

1. Keep E26 as the prerequisite: cloud and candidate build audit, worker-only architecture audit, all core/Room/Robolectric tests must pass on one exact SHA. Do not paper over missing permissions/components.
2. Add a narrow internal recovery entry point that permits tests to use a uniquely named Room database and test-owned private files while production \`doWork()\` always uses the fixed production database and \`files/updates\` root. No WorkManager input field may select a database path or filesystem root.
3. Seed real Room journal rows using \`RoomUpdateTaskStore\` and durable task transitions:
   - PREPARING + an incomplete snapshot directory: worker removes only that task snapshot and CAS-transitions it back to QUEUED with markers cleared.
   - APPLYING / VERIFYING + an existing prepared snapshot: worker changes the task to REVIEW_REQUIRED/INTERRUPTED and retains the snapshot.
   - REVIEW_REQUIRED, QUEUED, terminal rows and pause control: remain unchanged.
4. Test the actual \`CoroutineWorker\` with an injected/test database provider or package-private helper; the production path must not accept caller-controlled paths. Validate retry on lock contention and cleanup failure; no test may call the native SDK, \`LocalServices\`, \`RuntimeBindings.engine\`, \`PackageManager\`, source APK enumeration or guest components.
5. Assert all task revisions/states/issues/fingerprints, snapshot file presence/absence, queue pause preservation, idempotence, and no accidental state promotion to SUCCEEDED. Use disposable synthetic metadata and never real account or APK data.
6. Update \`scripts/audit_journal_worker.py\` to verify the test hook is package-private and cannot be selected through worker \`Data\`; verify production worker still hardcodes the default DB/root. Add required file paths to E27 workflow. Keep exact WorkManager permission and component allowlists.
7. CI must run Python audit, core/APK tests, app Robolectric worker tests, cloud build + exact manifest/signature audit, candidate build + exact manifest/signature audit, and E21–E25 Android regression jobs. Record actual totals and hashes only after all required checks pass.

## Acceptance criteria

- Actual worker code reconciles durable Room state for PREPARING/APPLYING/VERIFYING as specified.
- A crash after external APPLYING never triggers another engine import; it remains REVIEW_REQUIRED pending foreground readback.
- Snapshot cleanup is scoped to the exact task UUID and cannot escape the app-private updates root.
- Pause flag and unrelated/terminal tasks are preserved; a second run is idempotent.
- Worker cannot start or initialize the SDK and has no guest/account API access.
- Production WorkManager input cannot choose a DB or file path.
- No increase in product readiness percentage solely from synthetic recovery tests.

## Explicitly not claimed

This remains journal reliability, not a clone engine. It does not prove actual guest Activity/Service/Provider registration, Android PackageManager/Binder/permission virtualization, per-account OS UID isolation, real social login, Samsung ARM64, persistent sessions across real app updates, performance, or release readiness.
