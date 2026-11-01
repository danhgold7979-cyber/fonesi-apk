# E53 — WorkManager retained/private-source recovery safety results

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. Original **Product Phase 4, steps 18–20**, not another product Phase.

## Source and verified checks

- Branch `phase/e53-background-private-source-recovery-20261029`, [Draft PR #27](https://github.com/danhgold7979-cyber/fonesi-apk/pull/27) stacked on verified E52 [Draft PR #26](https://github.com/danhgold7979-cyber/fonesi-apk/pull/26). E52 code `59bf73522b3a86c1caca57af8613bb2e7b915b2b` [CI #40853790848](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40853790848) SUCCESS, 423 tests each gate; E52 reporting HEAD `74492efde2357df3c1f8c1fbbae9b182ee320550` [CI #40855141141](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40855141141) SUCCESS.
- Detailed [E53 pre-code plan](E53_BACKGROUND_PRIVATE_SOURCE_RECOVERY_PLAN.md) was committed in `a8195c3b8a413adbbff11e457ed03d5e49a3cda0` before implementation `5f2bfb17dac2cff9ff40e186dde233fb42bf52db` and subsequent audit tightening. **Exact accepted E53 source is `76ab8d3ac991f92f8d2a21b5c4fff3d51b45e3b9`.**
- [E53 GitHub Actions #40855911991](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40855911991) completed **SUCCESS** at source `76ab8d3ac991f92f8d2a21b5c4fff3d51b45e3b9`: **427 tests per cloud/candidate gate**, build/lint, signed APK/permission verification and pinned candidate SDK/native tests. All E40–E53 Python/static source audits PASS. Safety gate confirms full split provider, guest activation and destructive cluster GC are not enabled.
- [Test-only artifact #12890370115](https://github.com/danhgold7979-cyber/fonesi-apk/actions/artifacts/12890370115) holds sanitized test receipts, generated Room v6 schema and build verification reports, not a production signed release.

## Implementation and evidence

`core/src/main/kotlin/vn/fonesi/core/update/UpdateJournalRecovery.kt`: background-only PREPARING cleanup remains conditional on an explicit read-only original-source authority check; all `retainedSource` tasks are held before any snapshot IO even if external callback would otherwise allow them. A callback that denies cleanup preserves the exact PREPARING task+snapshot; an exception leaves data untouched and marks the recovery retryable. Legacy unowned tasks still discard incomplete snapshots and CAS back to QUEUED. Applying/verifying outcomes only become REVIEW_REQUIRED, retaining snapshots, without calling the engine.

`app/src/main/java/vn/fonesi/app/update/UpdateJournalRecoveryWorker.kt` injects `RoomLegacyUpdateSourcePolicy(database)::allows`, which disallows older E41 private sources held by UPDATE_TASK or REVIEW_REQUIRED owner references in addition to durable retained flags. No runtime activation or original APK catalog readback occurs.

Three new core/JVM regressions and one Room/native SQLite multi-case Robolectric worker test cover marked private PREPARING, owner-only PREPARING, owner lookup errors, unowned legacy cleanup, retained APPLYING uncertainty and second-run idempotence. `scripts/audit_e53_background_private_source_recovery.py` plus E53 dedicated workflow preserve E40–E52 source-identity checks.

## Acceptance boundaries and next

**Bounded technical PASS only.** Product readiness **28.00% weighted**, accepted real third-party app clones **0**. Room v6, `supportsSplits=false`, `importsSplitApks=false`, no production real guest Activity/complete split provider, OS UID or account-session isolation, social-app update data proof, physical Samsung/other device testing or release signing. `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` remains unchanged; PR Draft. A documentation reporting commit has its own SHA and must pass exact-head CI separately.

Next technical work should follow a new pre-code plan and ensure quarantined private tasks do not starve **unrelated, safely executable** original-source queue work; keep same-package protected sources fail-closed. Do not enable provider/split import without audited actual guest readback and phone acceptance.
