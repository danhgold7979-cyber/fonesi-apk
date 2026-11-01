# E72 — signed PRIVATE Application process inheritance safety results

Date: 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`; technical milestone E72 under original **Product Phase 1 steps 7–8**, preparing Phase 3 step 15. Branch `phase/e72-signed-app-process-inheritance-20261029`, [Draft PR #46](https://github.com/danhgold7979-cyber/fonesi-apk/pull/46) stacked on [E71 Draft PR #45](https://github.com/danhgold7979-cyber/fonesi-apk/pull/45). [Detailed pre-code plan](E72_SIGNED_APP_PROCESS_INHERITANCE_PLAN.md) and execution plan entry were committed before source modifications.

## Code and regressions

- `apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkComponentCatalog.kt` now retains the optional signed base `<application android:process>` declaration as `applicationProcessName`, rejecting all feature/config APK attempts to override that base process policy. Retains bounded binary manifest parsing, exact PRIVATE base/split signature, version, digest and repeat-store-readback integrity.
- `SignedPrivateExplicitActivityResolver.select` refuses both internal and external Activity selection if the signed base Application has an explicit process declaration, in addition to E71 component/alias/target process guards, E70 exported/enabled, inherited permission and signed Activity target checks. No Android Intent or component starts.
- Two new real signed APK fixtures (`catalog-app-process`, `catalog-split-app-process`) generated with disposable test keys, plus four JVM regressions in signed catalog and Activity resolver tests, covering inherited process denial for both callers, process inventory readback, split override refusal and unaffected ordinary signed Activity selection. Added E72 static source audit and pinned exact-SHA GitHub workflow.

## Exact source acceptance

**E72 technical PASS at exact implementation/test SHA `1b8c5a7e803fcb7832d790d8614d1c92c219953d`: [GitHub Actions #40912239445](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40912239445) **SUCCESS**, exact-source checkout, E40–E72 audits, **510 tests per cloud/candidate gate**, signed binary-manifest JVM fixture tests actually executed, pinned SDK/JDK/cloud/candidate build/lint, signed APK and permission allowlist checks, opt-in source-engine native AAR/source JVM tests and no full split/provider/GC activation. [Sanitized test-only artifact #12914827167](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40912239445/artifacts/12914827167). E71 source SHA `c1d825bbd356db40e175555da23d786e124ff481` [CI #40911242212](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40911242212) **SUCCESS** (506 tests per cloud/candidate gate); E70 source `469b5aa567344154622c099a73351b6ae60494d9` [CI #40910681708](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40910681708) also PASS (503 tests/gate).

This E72 results/integration commit has a new SHA and requires **its own** exact-source CI; E70/E71 reporting SHAs require separate verification as well. Do not equate signed manifest inspection with guest execution.

## Unmet product conditions

No actual guest Android Application/Activity/Service/Provider lifecycle, Binder/process mediation, independent Android UID/account isolation, or production full split import (`supportsSplits=false`, `importsSplitApks=false`). No real Facebook/Zalo/TikTok phone sessions or Samsung/non-Samsung acceptance; release signing remains undone. **Weighted product readiness 28.00%, accepted real third-party clone apps 0**. Keep all stacked PRs Draft and `main` unchanged.
