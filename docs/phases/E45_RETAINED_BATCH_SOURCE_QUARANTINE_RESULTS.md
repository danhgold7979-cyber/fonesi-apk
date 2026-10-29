# E45 — Retained multi-target batch + source-authority quarantine: CI results

Date: 2026-10-29. Repository [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Branch `phase/e45-retained-batch-source-quarantine-20261029`, stacked [Draft PR #19](https://github.com/danhgold7979-cyber/fonesi-apk/pull/19) on E44 batch PR #17. Implements the no-original-app fallback guard from separately tested E44 quarantine PR #18; no merge to `main`.

## Verified source and checks

- **Technical E45 implementation SHA `64e0a285da84360e3fdea57135367d5b9bd6f8b5`**, [GitHub Actions #40848683404](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40848683404) — **SUCCESS** on that exact SHA, 2026-10-29.
- CI log explicitly reports **390/390 tests for cloud** and **390/390 for candidate**, 0 failed tests, and successful builds, lint, APK signing/permission audits. E40–E45 source audits and provider single-APK/no private APK garbage-collection guards passed. No tests were skipped to force success.
- Workflow artifact **12886437182**, `fonesi-E45-retained-batch-source-quarantine-TEST-ONLY`, contains sanitized cloud/candidate receipts and committed Room schema v6. [Workflow and artifact](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40848683404).
- Prior independent receipts: E44 batch 382 tests/gate at [#40846706698](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40846706698) on reporting HEAD; E44 quarantine 380 tests/gate at [#40847390931](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40847390931) after explicit Kotlin JUnit `Unit` return fix. E45 integration re-ran both safety audits and all tests on the integrated source.

## Code and validation boundary

- Public `PrivateClusterRetainedEnqueuer.enqueueBatch` re-opens **every** previously published, signed private cluster before inserting any Room row, verifies exact package/version/signers, then atomically queues the batch and acquires matching UPDATE_TASK references.
- Each task persists immutable `retainedSource=true` in the backward-compatible Room journal JSON; mixed original/retained requests with identical active dedup identity fail closed. The older `associate` path atomically marks retained authority with its owner reference.
- `UpdateRunner.runNext` blocks marked/private-source tasks before the original `SourceCatalog` fallback. `RoomLegacyUpdateSourcePolicy` also blocks older unmarked tasks already associated with UPDATE_TASK/REVIEW_REQUIRED references.
- Two new integration native-SQLite Robolectric tests cover successful batch owner/marker persistence across Room reopen and rollback when the later target conflicts with an existing legacy source task. Existing E43/E44 tests remain.

This is **not** a verified private-source execution adapter, guest engine or real clone update. Source-authority quarantine is a deny rule; it cannot launch/import a guest app, run full split APKs, separate kernel UIDs, prove account isolation or preserve sessions. The normal legacy update runner remains for unassociated ordinary tasks only. Room stays v6 and candidate split support remains disabled.

## Remaining product acceptance and follow-up

Original **Phase 4 steps 17–20** remain incomplete. No physical Samsung/device proof, third-party social-account session, arbitrary split provider, real guest Activity, release signing or isolation acceptance. Product weighted estimate remains **28.00%**; real third-party apps accepted: **0**. E46 should close a separate recovery-time false-success/fallback risk: legacy `recoverLocked()` may read the engine when an older/private retained task is APPLYING/VERIFYING/REVIEW_REQUIRED; it must not produce success without source-appropriate verified receipt. E46 requires its own pre-code plan, tests and CI. Keep PR #19 Draft; the report-only follow-up HEAD needs its own workflow check.
