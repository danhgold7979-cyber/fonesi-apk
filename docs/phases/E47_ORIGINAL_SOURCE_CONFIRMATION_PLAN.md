# E47 — Fail-closed original-source confirmation when private updates are active (pre-code plan)

Date 2026-10-29; repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e47-original-source-confirmation-20261029` from E46 implementation `ef06a06818c997aba1a0c796171ea1b265ebdd9f`. Original **product Phase 4, steps 17–20**. This is a technical increment, not a new product phase. E46 CI #40849553940 was still running when this plan was committed. Before treating E47 as accepted, parent E45/E46 and the exact E47 source must independently pass their required gates.

## Threat and existing behavior

The E45 original-source runner quarantine and E46 foreground reconciliation guard are not sufficient if `UpdateRequestService.confirm` can enqueue an *additional* legacy task at a different dedup version for the same engine/package while an active retained/private task already holds that source authority. This can leave conflicting source-authority tasks in the durable queue even though the current runner blocks the older one. The confirmation path must fail closed and commit zero new tasks for all selected packages when any is protected.

## Ordered implementation

1. Read roadmap, E45/E46 plans, current GitHub refs, and `UpdateRequestService` plus its native SQLite tests before changes. Keep E46 and E45 branches unmodified.
2. In the existing `db.runInTransaction` confirmation boundary, inspect all existing *nonterminal* tasks for the current engine and requested package(s). Use `RoomLegacyUpdateSourcePolicy` to reject any retainedSource marker or an older owner-associated UPDATE_TASK/REVIEW_REQUIRED task (including a pre-marker legacy row). Any unreadable task/Room policy must abort confirmation. Do not mutate or release the private owner and do not consult an unverified private byte path.
3. Add Robolectric/native SQLite tests for private retained task from a different source version, pre-marker owner reference, mixed package batch all-or-nothing rejection, and ordinary pure-legacy/different-package confirmations that should continue to work. Preserve existing preview consent, stale source, permission, double enqueue and failure rollback tests.
4. Run pinned E40–E47 source audits and full cloud/candidate test/build/lint/signature/permission workflow on the exact code SHA. No test bypass, no added runtime provider, no Room migration. Review CI logs and artifacts before PASS.
5. Record exact result and update `docs/STATUS.md`, `docs/PRODUCT_PROGRESS.md` and E47 results from verifiable evidence; keep Draft PR stack unmerged to `main`.

## Acceptance limits

Active private update ownership blocks a conflicting original-app confirmation **before any queue mutation**; an unrelated package or an unowned legacy job still follows existing preview consent and dedup semantics. This does not import/launch/update a guest from retained private APK, enable split import, isolate actual user accounts or complete the 4 phone acceptance checkpoints. Room v6 and no automatic private-file GC preserved. Weighted product estimate remains **28.00%** with **0 real third-party apps accepted** until actual product evidence changes.
