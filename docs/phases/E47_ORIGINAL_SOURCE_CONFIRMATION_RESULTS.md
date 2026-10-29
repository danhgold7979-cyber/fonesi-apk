# E47 — Original-source consent authority: exact-source CI results

Date: 2026-10-29. Repository [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk).
Branch `phase/e47-original-source-confirmation-20261029`, [Draft PR #21](https://github.com/danhgold7979-cyber/fonesi-apk/pull/21), stacked on E46 Draft PR #20. Original **Product Phase 4, roadmap steps 17–20**, not a new product phase.

## Technical result

- Exact implementation commit **`2ccffc5e5184245c29e1a86955919b3765606012`**: dedicated [E47 Actions #40850268707](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40850268707) concluded **SUCCESS** on that source SHA.
- CI log cloud and candidate each prints **`PASS: 402 tests; build artifacts, lint and APK signature/permissions verified.`**, no reported failed or skipped tests. Prior E40–E46 and new E47 source safety audits, pinned Android SDK/JDK and unchanged Room v6/split-disabled/no-GC assertions ran successfully.
- Sanitized test-only artifact **12886727710**, named `fonesi-E47-original-source-confirmation-TEST-ONLY`, includes test reports and generated schema. It is not a signed release artifact.
- Four new native SQLite/Robolectric tests cover a retained-source task with a different installed-app version, pre-marker UPDATE_TASK/REVIEW_REQUIRED ownership, mixed selected-package batch atomic rejection, and unrelated/legacy paths. Prior consent, concurrent source-change and rollback tests remain.

## Implementation boundary and blockers

`UpdateRequestService.confirm` checks all currently active private-source authorities for selected update target packages **inside its existing atomic Room transaction before inserting any original-app queue task**. A private retained marker or older E41 owner reference blocks an unsafe original-app consent; an unreadable database state fails closed. No private-cluster file is deleted or installed, no engine is invoked, no split support is enabled.

**This is a technical CI PASS, not product Phase 4 acceptance.** Product score stays **28.00% (~30% rounded)**; real accepted third-party clones **0**. Real guest Activity, full base/split provider, account/UID isolation, Samsung/other-device tests and production release signing remain unresolved. Keep Draft PR, no merge to `main`. A documentation-only follow-up SHA must pass its own exact-HEAD CI before claiming that HEAD is tested.
