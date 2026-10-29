# E62 — prohibit guest launch from unconfirmed reservation (pre-code plan)

Date: 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`, development branch `phase/e62-ready-guest-launch-gate-20261029` stacked on [E61 Draft PR #35](https://github.com/danhgold7979-cyber/fonesi-apk/pull/35), reporting HEAD `b4922a080b9704dcd25b5c9ca5a09c56f31c6beb`. E61 implementation code/test SHA `2467c147d71f4f4d204a74628508023b7b2b14ab` passed [CI #40887119633](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40887119633); E61 reporting-head CI was in progress when planning. Original **Product Phase 3 steps 13–16** clone lifecycle. E62 is a technical increment, not a product Phase.

## Gap in current source

`ManagedGuestEngine.instance` includes RESERVED, READY and DELETING rows to support uncertain creation/reconciliation. `ManagedGuestEngine.launch` currently checks only `row.state != DELETING` and `record(row) != null` before setting durable `launchPending` and calling `backend.launch`. Consequently a partially imported RESERVED instance that happens to have native package readback may be launched without the successful create/metadata confirmation that transitions it to READY. CONFLICT/DELETED must likewise never be launchable. Reconciliation via `listClones` is not the same as permission to launch an unconfirmed reservation.

## Implementation and gates

1. Read current README, STATUS, ROADMAP, EXECUTION_PLAN, CODE_TEST_RULES, PRODUCT_PROGRESS, latest E61 results, `ManagedGuestEngine`, `InstanceRegistry` and core tests; verify parent HEAD/CI/PR. Plan must be committed before source.
2. In `ManagedGuestEngine.launch`, require **exact `InstanceState.READY`** before the SDK package readback and before any durable launch-pending write or backend launch. RESERVED after interrupted import must remain available for explicit reconciliation, not actual launch; DELETING, CONFLICT and DELETED fail closed. Preserve E61 stop validation and all existing launch authorization/pending error semantics.
3. Add focused JVM regressions: RESERVED with successful native readback refuses to launch and keeps pending unchanged; READY still dispatches the exact native user, persists pending before dispatch, and exceptions remain unknown; DELETING/CONFLICT/DELETED remain refused without changing registry or SDK. No production guest data fabricated.
4. Add E62 static audit and pinned exact-HEAD cloud/candidate workflow running E40–E62 audits, tests, build, lint, APK signature and permission checks, no split/provider/GC activation. Do not mark PASS until exact-source CI succeeds.
5. Record results and update STATUS/EXECUTION_PLAN/PRODUCT_PROGRESS after verified source CI; retain Draft PR and leave main unchanged.

## Exclusions and product readiness

No accepted arbitrary third-party Android guest Activity, split import or account/UID isolation, real Samsung phone/session proof or release signing. `supportsSplits=false`, `importsSplitApks=false`. Weighted product estimate **28.00%**, **0 accepted third-party clones**; do not merge or release as a complete clone engine.
