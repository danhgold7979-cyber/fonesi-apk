# E37 — provider capability snapshot and truthful operation gating (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`; do not create a new phase branch. E36 private cluster store integrity CI passed on source SHA `0fb45f7439176cdd9e62b1f330217ca9558a8ff0` (323 tests per cloud/candidate gate). E37 must distinguish verified storage capabilities from provider execution capabilities.

## Pre-code inspection

- `core/src/main/kotlin/vn/fonesi/core/CloneEngine.kt`: current `EngineAvailability.Ready` exposes only update scope and `importsSplitApks`; a Ready engine does not prove guest lifecycle or isolation.
- `app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt`: candidate declares `supportsSplits=false` and explicitly says engine initialization is not clone/isolation acceptance.
- `app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt`: `checkedInput` requires exactly one APK; `inspect()` verifies package/version/signer and an SDK-owned private code file.
- `app/src/main/java/vn/fonesi/app/AppsViewModel.kt` and `ManagementCards.kt`: user-facing clone states must remain tied to actual engine readback and must not infer guest UI/account success from a launch request.

## Goal

Introduce a typed, conservative capability snapshot and use it to make UI/planner decisions explicit. A provider can be initialized and able to manage a single APK while guest lifecycle, split import, 16 KB support or account isolation remain unverified.

## Implementation sequence

1. Add an immutable `EngineCapabilities` model in core. Include single-APK inspect/install, split-cluster import, guest Activity/Service/Receiver/Provider lifecycle, resource/configuration, native-library/page-size support, update/readback guarantees, and isolation level (`HOST_UID_NAMESPACE`, `SEPARATE_OS_UID`, `SEPARATE_GUEST_OS`, `UNKNOWN`). Unsupported/default capabilities are false/UNKNOWN.
2. Preserve source compatibility for existing `CloneEngine` implementors with a default capability snapshot derived conservatively from `availability`; a `Ready` value alone must never imply real guest lifecycle or account isolation. Keep `importsSplitApks=false` as the authoritative legacy guard until all call sites migrate safely.
3. Map the SpaceCore candidate explicitly: single-APK metadata/install may be available only when the existing adapter reports ready; split import false; real guest component registration false; framework mediation unknown; kernel UID isolation false/UNKNOWN; 16 KB native support false.
4. Add pure core tests proving unavailable engines expose no capabilities; ready legacy engines do not implicitly claim guest lifecycle/isolation; split support cannot be true when `importsSplitApks=false`; unknown isolation never satisfies a stronger isolation requirement.
5. Expose a short capability summary in the manager diagnostics/status UI. Wording must distinguish “engine initialized”, “APK stored/verified”, “provider readback verified”, “guest component lifecycle verified” and “account isolation verified”. Do not change a clone record to READY solely from capability metadata.
6. Gate split-specific source operations/plans on both provider capability and the legacy `importsSplitApks` flag. Keep single-APK flow unchanged except for clearer truthful status messages.
7. Add source audit assertions to prevent `supportsSplits=true`, `importsSplitApks=true`, or `SEPARATE_OS_UID` from being claimed without dedicated evidence/acceptance references.
8. Run core/APK auditor tests, cloud build/audit, candidate build/audit and exact manifest/signature checks. Record exact SHA and test counts. No provider switch or guest code execution in E37.

## Acceptance criteria

- A ready engine does not imply a verified guest Activity, guest Service/Receiver/Provider, or account isolation.
- Current SpaceCore candidate reports split support false and does not advertise OS-level isolation or verified guest component registration.
- UI strings do not equate a clone tile or accepted launch request with guest app/login success.
- Capability defaults are conservative and tested.
- E36 private store tests remain green; E37 does not alter package-cluster storage or enable split execution.

## Not claimed

E37 does not create a production guest runtime, execute a third-party app, prove account/session isolation, enable split APKs, or increase product readiness merely because the capability model and UI have tests.
