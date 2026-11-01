# E63 — READY-only shared guest mutation results

Date: 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`, original **Product Phase 3 steps 13–16**, plus shared update precautions for Phase 4 steps 17–20. Branch `phase/e63-ready-shared-guest-mutations-20261029`, [Draft PR #37](https://github.com/danhgold7979-cyber/fonesi-apk/pull/37), stacked on E62 [Draft PR #36](https://github.com/danhgold7979-cyber/fonesi-apk/pull/36).

## Implemented

- `ManagedGuestEngine.sharedRecords` now refuses all native-backed RESERVED peers and other non-READY rows **before SDK package readback**. Its callers `checkCreatePeers` and `update` fail closed before beginning a new snapshot/import or shared APK install, rather than treating an interrupted install with visible native bytes as confirmed creation.
- Added **three JVM regressions**: new clone refusal without a new snapshot/SDK user, shared update refusal without a new install, and mixed READY/RESERVED peer refusal without silently narrowing affected scope. Existing READY flows, missing/DELETING peer checks, E62 launch and E61 stop guards remain.
- Added `scripts/audit_e63_ready_shared_mutations.py` and E40–E63 exact-source workflow with both flavors.

## Verification

**E63 technical PASS** on exact source/test commit `bbaf4bbb4765033306d8a84619ab245d16e18513`: [GitHub Actions #40894087779](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40894087779) **SUCCESS** with verified checkout SHA, E40–E63 audits, pinned Android/JDK toolchain, **467 tests in each cloud and candidate test gate** (zero test failures reported), build, lint, APK signature/permissions and no split/provider/garbage-collection activation checks. [Test-only receipts artifact #12906503636](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40894087779/artifacts/12906503636). These are JVM/Robolectric and build receipts, **not** runtime execution proof of a third-party guest.

The results-only reporting SHA `ba26d2104ab7a28d23b7bfd96a0c8451b9bf71b1` independently passed [GitHub Actions #40895952892](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40895952892) **SUCCESS**. The verified code/test SHA remains `bbaf4bbb4765033306d8a84619ab245d16e18513`.

## Remaining limitations

Production `supportsSplits=false` and `importsSplitApks=false`, no independently verified guest Activity/Service/Provider, Android UID/account isolation, required social-app logins, Samsung/non-Samsung phone tests, retained session after updates or release signing. Keep Draft PR; `main` unchanged. Weighted product readiness **28.00%** under the official rubric; accepted real third-party clone apps **0**.
