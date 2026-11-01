# E70 — signed private explicit Activity selection results

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`; original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15. [Draft PR #44](https://github.com/danhgold7979-cyber/fonesi-apk/pull/44), stacked on E69; [pre-code plan](E70_SIGNED_EXPLICIT_ACTIVITY_SELECTION_PLAN.md) was committed before implementation.

## Code and tests

- Added read-only `SignedPrivateExplicitActivityResolver` for an exact package and fully qualified requested Activity/Activity-alias name from a previously verified cryptographically signed PRIVATE base/feature/config APK component inventory.
- Enforced alias target existence, explicit external exported=true/enabled=true, internal enabled!=false, base Application enabled and permission inheritance, denied protected components without a real Android permission broker, and retained signed split origin. No implicit resolution, original-app fallback, Intent or Android Activity start.
- Extended binary manifest signed inventory with base Application enabled/permission and rejected feature split redefinition. Added real signed APK fixtures and 11 JVM Activity selection tests including Application disabled/protected, alias, feature split and unsafe declaration cases. E70 audit and exact-SHA dual-flavor/source-engine GitHub workflow retain E40–E70 previous safety gates.

## Verified bounded acceptance

**E70 technical PASS** on exact source/test SHA `469b5aa567344154622c099a73351b6ae60494d9`: [GitHub Actions #40910681708](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40910681708) **SUCCESS**. Exact SHA checkout, E40–E70 audits, **503 tests per cloud/candidate gate**, signed fixture JVM tests, build/lint, candidate SDK/APK signatures and permission allowlists, opt-in source-engine native AAR checks and no split/provider/garbage-collection activation passed. [Sanitized test-only artifact #12914911137](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40910681708/artifacts/12914911137). This documentation commit has a distinct SHA and must pass its own CI before treating the report HEAD as verified.

## Critical gaps

The selection is immutable-like read-only metadata only; it never starts a guest Activity, binds an Application, virtualizes Binder/PackageManager or authorizes guest UID/account isolation. Candidate `supportsSplits=false`, `importsSplitApks=false`; real Facebook/Zalo/TikTok sessions, Samsung/non-Samsung device proof and release signing remain unaccepted. **Weighted product score 28.00%; real accepted third-party clone apps 0.** Keep Draft and `main` unchanged.
