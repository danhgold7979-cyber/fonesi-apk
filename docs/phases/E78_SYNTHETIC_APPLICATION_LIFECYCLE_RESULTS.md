# E78 — signed synthetic Application attach/onCreate and scoped data-plane proof

Date: 2026-10-30
Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk)
Product mapping: **Phase 1, steps 7–8** (controlled-app portion of step 8); prepares Phase 2 steps 9–12. This does not create a new product Phase.

## Exact-source CI result

**E78 technical PASS**

- Source/test SHA: `57d60cb80d6cece692b2898afd15d8668fd14883`.
- GitHub Actions [#40998462184](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40998462184): **SUCCESS**, 17 substantive workflow steps plus setup/teardown.
- Exact-source checkout/assertion, E40–E78 audits, 13 Python receipt/fingerprint tests, cloud/candidate checks, source-engine AAR/JVM tests, signed host/payload APK build/lint/static audit, AOSP API 34 image installation and E78 runtime receipt gate all passed.
- Cloud and candidate each report **510 tests**, 0 failures/errors/skips (173 core + 68 APK runtime + 269 Android-simulated).
- Source-engine JVM XML reports **63 tests**, 0 failures/errors/skips, including five E78 Application preflight tests. Source-engine verification also reports 18 native-library host integration tests passed and an ARM64 AAR built; Android native execution/provider integration remains false.
- [E78 test-only artifact #12952618461](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40998462184/artifacts/12952618461).

## AOSP API 34 runtime evidence

The pinned AOSP API 34 x86_64 image booted under **software emulation** because the GitHub runner has no accessible KVM. Both signed synthetic host and payload APKs installed.

- Result: `PASS_E78_SIGNED_SYNTHETIC_APPLICATION_ATTACH_AND_SCOPED_DATA_ONLY`.
- Emulator boot and APK installation: true; acceleration: `software`; UI fingerprint: four samples with three consecutive stable transitions.
- Source SHA: `57d60cb80d6cece692b2898afd15d8668fd14883`.
- Host probe APK SHA-256: `32d02243206ce3fbe1529a35ef1a6989dd534a0ebe7bbf56cf1fc06cd9ca9cc6`.
- Signed payload APK SHA-256: `c19a59c4dbf08ebc658d1d7602fa55e0154203970e58ceb008494c44e1055859`.
- Android API 34; host process `vn.fonesi.nativeprobe:fonesi_guest_ui`; PID 2355; UID 10142.
- Two distinct canonical scope IDs: `e60a014c-9cac-462a-922d-f3cc64f5cbb1` and `90630e15-a823-42a3-a108-5d9a13ab23b6`.
- Signed PRIVATE `vn.fonesi.payload.SyntheticGuestApplication` was preflighted without initialization. Two distinct Application objects were attached with public `Instrumentation.newApplication`; `callApplicationOnCreate` ran on Android's main thread once for each fresh scope.
- Both scopes passed SharedPreferences, private-file and SQLite write/read; data survived reconstruction of `SyntheticGuestScopedContext` wrappers; cross-scope readback did not expose the other scope's marker.

## Explicit limits — not product acceptance

- `same_android_uid=true`: logical namespaces in one host UID/process, **not** OS-level isolation.
- `guest_application_registered_with_activity_thread=false`; `guest_application_registered_with_package_manager=false`; `full_guest_application_lifecycle_verified=false`.
- `actual_guest_activity_attached=false`; `real_guest_activity_registered=false`.
- `third_party_component_executed=false`; `third_party_guest_components_tested=false`.
- `kernel_uid_isolation=false`; `social_accounts_tested=false`; `production_clone_accepted=false`.
- Candidate `RuntimeBindings.supportsSplits=false`; `SpaceCoreBackend` still rejects input APK sets with more than one APK. E78 did not enable split import/provider integration.
- No Samsung SM-A525F or other physical-device test was run. Software emulator evidence is not physical-device evidence.

**Conclusion:** E78 is a technical PASS for a project-owned signed synthetic Application and same-UID scoped data experiment. It is not a production guest Application/Activity runtime, a third-party app clone, an independent account sandbox, or completion of Product Phase 1/2. Product readiness remains **28.00%** under the existing weighted rubric; accepted real third-party clone apps remain **0**. Keep PR #52 Draft, PR #51 Draft and `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` unchanged.

## Next engineering boundary

Proceed with a separately planned E79 focused on process-death/restart durability of the same synthetic scoped stores. This can strengthen the controlled data-plane proof but still cannot be counted as real UID/account isolation or product clone acceptance. The E79 plan must preserve the original 8-phase/32-step roadmap and must not enable candidate split support.
