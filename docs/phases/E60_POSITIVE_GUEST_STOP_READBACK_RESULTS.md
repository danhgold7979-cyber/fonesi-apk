# E60 — positive native guest stop readback: results

Date: 2026-10-29. Repository [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Development branch `phase/e60-positive-guest-stop-readback-20261029`, stacked on E59. Original **Product Phase 3 steps 14–16** (clone lifecycle); technical E60, NOT a product phase.

## Parent verified evidence

E59 code/test source `7176ae9c8c9eaf92989ddef3e4882b06e3e9c367`, [CI #40883514023](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40883514023) **SUCCESS**: 453 tests in each cloud/candidate gate, 0 fails/errors/skips. E59 reporting SHA `90ab636350c14230c87230d17af0dea0886bf4c2` separately has [CI #40884715219](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40884715219); its exact status must be rechecked and is not assumed PASS.

## Implementation (code committed; CI acceptance pending)

- `ManagedGuestEngine.stop` retains the existing authorization and native `backend.stop` call, then requires **nonnull `GuestPackage` readback** with the expected package identity, nonnegative version, nonempty signer set and `running=false` before clearing durable `InstanceRegistry.launchPending`. Null, corrupt, still-running or exception-throwing native responses leave unknown launch/stop outcome pending.
- Added six focused core/JVM tests: missing readback, wrong package, negative version, no signers, backend returns still-running, and valid explicit stopped readback. Existing create/update/launch/stop tests are preserved; all guest fixtures remain test-only.
- Added E60 static source safety audit and pinned exact-head cloud/candidate GitHub Actions workflow incorporating all E40–E60 audits, SDK build/test/lint, signed APK/permissions, and split/provider/GC guard. No new engine, data deletion or UI success fallback is enabled.

## Acceptance

**E60 technical CI PENDING.** Do not call E60 PASS without the exact-source workflow success, receipt/test counts, and explicit no-split/no-provider/GC checks. Build/unit tests cannot certify a real social app was stopped by SpaceCore on a physical Android device.

## Persistent product and release blockers

`supportsSplits=false`, `importsSplitApks=false`, no accepted real guest Activity/Service/Provider, kernel UID/account isolation, session-update testing on Samsung/other phones or release-signed APK. **Weighted product estimate remains 28.00% (~30% rounded); 0 accepted real third-party clone apps.** Keep Draft PR and `main` unchanged.
