# E37 — provider capability snapshot and truthful manager gating results

Date: 2026-10-29. **E37 CI PASS** on exact tested source SHA `2d35a8a99981131e32aeb6ac98a40dfaf0ef12e7`. Workflow: [#40741094158](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40741094158). Test-only artifact: [fonesi-E37-provider-capabilities-TEST-ONLY](https://github.com/danhgold7979-cyber/fonesi-apk/actions/artifacts/12838649921), SHA-256 `e757cad0a2387acdab312f2cc4d38dc960b30715ce69469ce90adf917986d6a9`, retained until 2026-11-04.

## CI evidence

- Fail-closed capability source audit and Python compilation: PASS.
- Cloud/core/APK/app gate: PASS, 326 tests; build, lint and exact APK signature/permission audit passed.
- Opt-in candidate gate: PASS, 326 tests; build, lint and exact APK audit passed.
- Explicit acceptance-boundary check: PASS; split execution, real guest lifecycle and account isolation remain unaccepted.
- E36 store regression workflow also passed on the same source SHA: [#40741094925](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40741094925).

## Implemented

- Added `EngineCapabilities` and `EngineIsolationLevel` to the core engine contract. The snapshot distinguishes operational single-APK preflight/install/readback from verified guest Activity/Service/Receiver/Provider lifecycle, framework mediation, native page-size support, update readback and isolation level.
- Legacy `CloneEngine` implementations default to all-false capabilities and `UNKNOWN` isolation. `EngineAvailability.Ready` alone does not imply guest acceptance.
- `ManagedGuestEngine` reports only the narrow single-APK operational support available when its backend is ready. It leaves split import, all guest component lifecycle flags, framework mediation, native page-size acceptance and update acceptance false; isolation remains `UNKNOWN`.
- `RuntimeState` carries the capability snapshot and the manager UI now displays separate states for single-APK operations, split support, guest Activity lifecycle and account isolation.
- Added `EngineCapabilitiesTest` for unavailable engines, legacy ready engines and explicit isolation-level comparisons.
- Added `scripts/audit_engine_capabilities.py` and the dedicated E37 workflow to prevent unsupported capability claims.

## Correction made during CI

The first E37 attempt failed at compile time because the test imported `UpdateTarget` from the wrong package. The import was corrected in commit `2d35a8a99981...`; the green E37 run above is on that corrected SHA. The failed attempt is not counted as a pass.

## Acceptance boundary

E37 improves capability reporting and fail-closed defaults. It does **not** implement a guest runtime, prove third-party app execution, prove cross-clone account/session isolation, enable split APKs or validate 16 KB native libraries. `RuntimeBindings.supportsSplits=false`, the single-APK guard, `real_guest_activity_registered=false`, `kernel_uid_isolation=false` and `production_clone_accepted=false` remain unchanged.

## Next

E38 should address **private package-cluster reference lifecycle and retention**: define how immutable cluster generations are referenced by clone metadata/update tasks, ensure no generation is garbage-collected while active/review-required/rollback work refers to it, and test recovery across process restart. It must remain storage-only and must not wire the cluster store into SpaceCore's install path until a provider actually supports and verifies split import.

## Product progress

The product estimate remains **28.00% weighted / ~30% rounded**. E37 is reliability/capability-contract work, not acceptance of a new user-facing clone capability. Real third-party clone applications accepted: **0**.
