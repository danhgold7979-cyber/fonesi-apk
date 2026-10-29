# E64 — quarantine unknown native guest launch outcomes (pre-code plan)

Date: 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`, branch `phase/e64-unknown-guest-launch-retry-gate-20261029`, stacked on E63 source HEAD `bbaf4bbb4765033306d8a84619ab245d16e18513`, [E63 Draft PR #37](https://github.com/danhgold7979-cyber/fonesi-apk/pull/37). E63 E40–E63 source audit was already successful, but cloud/candidate CI was **in progress at E64 planning**; no E63 PASS assumption. E62 source `505fc58f864532e0cff8d815d77287cd0f161974` passed [CI #40889192439](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40889192439). E64 serves original **Product Phase 3 steps 14–16**, especially safe launch and recovery (NOT Product Phase 64).

## Verified gap

`ManagedGuestEngine.launch` requires READY and a nonnull `record(row)`, then durably sets `launchPending` and calls `backend.launch`. However `record` returns a nonnull `CloneRecord` with `running=null` when an earlier asynchronous launch remains pending and SDK reports not running. A repeat user/shortcut launch can therefore dispatch again after an unknown/failing earlier attempt without reconciled outcome. This violates the conservative no-blind-retry rule despite durable pending being recorded.

## Ordered changes and acceptance

1. Commit this plan and an EXECUTION_PLAN entry **before code**. Preserve E62 READY lifecycle checks and E63 READY shared-peer safeguards.
2. In `ManagedGuestEngine.launch`, read the native guest record as a nonnull `CloneRecord` and **require a known Boolean running state** before any new `registry.pending(true)` or `backend.launch`. If `record` returns `running=null`, reject without SDK dispatch or mutating the pending marker. A positive `running=true` native readback may reconcile prior pending before a foreground/launch dispatch. A verified stopped `running=false` after explicit `stop` permits retry. Keep exact READY gate before readback and existing foreground permission check.
3. Add JVM tests: unknown pending after successful async launch refuses second launch; backend exception remains pending across engine recreation and refuses manual repeat; explicit verified native stop permits retry; positive running native readback allows normal re-open; ensure exact user/package dispatch, no silent original app fallback, no invented clone data.
4. Strengthen E62 launch audit to account for nonnull record + known-state precondition without removing READY invariant. Add E64 source audit and pinned exact-HEAD dual-flavor CI (E40–E64 audits, cloud/candidate unit/Robolectric/build/lint/signature/permission verification, no split/provider/GC activation).
5. Report real CI/source SHA and product rubric without inflating readiness. E64 technical PASS requires exact-source CI SUCCESS; follow-up docs commits have new unverified SHAs until retested. Draft stacked PR only, `main` unchanged.

## Explicit exclusions

No full split-capable production engine, guest Activity/Service/Provider, independent UID/account sessions, physical Android social-app acceptance, Samsung/non-Samsung device proof or release signing. `supportsSplits=false` and `importsSplitApks=false`. Weighted product completion **28.00%**, accepted real third-party clone apps **0**, pending device acceptance.
