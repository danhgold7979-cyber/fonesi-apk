# E65 — native guest delete identity continuity results

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`; branch `phase/e65-native-guest-delete-identity-20261029`; [Draft PR #39](https://github.com/danhgold7979-cyber/fonesi-apk/pull/39) stacked on E64 [Draft PR #38](https://github.com/danhgold7979-cyber/fonesi-apk/pull/38). Original **Product Phase 3 steps 14–16**, not Product Phase 65.

## Changes implemented

- `core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt`: after initial guest package readback, stopped/unknown guard and foreground authorization, `delete` reads the native guest **again before** persisting DELETING or calling `backend.uninstall`. Exact package, version, complete signer set and explicit not-running state must match the initial guest. A package initially absent must remain absent. Changed/active/newly appearing package aborts without SDK uninstall, durable state mutation, ID reuse or destructive fallback. Existing post-uninstall absence confirmation is preserved.
- Added five protocol/JVM tests: `changedNativeVersionBeforeDeleteDispatchDoesNotRemoveGuest`, `changedNativeSigningIdentityBeforeDeleteDispatchDoesNotRemoveGuest`, `guestBecomingRunningBeforeDeleteDispatchCannotBeUninstalled`, `nativeGuestAppearingAfterInitiallyMissingReadbackRequiresReconciliation`, and `consistentlyAbsentNativeGuestCanRetireWithoutSdkUninstall`. Prior successful deletion and pending launch guards remain.
- Added `scripts/audit_e65_native_guest_delete_identity.py` and pinned exact-source `.github/workflows/e65-native-guest-delete-identity.yml` with all E40–E65 audits and cloud/candidate checks.

## Verified technical results

**E65 technical PASS**, exact code/test SHA `9393f288be8d311e3c2f4f65a989c022eda190fa`: [GitHub Actions #40897506047](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40897506047) **SUCCESS**. Workflow verified source SHA, E40–E65 source audits, pinned SDK/JDK, **475 tests each cloud and candidate gate**, build, lint, APK signatures/permission allowlists and no split/provider/garbage-collection activation. [Test-only receipts artifact #12907962058](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40897506047/artifacts/12907962058). Parent E64 code/test `a54c034d21711b7d15d301ebe67a5466c19a3d7d` passed [CI #40895219310](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40895219310) (470 tests/gate), its report HEAD `3da8eddd267b0118281985167d4adc1a1f7567e7` separately passed [CI #40896767719](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40896767719). The E65 documentation reporting SHA has its own CI requirement; it is **not** the code SHA above.

No test in E65 executes a third-party guest Activity or proves isolation after deletion. The second pre-dispatch readback narrows SDK identity drift, but without an atomic native transaction it cannot preclude arbitrary uncooperating SDK races.

## Product acceptance still blocked

Candidate `supportsSplits=false`/`importsSplitApks=false`; no accepted guest Activity/Service/Provider, independent Android UID/account isolation, Facebook/Zalo/TikTok live sessions, Samsung/other-device acceptance, update session retention or release signing. **28.00% weighted product readiness**, **0 accepted real third-party clone apps**, Draft PR, `main` unchanged.
