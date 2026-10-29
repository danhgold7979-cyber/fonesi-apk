# E76 — prove signed Activity preflight does not initialize guest class

Date: 2026-10-30. Repository `danhgold7979-cyber/fonesi-apk`; original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15. Branch `phase/e76-activity-preflight-no-class-init-20261030`, [Draft PR #50](https://github.com/danhgold7979-cyber/fonesi-apk/pull/50) stacked on E75 [Draft PR #49](https://github.com/danhgold7979-cyber/fonesi-apk/pull/49). [Pre-code plan](E76_ACTIVITY_PREFLIGHT_NO_CLASS_INIT_PLAN.md) and EXECUTION_PLAN entry were committed before code.

## Implementation and tests

- Kept `Class.forName(selection.targetActivityName, false, privateLoader)` and E75 `candidate.getConstructor()`; neither invokes the guest constructor or class initializer.
- Added two source-engine JVM regressions using separate static-initializer sentinels. Both successful preflight and a failing constructor-shape preflight leave the guest `<clinit>` sentinel unchanged. E75 public no-arg/parameterized/private/protected constructor checks, exact private classloader identity, Activity subtype/public/concrete/static-inner rules, and signed manifest without DEX rejection remain.
- Added `scripts/audit_e76_activity_preflight_no_class_init.py` and pinned E40–E76 exact-source workflow. The workflow asserts both new test names actually appear in test XML; no test was skipped.

## Exact-source technical acceptance

**E76 technical PASS on exact source/test SHA `8abf642a4387b9e80cdc7288c0b84c3b4161a296`**: [GitHub Actions #40929596439](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40929596439) **SUCCESS**, exact source checkout, E40–E76 audits, **510 tests per cloud/candidate gate**, build/lint/APK signature/permissions, source-engine AAR and class-preflight JVM tests. The source-engine `PrivateGuestActivityClassPreflightTest` suite passed with at least ten tests, including both static-initializer cases. [Test-only artifact #12923060854](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40929596439/artifacts/12923060854). E75 source `f6da373ae11806cce727fab0f07433d521a2c594` passed [CI #40927202073](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40927202073), and E75 report SHA `801f68b4e73e91face130b8ee2d8bda91929e5d3` passed [CI #40929241688](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40929241688).

This report-only commit has a newer SHA and requires independent CI. No Android guest Activity was instantiated/attached/launched; this is a JVM class-loading safety proof, not guest lifecycle or account isolation.

## Product blockers

No ActivityThread/Instrumentation attach, guest Context/PackageManager/Binder permission mediation, independent Android UID/account sessions, full split APK provider (`supportsSplits=false`, `importsSplitApks=false`) or real Samsung/social-app phone acceptance. **Weighted product readiness 28.00%, accepted real third-party clones 0.** Keep Draft and `main` unchanged.
