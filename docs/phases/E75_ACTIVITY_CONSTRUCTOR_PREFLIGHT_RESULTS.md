# E75 — signed PRIVATE Activity constructor preflight results

Date: 2026-10-30. Repository `danhgold7979-cyber/fonesi-apk`; original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15. Branch `phase/e75-activity-constructor-preflight-20261030`, [Draft PR #49](https://github.com/danhgold7979-cyber/fonesi-apk/pull/49) stacked on E74 [Draft PR #48](https://github.com/danhgold7979-cyber/fonesi-apk/pull/48). [Pre-code plan](E75_ACTIVITY_CONSTRUCTOR_PREFLIGHT_PLAN.md) and EXECUTION_PLAN entry were committed before production code.

## Implemented

- `PrivateGuestActivityClassPreflight.requireMatches` now inspects `candidate.getConstructor()` and requires a public zero-argument constructor before returning the selected signed PRIVATE Activity type. It never calls the constructor or `newInstance`.
- Preserved exact PRIVATE defining classloader, target class name, Activity assignability, public/concrete class and static nested-class constraints.
- Added regressions for public no-arg constructor with side-effect sentinel, parameterized-only, private/protected constructor, and public no-arg plus parameterized overload. Existing signed manifest without DEX still fails class resolution. Added E75 static audit and pinned E40–E75 workflow.

## Exact-source technical verification

**E75 technical PASS at source/test SHA `f6da373ae11806cce727fab0f07433d521a2c594`**: [GitHub Actions #40927202073](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40927202073) **SUCCESS**. Exact source SHA was checked out. E40–E75 audit, **510 tests per cloud/candidate gate**, lint, build, APK signatures/permissions, candidate SDK, source-engine native AAR/JVM class preflight and no split/provider/GC activation all passed. The source-engine `PrivateGuestActivityClassPreflightTest` suite was required by workflow and passed (the audit requires at least eight class-preflight tests; this revision contains the original eight plus two constructor regressions). [Sanitized test-only artifact #12920584024](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40927202073/artifacts/12920584024).

This report/integration commit has a different SHA and requires its own CI. E74's opt-in synthetic host Activity probe builds but does not run in this CI; it still does not instantiate, attach or launch a guest Activity. Constructor-shape proof is not framework lifecycle proof.

## Remaining product acceptance blockers

No `ActivityThread`/`Instrumentation` attach, guest Context/PackageManager/Binder permission virtualization, independent Android UID/account sessions or production split APK provider (`supportsSplits=false`, `importsSplitApks=false`). No accepted Facebook/Zalo/TikTok session on Samsung/non-Samsung phones; release signing remains open. **Weighted product readiness 28.00%, accepted real third-party clone apps 0.** Keep Draft, `main` unchanged.
