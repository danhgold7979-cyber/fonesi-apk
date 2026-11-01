# E63 — READY-only shared guest package mutations (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`; branch `phase/e63-ready-shared-guest-mutations-20261029` from E62 reporting HEAD `505fc58f864532e0cff8d815d77287cd0f161974`. E62 exact-HEAD [CI #40889192439](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40889192439) completed **SUCCESS** (exact source checkout, E40–E62 audit, pinned cloud/candidate test/build/lint/APK gates). [Parent Draft PR #36](https://github.com/danhgold7979-cyber/fonesi-apk/pull/36) stays open. **Original Product Phase 3 steps 13–16**, with Phase 4 steps 17–20 affected by shared-code updates. E63 is NOT Product Phase 63.

## Verified residual risk

`ManagedGuestEngine.sharedRecords(packageName)` scans every RESERVED/READY/DELETING instance for the package and only rejects DELETING or missing SDK package readback. Following an interrupted install, a RESERVED row can have a valid native package readback yet lacks confirmed create completion. E62 rejects launching such a reservation, but shared-code `update` may still include it in `affectedCloneIds` and dispatch `backend.install`, or `checkCreatePeers` may allow a new clone to be created alongside that unconfirmed guest. A successful guest-package readback does not certify READY lifecycle.

## Ordered implementation

1. Commit this plan and an EXECUTION_PLAN entry **before production code**; retain E62 exact source SHA/CI and original weighted rubric.
2. In `ManagedGuestEngine.sharedRecords`, reject every row not exactly `InstanceState.READY` before invoking `record(row)` or authorizing shared package create/update. Keep the specific DELETING rejection, signed/native readback checks, shared-scope equality and pending/busy protections. `listClones` must remain diagnostic for RESERVED and there must be no implicit promotion, uninstall, rollback, restart or user-ID reuse.
3. Add core/JVM regressions with a backend double that actually records a native package for an interrupted RESERVED install: fail closed on subsequent shared `create` before snapshots/reservations/SDK mutation, and on shared `update` before any new install; native package and registry state remain intact. Existing READY peer/new clone/update success and missing/DELETING peer guards must continue passing.
4. Add `scripts/audit_e63_ready_shared_mutations.py` and pinned E40–E63 exact-source workflow including both cloud and candidate tests, build, lint, SDK/APK signature-permission checks and no split/provider/GC activation checks.
5. Record actual source SHA and workflow result in E63 results/STATUS/EXECUTION_PLAN/PRODUCT_PROGRESS. Only call E63 technical PASS on **SUCCESS at the exact source SHA**; later docs-only commits need independent CI. Leave `main` unchanged and create a stacked Draft PR against E62, not directly against main.

## Non-goals and release blockers

No full split APK import, guest Activity/Service/Provider, independent guest Android UID/account isolation or real Samsung/other-device and social login session proof is claimed. `supportsSplits=false`, `importsSplitApks=false`, no destructive cluster GC. Weighted product estimate stays **28.00%** under `docs/PRODUCT_PROGRESS.md`, 0 real third-party apps accepted; no product release or merge.
