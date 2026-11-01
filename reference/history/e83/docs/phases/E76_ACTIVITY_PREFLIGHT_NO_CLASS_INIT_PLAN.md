# E76 — prove signed Activity preflight never triggers class initialization (pre-code plan)

Date: 2026-10-30. Exact repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e76-activity-preflight-no-class-init-20261030` from E75 reporting HEAD `801f68b4e73e91face130b8ee2d8bda91929e5d3`, [Draft PR #49](https://github.com/danhgold7979-cyber/fonesi-apk/pull/49). E75 source/test SHA `f6da373ae11806cce727fab0f07433d521a2c594` passed [CI #40927202073](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40927202073) **SUCCESS** (510 tests per cloud/candidate gate, source-engine AAR/JVM suite). E75 reporting HEAD `801f68b4e73e91face130b8ee2d8bda91929e5d3` CI [#40929241688](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40929241688) is still in progress at planning; do not claim report-head PASS yet. Original **Product Phase 1 steps 7–8**, preparing Phase 3 step 15. E76 is technical, not a new product Phase.

## Gap

`PrivateGuestActivityClassPreflight.resolve` correctly calls `Class.forName(name, false, privateLoader)`, and E75 calls reflection `getConstructor()` without invoking it. Tests prove constructor side effects do not occur, but do not prove a guest class static initializer remains unexecuted. Reflection/class metadata must remain a non-executing preflight boundary: a signed APK can still contain arbitrary code, and signature validity does not make initialization safe.

## Ordered implementation

1. Commit this plan and EXECUTION_PLAN entry before source changes. Verify E75 source and report SHA/CI separately.
2. Add a dedicated static-initializer sentinel class whose `<clinit>` increments a test-owned counter. Obtain the class via `Class.forName(name, false, loader)` and run the full E75 `requireMatches` path; assert the sentinel remains untouched. Use a separate non-guest sentinel class so reading the counter does not initialize the guest class.
3. Add a failure-path test that resolves a missing public no-arg constructor on a class with a static initializer; assert both the error and zero initializer calls. Preserve the no-constructor-invocation test, signed manifest no-DEX rejection, exact classloader ownership, Activity type, public/concrete and constructor checks.
4. Add `scripts/audit_e76_activity_preflight_no_class_init.py` and a pinned E40–E76 exact-source workflow derived from E75; verify E70–E76 audits, cloud/candidate 510-test floors, E75+E76 source-engine suite execution and no guest Activity instantiation/attach, split/provider/GC activation.
5. Commit, open stacked Draft PR #50, run CI and inspect exact SHA/test receipts. PASS only on successful exact-source CI. Then update result/STATUS/EXECUTION_PLAN/PRODUCT_PROGRESS; report SHA requires separate CI.
6. Keep `main` unchanged and do not label a JVM/static initializer test as Android guest runtime proof.

## Exclusions

No guest `ActivityThread`/`Instrumentation` lifecycle, Binder/PackageManager virtualization, independent Android UID/account, full split provider or Samsung/social-app device acceptance. `supportsSplits=false`, `importsSplitApks=false`. Product weighted readiness remains **28.00%**, accepted real third-party clone apps **0**.
