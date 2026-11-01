# E80 — controlled signed PRIVATE synthetic Activity Java constructor on Android (technical results)

Date: 2026-10-30. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15, not new Product Phase. Branch `phase/e80-signed-private-activity-construction-20261030`, [Draft PR #54](https://github.com/danhgold7979-cyber/fonesi-apk/pull/54) stacked on E79 Draft PR #53. [Pre-code plan](E80_SIGNED_PRIVATE_ACTIVITY_CONSTRUCTION_PLAN.md).

## Exact-source CI and scope

- **E80 source/test SHA `050deaedeaebd04f9e2327bfb3741636631574e3`** — [commit](https://github.com/danhgold7979-cyber/fonesi-apk/commit/050deaedeaebd04f9e2327bfb3741636631574e3), [CI #41018992724](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41018992724) **SUCCESS**, all 17 substantive workflow steps.
- Cloud **510 tests**, candidate **510 tests**, signed APK build/lint/permission audits PASS; source engine **63 JVM tests** and native AAR checks; **27 Python** emulator/receipt/fingerprint tests and **10 synthetic payload JVM** tests (7 inherited marker + 3 constructor); 0 errors, failures or skips in required suites. E40–E80 source audits passed with the older E74/E77 negative safety assertions retained.
- Owned AOSP API 34 x86_64 **software emulator** booted and installed two independently signed synthetic project-owned APKs. [Test-only artifact #12961168620](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41018992724/artifacts/12961168620) contains the exact-source receipt and XML reports.
- Initial E80 source `a398c62fa06562b91063d48be399fe3530595239` [CI #41018609052](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41018609052) **FAILED** a historical E74 audit source string after the safe proof-mode extension. Added a pre-code plan amendment, then updated the E74/E77 static audits to demand the full E80-augmented preflight predicates while keeping all preceding fail-closed guards. Final exact-source CI passed; do not call the old failed run PASS.

## AOSP receipt verified

| Property | E80 write process | E80 verify after package force-stop |
| --- | --- | --- |
| Android host PID | 2324 | 2941 |
| Android host UID | 10142 | 10142 |
| Proof level | e80 | e80 |
| Exact signed PRIVATE synthetic Activity class | `vn.fonesi.payload.PayloadActivity` | same |
| Activity object constructed from PRIVATE loader | true | true |
| `Instrumentation.newActivity` on Android main Looper | true | true |
| Test-only constructor count | 1 | 1 |
| Activity has not been framework attached | true (`getBaseContext()==null`) | true |
| Synthetic Application start markers for A/B | 1, 1 | 2, 2 |
| A/B SharedPreferences, file, SQLite and cross-readback | PASS | PASS after process death |

The runner confirmed package force-stop and absence of the old dedicated process, then restarted with the exact same signed PRIVATE APK cluster `057e769c-6b69-4000-990a-73d97b867b22`. It required distinct process PIDs/tokens, identical signed APK/source hashes, two different scope IDs, exact data marker readback, and application start counters 1→2. Source SHA matches `050deaedeaebd04f9e2327bfb3741636631574e3` in both stage receipts.

- Signed host probe APK SHA-256: `ec2ffe6a74c40513d82d5c672a31c4e4a8295187b0d273445f974ff31aa42b21`.
- Signed payload APK SHA-256: `484c1949c3824f25ba4c379dfc83bf9d6b8b3a81ea28f22aa15413944b8e254e`.
- Overall evidence marker: `PASS_E80_SIGNED_SYNTHETIC_ACTIVITY_CONSTRUCTOR_ONLY`.

## Explicit boundary and next work

This proof **only constructs a Java object** of the test-owned signed PRIVATE Activity without attaching or executing its Android `onCreate`. Both receipts explicitly keep `e80_guest_activity_oncreate_called=false`, `actual_guest_activity_attached=false`, `guest_application_registered_with_activity_thread=false`, `real_guest_activity_registered=false`, `third_party_component_executed=false`, `kernel_uid_isolation=false`, `social_accounts_tested=false`, `production_clone_accepted=false`. These two scopes run under **the same host UID** and are not independent Android accounts/security sandboxes.

Missing product acceptance: full guest ActivityThread/PackageManager/Instrumentation/Context/Binder/permission mediation, actual third-party application execution, full production base/split APK import, independent account/session proof, Samsung/other-device acceptance and final release signing. `RuntimeBindings.supportsSplits=false`, candidate `SpaceCoreBackend` single-APK guard unchanged. No root or original-app launch substitution.

**Weighted product completion remains 28.00%, 0 accepted real third-party clones** under unchanged [PRODUCT_PROGRESS](../PRODUCT_PROGRESS.md) rubric. Keep PR #54 Draft, `main` unchanged. This results/documentation commit is a **new SHA** and requires its own exact-source CI before reporting that commit as separately verified. The next milestone should prioritize the actual production guest framework lifecycle gap rather than treat constructor creation as a launched guest.
