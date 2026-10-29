# E71 — fail closed on guest Activity process declarations without process mediation (pre-code plan)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e71-signed-activity-process-safety-20261029` stacked on E70 implementation HEAD `469b5aa567344154622c099a73351b6ae60494d9`, [Draft PR #44](https://github.com/danhgold7979-cyber/fonesi-apk/pull/44). E70 CI [#40910681708](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40910681708) was **in progress** at plan time. E69 exact-source [CI #40908213615](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40908213615) PASS. Original Product **Phase 1 steps 7–8**, preparatory for Phase 3 step 15; E71 is not Product Phase 71.

## Identified gap

E70's signed private explicit Activity resolver validates enabled, exported, permissions, alias target and signed split membership, but does not enforce `android:process`. The future guest proxy has no guest process allocator or Binder mediation. Returning a dispatchable-looking target with a nondefault requested or alias target process could accidentally run code in a different host process. Since this is a metadata-only selector, it must fail closed rather than ignore the declared process. No Android launch is authorized.

## Ordered changes and acceptance

1. Commit this plan and EXECUTION_PLAN record before source changes. Inspect exact E70 source and CI, preserve all existing permissions, application policy and exported checks.
2. In `SignedPrivateExplicitActivityResolver.select`, require both requested Activity/alias and resolved Activity target `processName == null` before returning a selection. Null is not evidence of runtime process allocation; it only means no explicit per-component override. Do not invent an Android Activity or export permission.
3. Add signed-inventory JVM regressions using actual E70 signed binary-manifest inventory and constrained copies: explicitly assigned process on Activity, alias, and alias target must refuse selection for both internal and external caller policies. Positive existing plain Activity and alias remain valid; no launch or fallback.
4. Add E71 static audit and exact-HEAD workflow E40–E71 with pinned cloud/candidate build, signed fixture JVM tests, source-engine opt-in tests/AAR audit, no split/provider/GC activation. Technical PASS only on exact source SHA SUCCESS.
5. Record results and update STATUS/EXECUTION_PLAN/PRODUCT_PROGRESS after verified CI; reporting SHA requires separate CI. Keep Draft stacked on E70. No main merge or product release.

## Product boundaries

Still no guest Activity/Service/Provider lifecycle, guest process mediation, complete split APK provider, independent Android UID/account sessions, social-app tests on Samsung/non-Samsung phones or release signing. `supportsSplits=false`, `importsSplitApks=false`; **28.00% weighted readiness**, **0 real accepted clones**.
