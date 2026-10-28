# E27 — Room-backed durable journal recovery results

Date: 2026-10-28. Workflow: [E27 Room-backed durable journal recovery #40719612538](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40719612538).

## Result

**E27 CI PASS** on workflow source SHA `d0fa57508bc05a8a271b1e3da94c6f567cef77e6`. The workflow ran on the cumulative branch `phase/e21-android-split-code-proof-20261028`. Later commits on that branch changed workflow path filtering and added this E28 plan only; they did not alter the E27-tested worker, core recovery logic, Room test, or architecture audit.

- Python source compilation and the fail-closed background-worker architecture audit: PASS.
- Core tests, APK auditor, Room/Robolectric worker tests, cloud build/lint, and exact cloud APK signature/permission audit: PASS.
- Integrated candidate build/tests/lint and exact candidate manifest/signature audit: PASS.
- Both cloud and candidate reports were checked to keep `engine_runtime_verified == false`; no synthetic test was promoted to product-engine acceptance.
- Test/build log reported **320 tests** for the cloud gate and **320 tests** for the candidate gate; no test failure was reported.
- CI artifact: [E27 test-only verification receipts](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40719612538/artifacts/12828844755).

## What the E27 tests now exercise

1. A real, uniquely named Room database is seeded through `RoomUpdateTaskStore`; the worker recovery helper processes those persisted rows.
2. `PREPARING` cleans only its own incomplete snapshot and returns to `QUEUED`, clearing affected IDs/fingerprint/issue.
3. `APPLYING` and `VERIFYING` move to `REVIEW_REQUIRED/INTERRUPTED` and retain their prepared snapshot files; the worker never reruns import or marks them successful.
4. Existing `REVIEW_REQUIRED`, `QUEUED`, `FAILED`, and `CANCELLED` rows remain unchanged; the user's paused-queue flag is preserved.
5. A second recovery pass is idempotent.
6. Lock contention returns WorkManager `Retry` and leaves the task state/revision unchanged.
7. Unsafe cleanup through a task snapshot symlink returns `Retry`, does not advance the `PREPARING` row, and does not touch the symlink target.
8. Architecture audit confirms the production worker uses the fixed `fonesi.db` and `files/updates` root, accepts no WorkManager input path override, and has no clone SDK, package enumeration, launch, or import dependency.

## Limits / not accepted

E27 is a journal persistence/recovery milestone only. It does **not** prove real third-party clone lifecycle, split APK production import, guest Activity/Service/Provider registration, OS UID-level account isolation, social login, Samsung ARM64 behavior, session-preserving updates, background execution guarantees across OEMs, or release signing.

The product readiness estimate remains **~30% (28.00% weighted rubric)**. Do not raise it for test-only recovery work.

## Next

E28: [WorkManager scheduling idempotence and lifecycle safety plan](E28_WORKMANAGER_SCHEDULING_PLAN.md). Add scheduling tests before changing behavior; verify unique startup/periodic work, repeated unlocked-lifecycle calls, and no engine activation from background/application startup. Keep the cumulative branch and preserve the current acceptance boundary.
