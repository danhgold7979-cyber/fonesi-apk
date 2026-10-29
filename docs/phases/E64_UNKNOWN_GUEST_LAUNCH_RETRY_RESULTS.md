# E64 — unknown guest launch retry quarantine results

Date: 2026-10-29. Exact repository: `danhgold7979-cyber/fonesi-apk`; branch `phase/e64-unknown-guest-launch-retry-gate-20261029`, [Draft PR #38](https://github.com/danhgold7979-cyber/fonesi-apk/pull/38), stacked on E63 [Draft PR #37](https://github.com/danhgold7979-cyber/fonesi-apk/pull/37). Original **Product Phase 3 steps 14–16**, not a new product Phase.

## Implemented

- `core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt`: `ManagedGuestEngine.launch` requires a positive native readback with **known running Boolean state** before persisting another launch-pending marker and dispatching the SDK. Previous asynchronous launch still unknown (`running == null`) now aborts without a second SDK launch, preserving the pending marker. Positive SDK running readback can reconcile the pending result; verified exact native stopped readback during explicit stop can also allow a subsequent launch.
- Three JVM protocol regressions in `core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt`: `pendingUnknownLaunchCannotDispatchTwiceBeforeVerifiedStop`, `failedLaunchCannotBeBlindlyRetriedAcrossEngineRecreation`, and `positiveRunningReadbackResolvesUnknownAndAllowsReopenOfSameNativeGuest`. No fake guest engine or app source shortcut.
- Strengthened `scripts/audit_e62_ready_guest_launch_gate.py` to preserve E62 READY-only preflight when new known-state guard was added; `scripts/audit_e64_unknown_guest_launch_retry.py` and pinned `.github/workflows/e64-unknown-guest-launch-retry.yml` cover E40–E64.

## Verified acceptance

**E64 technical PASS at exact source/test SHA `a54c034d21711b7d15d301ebe67a5466c19a3d7d`**: [GitHub Actions #40895219310](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40895219310) **SUCCESS**, with exact source checkout, E40–E64 source audits, pinned Android/JDK, **470 tests each cloud and candidate gate**, build/lint, APK signatures/permissions, SDK integration artifact checks and disabled split/provider/GC guards. [Test-only receipt artifact #12907157275](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40895219310/artifacts/12907157275). Parent E63 exact-source `bbaf4bbb4765033306d8a84619ab245d16e18513` passed [CI #40894087779](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40894087779) (467 tests each gate).

This reporting merge commit has a different SHA and needs its own exact-HEAD CI before it is considered verified. JVM synthetic backend doubles and signed APKs **do not prove** a real third-party Android guest process/account session.

## Unmet product gates

Production `supportsSplits=false`, `importsSplitApks=false`, unverified guest Activity/Service/Provider, independent Android UID and account isolation, real Facebook/Zalo/TikTok sessions, Samsung/other-device testing, update session preservation and release signing. **Weighted product readiness 28.00%**, **0 real third-party clone applications accepted**. Keep both technical PRs Draft and `main` unchanged.
