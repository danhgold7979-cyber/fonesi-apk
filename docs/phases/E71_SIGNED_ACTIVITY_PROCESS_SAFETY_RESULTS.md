# E71 — native guest Activity process declaration denial (technical results)

Date: 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`; original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15. Branch `phase/e71-signed-activity-process-safety-20261029` and [Draft PR #45](https://github.com/danhgold7979-cyber/fonesi-apk/pull/45) stacked on E70; [pre-code E71 plan](E71_SIGNED_ACTIVITY_PROCESS_SAFETY_PLAN.md) was committed before source changes.

## Implemented

`SignedPrivateExplicitActivityResolver.select` now rejects any explicitly declared `android:process` for either selected Activity/alias or its signed Activity target, for both internal trusted and external untrusted policies. No process allocator or Android Binder/Activity mediation exists. Three JVM signed-inventory regressions cover explicit Activity, alias and target process overrides, preserving normal signed public/private Activity behavior. Added E71 source audit and pinned E40–E71 exact-source workflow, with cloud/candidate, signed manifest tests and optional source-engine AAR/native checks.

## Verification

**E71 technical PASS at exact implementation/test source SHA `c1d825bbd356db40e175555da23d786e124ff481`**: [GitHub Actions #40911242212](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40911242212) **SUCCESS**, verified checkout SHA, E40–E71 source audits, **506 tests per cloud/candidate gate**, build/lint, APK signature/permission allowlist, real signed fixture JVM regressions, opt-in source-engine native AAR/source tests, and no split/provider/GC activation. [Sanitized test-only artifact #12914412753](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40911242212/artifacts/12914412753). E70 implementation SHA `469b5aa567344154622c099a73351b6ae60494d9` passed [CI #40910681708](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40910681708), 503 tests per gate.

This results merge commit has a newer SHA and must pass independent CI before its own HEAD is called verified. This is a **read-only signed component policy** and does not run Android guest Activity, mediate Binder, create independent UID/account, activate full-split provider or establish phone acceptance.

## Product blockers

No real guest Application/Activity/Service/Provider component execution or Android guest process mapping; `supportsSplits=false`/`importsSplitApks=false`. Samsung/other-phone social apps, independent login/session isolation, update retention and release signing remain unaccepted. Weighted readiness **28.00%**, real third-party clone applications accepted **0**. Keep Draft, `main` unchanged.
