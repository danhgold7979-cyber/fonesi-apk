# E78 — signed PRIVATE synthetic Application attach/onCreate and scoped data-plane proof (pre-code plan)

Date: 2026-10-30. Repository: `danhgold7979-cyber/fonesi-apk`.

- Product mapping: **Phase 1 steps 7–8**, specifically the controlled-app part of step 8 (create/open/read a test data scope); prepares Phase 2 steps 9–12 but does not satisfy phone/account acceptance.
- Technical predecessor: **E77 PASS** at exact source/test SHA `6cfa12e83e66f191720b7dfb7601dcaf883f4fdc`, [CI #40991321742](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40991321742), 19/19 steps and 510 tests in each cloud/candidate gate. E77 verifies a signed PRIVATE Activity class and public no-argument constructor without instantiating it. E77 status/report documentation was subsequently committed at `bfd2d7d46bdddaad43041103c6acbcc341240c59`; its independent report-commit workflow [#40993741596](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40993741596) was still running when this plan was written. Do not treat that second SHA as CI-verified until its own run completes.
- Base: E77 branch `phase/e77-emulator-activity-preflight-proof-20261030`, documentation head `bfd2d7d46bdddaad43041103c6acbcc341240c59`; `main` remains unchanged at `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`.
- New development branch: `phase/e78-synthetic-application-lifecycle-20261030`, stacked on E77. PR will remain Draft.

## Goal

Move one carefully bounded step beyond E77: on the owned AOSP API 34 emulator, instantiate and attach the **project-owned signed synthetic** `vn.fonesi.payload.SyntheticGuestApplication` using Android's public `Instrumentation.newApplication(...)` and invoke `callApplicationOnCreate(...)` with a `SyntheticGuestScopedContext`. Verify the controlled Application sees the signed PRIVATE code loader/resources and that two fresh synthetic clone IDs have independent SharedPreferences, files and SQLite markers which survive reconstruction of the scoped Context wrapper.

This is a synthetic Android Application lifecycle/data-plane experiment only. It is not production guest process registration or a security boundary.

## Source findings and constraints

- E77's `GuestCodeLoader` already returns a `LoadedGuestCode` bound to the cryptographically re-opened private APK cluster and signed `VerifiedPrivateComponentInventory`; that inventory includes the base Application class name.
- `PrivateGuestActivityClassPreflight` already demonstrates the required pattern: `Class.forName(name, false, privateLoader)`, exact defining-ClassLoader check, type/visibility/concreteness checks and public zero-argument constructor inspection without initialization or invocation.
- The signed synthetic payload already declares `SyntheticGuestApplication`; its `onCreate` checks package/classloader/private resource identity and writes a per-scope SharedPreferences start marker. `ScopedProbeEntrypoint` already performs a synthetic-only SharedPreferences/file/SQLite write/read consistency check.
- `SyntheticGuestScopedContext` is explicitly documented as a test-only `ContextWrapper`. It runs in the host UID and same process; arbitrary code can bypass a wrapper. Never expose it to a third-party APK or production provider.
- Candidate production integration currently declares `RuntimeBindings.supportsSplits = false`; E78 must not change this or enable `importsSplitApks`.
- Android `Instrumentation.newApplication`/ `callApplicationOnCreate` are public testing APIs. Their manual use attaches a Java Application object to the supplied Context; it does **not** register that Application with ActivityThread/LoadedApk/PackageManager or create a real guest process. Call the lifecycle APIs on the Android main thread. Keep SQLite/file stress off the UI thread.

## Ordered implementation

1. Add `PrivateGuestApplicationClassPreflight` and a typed handle. Resolve only the Application class name in the signed private component inventory. Load without initialization; require exact private defining ClassLoader, exact signed class name, concrete public Application subtype, standalone/static type and public zero-argument constructor. No `newInstance`, reflection constructor invocation, static initializer or Application callback occurs during preflight.
2. Add deterministic JVM tests for exact private ownership, parent delegation, missing/wrong class, wrong base type, abstract/non-public/non-static-inner types, absent/private/protected/parameterized constructors and static-initializer non-execution on both success and failure. Keep tests independent of Android hidden APIs.
3. Extend `LoadedGuestCode` with an explicit read-only Application-class preflight method using the existing signed inventory. No package lookup against the installed original app and no fallback to the host classloader.
4. Extend the controlled synthetic host probe with proof level `e78`. Preserve E77's Activity class/constructor checks, then preflight the signed base Application class before intentionally executing the synthetic Application code. Require the exact payload package/class name and the same signed private image/APK readback.
5. In the dedicated host probe process, create two fresh canonical UUID scopes A/B. Through the private `ScopedProbeEntrypoint`, verify each starts empty, write distinct non-secret random hex markers to SharedPreferences/files/SQLite, read back all three stores, reopen new `SyntheticGuestScopedContext` wrappers for the same IDs and verify persistence, and prove A never reads B's marker (or vice versa). Reject partial/mismatched state.
6. On the Android main thread, use a fresh public `Instrumentation` instance to call `newApplication(privateLoader, signedApplicationClassName, scopedContext)` and then `callApplicationOnCreate(application)` once for each scope. Verify distinct Application instances, exact private classloader, exact attached base Context/package/resources and per-scope `onCreate` marker equal to one. Do not invoke `ActivityThread` hidden APIs or register/start a guest Activity.
7. Add an exact-source E78 receipt: source SHA, host/payload APK SHA-256, fresh run UUID, AOSP API/ABI, both scope IDs (UUIDs only), application class/loader checks, attach/onCreate flags, per-scope preference/file/SQLite readbacks and persistence. Explicitly record false for real guest Application registration, actual guest Activity attach/registration, third-party component execution, kernel UID isolation, social accounts, full split provider/import and production clone acceptance.
8. Extend `run_guest_code_emulator.py` with strict E78 receipt validation and result `PASS_E78_SIGNED_SYNTHETIC_APPLICATION_ATTACH_AND_SCOPED_DATA_ONLY`. Permit the existing bounded software-emulator path for E77 and E78 only; older proof levels remain fail-closed without KVM. Add unit tests for proof-level gating and required/forbidden E78 receipt fields.
9. Add `audit_e78_synthetic_application_lifecycle.py` and a dedicated pinned workflow. Run E40–E78 audits, the deterministic Python tests, cloud and candidate checks (510 existing tests/gate plus E78 additions), source-engine AAR/JVM tests, signed host/payload APK build/lint/audit, then boot owned AOSP API 34, install both signed APKs and execute the real E78 probe. Runtime failure must fail the workflow; no audit-only fallback. Upload only sanitized test receipts.
10. Run all checks on the exact E78 source SHA. Update E78 results, `docs/STATUS.md`, `docs/PRODUCT_PROGRESS.md` and the execution log only after exact-source CI succeeds. Keep the E78 PR Draft, E77 PR Draft and `main` unchanged. Product readiness remains **28.00%** unless real product acceptance evidence changes.

## Acceptance criteria

- All new JVM/Python tests pass; zero failures, errors or skipped tests; static audit and cloud/candidate build/lint/APK audits pass.
- Exact-source GitHub Actions is SUCCESS, including an actual booted AOSP API 34 x86_64 emulator and a fresh receipt bound to that source SHA and both APK hashes.
- The signed synthetic Application class is preflighted without initialization, then intentionally instantiated/attached through public Instrumentation APIs and receives exactly one `onCreate` per fresh scope.
- Two distinct scopes show consistent and persistent SharedPreferences/file/SQLite values with no cross-scope readback.
- Receipt explicitly denies ActivityThread/LoadedApk/PackageManager registration, actual guest Activity attach/launch, third-party code, separate UID/account, production split support and product acceptance.
- No change to production `supportsSplits=false`, no secrets, no real social app, no main merge.

## Risks and fail-closed behavior

- If the public Instrumentation API rejects the manually supplied Context or Android requires a main-thread lifecycle call, fix the invocation/threading and rerun; do not substitute reflection into hidden framework APIs.
- If scoped SQLite/SharedPreferences fail on the actual emulator, retain the failure and diagnose storage ownership/Context behavior; do not mock it into a PASS.
- If any signed Application identity, classloader, resource, receipt hash or scope readback differs, reject the receipt.
- E78 does not close Product Phase 1/2. Physical Samsung SM-A525F acceptance, real app/account isolation, guest ActivityThread/Instrumentation lifecycle, Binder/permission/process mediation and full split provider remain separate blockers.
