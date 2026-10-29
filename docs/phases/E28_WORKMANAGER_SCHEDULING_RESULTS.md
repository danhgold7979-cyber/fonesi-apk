# E28 — WorkManager scheduling idempotence and lifecycle safety results

Date: 2026-10-28. Workflow: [E28 WorkManager scheduling idempotence and lifecycle safety #40727455497](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40727455497).

## Result

**E28 CI PASS** on tested source SHA `5e42dec5545c6884e99be33fba81399f5aca9df6`. The current branch HEAD is `4deb14e29d119d0fcbae98d351a40d28021acebb`; GitHub confirms both commits have the same tree SHA `1cf49b189042dd9698499b5440b91961ccfdbd7a`, so the later commit changes no source files and the tested tree matches the current tree.

- Fail-closed worker/scheduler architecture audit: PASS.
- Core tests, APK auditor, Room/WorkManager/Robolectric tests, cloud build/lint, and exact cloud APK signature/permission audit: PASS.
- Candidate build/tests/lint and exact candidate manifest/signature audit: PASS.
- Both cloud and candidate reports preserved `engine_runtime_verified == false`; physical-device and third-party app acceptance remain explicitly unverified.
- CI reported **321 tests** for the cloud gate and **321 tests** for the candidate gate, with no reported test failure.
- Test-only evidence artifact: [E28 WorkManager scheduling receipts](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40727455497/artifacts/12833762727). Artifact digest: `sha256:69f21fe6c9d38bac2791313b4199025c4adb7e90f3e4f5d05f1311827197678c`.

## What the tests cover

1. Repeated calls to the same unique-work scheduler retain at most one unfinished startup recovery request and at most one unfinished periodic recovery request.
2. Both unique work names target `UpdateJournalRecoveryWorker`.
3. The test terminates a prior startup request and confirms a later manager-entry scheduling call can enqueue a fresh startup reconciliation.
4. Periodic recovery remains registered after re-entry and retains at most one unfinished periodic request.
5. The scheduler's initialization/scheduling entry point is synchronized, with the same unique-work implementation shared by production and tests.
6. Architecture audit confirms `ExistingWorkPolicy.KEEP`, `ExistingPeriodicWorkPolicy.KEEP`, six-hour periodic cadence, and scheduling only inside the visible/unlocked manager branch.
7. Application process startup remains side-effect free; the background worker does not initialize the clone SDK, enumerate installed apps, launch clones or import APKs.

## Limits / not accepted

E28 proves scheduling metadata and repeat-call behavior in WorkManager tests. It does **not** prove Android will execute periodic work at an exact time on every OEM, or establish third-party clone lifecycle, split APK production import, guest component registration, account/session isolation, Samsung ARM64 compatibility, session-preserving updates or release signing.

Product readiness remains **~30% estimated (28.00% weighted rubric)**. No percentage increase is justified by scheduler tests alone.

## Next

E29: [signed private component dispatch proof plan](E29_PRIVATE_COMPONENT_DISPATCH_PLAN.md). It must bind E25's verified private manifest/catalog to E24's signed private DEX/View proof with a bounded UUID-bound ticket and explicit negative acceptance flags. Keep it synthetic and test-only. Do not claim that the host Activity is a registered Android guest Activity.
