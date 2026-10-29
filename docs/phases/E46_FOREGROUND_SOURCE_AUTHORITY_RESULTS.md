# E46 — Foreground source-authority guard: exact-SHA CI results

Date: 2026-10-29. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk).
Branch `phase/e46-foreground-source-authority-20261029` and [Draft PR #20](https://github.com/danhgold7979-cyber/fonesi-apk/pull/20) stacked on E45 Draft PR #19. Original product **Phase 4, steps 17–20**.

## Technical acceptance evidence

- **Verified implementation SHA `ef06a06818c997aba1a0c796171ea1b265ebdd9f`** passed pinned [E46 GitHub Actions #40849553940](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40849553940), conclusion **SUCCESS**, on that same HEAD.
- Workflow ran E40–E46 source audits, pinned Android SDK/JDK bootstrap, `./scripts/check.sh` cloud and `./scripts/check.sh --with-engine-candidate`. Both gates logged **PASS: 394 tests; build artifacts, lint and APK signature/permissions verified**, with no detected test failures. Candidate single-APK guard, `supportsSplits=false`, no provider activation or private-cluster GC audit stayed enabled.
- Uploaded test-only artifact **12886991923**: `fonesi-E46-foreground-source-authority-TEST-ONLY` (Room schema v6 and sanitized test receipts), available through the workflow.
- Newly added `ForegroundAutoUpdaterTest` native-SQLite regressions exercise retained-source marked QUEUED tasks and pre-marker E41 UPDATE_TASK/REVIEW_REQUIRED owners when the current *original installed-app* APK version changes. Existing legacy source supersession and uncertain-result recovery tests remain.

## Scope and limitations

`ForegroundAutoUpdater` now uses `RoomLegacyUpdateSourcePolicy` before either cancelling older queued/deferred work or retrying deferred work from original-app preview. Retained, owner-protected, malformed/unreadable authority fails closed rather than being reconciled as an original-app update. The production runner and manual consent remain independent; this is **not** private APK provider execution, split import, guest Activity, UID/account isolation, phone testing or verified social-app update/session preservation.

**Product rubric remains 28.00%**, real accepted third-party app clones **0**; no product Phase is closed. Keep Draft PR, no merge to `main`; any report-only commit after `ef06a06818c997aba1a0c796171ea1b265ebdd9f` needs separate exact-HEAD CI verification.
