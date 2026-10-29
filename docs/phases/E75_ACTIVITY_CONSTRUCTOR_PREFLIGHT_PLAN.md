# E75 — signed PRIVATE Activity constructor-shape preflight (pre-code plan)

Date: 2026-10-30. Exact repository `danhgold7979-cyber/fonesi-apk`. Branch `phase/e75-activity-constructor-preflight-20261030` from E74 source `0c62831931366612f06c69d8396e68801f384ceb`, [Draft PR #48](https://github.com/danhgold7979-cyber/fonesi-apk/pull/48), stacked on E73 [Draft PR #47](https://github.com/danhgold7979-cyber/fonesi-apk/pull/47). E74 source CI [#40919455047](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40919455047) **SUCCESS**; E73 source CI [#40917685335](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40917685335) **SUCCESS**. E74 proves only private signed classloader ownership via an opt-in synthetic host Activity; no guest Activity was instantiated or attached. E75 is technical work for original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15, not Product Phase 75.

## Gap and safe boundary

`PrivateGuestActivityClassPreflight.resolve` currently verifies classloader ownership, Activity assignability, concrete/public class and inner/static constraints, but does not inspect whether the declared Activity has a public zero-argument constructor. A future framework lifecycle adapter could discover this only after beginning a dispatch. Constructor inspection can safely be done now without executing untrusted constructor code.

## Ordered implementation

1. Commit this plan and EXECUTION_PLAN entry before source changes; verify E74 exact SHA/CI, PR and main baseline.
2. Extend class preflight to require exactly one accessible public zero-argument constructor on the Activity class without calling `newInstance` or invoking any constructor. Reject classes with only parameterized/private/protected constructors and preserve existing host-vs-private loader, Activity subtype, public/concrete and static nested class rules.
3. Add controlled private synthetic DEX/JVM tests for public no-arg, parameterized-only, private no-arg, protected no-arg, abstract, non-Activity, host-owned class and non-static inner class. Confirm constructor side-effect sentinel is unchanged after preflight. If DEX fixture tooling cannot represent a case, document the limitation and cover it with an isolated JVM class test, not a false pass.
4. Add `scripts/audit_e75_activity_constructor_preflight.py` and a pinned exact-SHA E40–E75 workflow based on E74. Require cloud/candidate build/test/lint/APK signing/permission gates and source-engine native AAR/JVM tests. Audit that neither resolver nor probe invokes constructors, creates a guest Intent, attaches a guest Activity, or enables split/provider/GC.
5. Commit source/tests, open stacked Draft PR #49, run CI and inspect actual XML test counts and run conclusion. Mark E75 PASS only when exact code/test SHA is SUCCESS.
6. After code CI PASS, record E75 results and update README/STATUS/EXECUTION_PLAN/PRODUCT_PROGRESS on branch. Report-only SHA must pass its own CI. Keep `main` unchanged.

## Product boundaries

No actual guest Activity construction, ActivityThread/Instrumentation attach, Binder/PackageManager virtualization, independent Android UID/accounts, production split APK provider, Samsung/social-app runtime or release signing. E74 probe is a host Activity displaying a signed synthetic payload View and is not a real guest Activity. Weighted product readiness remains **28.00%**, accepted real third-party clones **0**.
