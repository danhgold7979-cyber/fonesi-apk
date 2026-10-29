# E73 — signed PRIVATE Activity bytecode-class preflight results

Date 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e73-private-activity-bytecode-preflight-20261029` [Draft PR #47](https://github.com/danhgold7979-cyber/fonesi-apk/pull/47), stacked on E72. Original **Product Phase 1 steps 7–8**, preparing Phase 3 step 15. [Pre-code plan](E73_PRIVATE_ACTIVITY_BYTECODE_PREFLIGHT_PLAN.md) was committed first.

## Changes

Added `engine-source/src/main/java/vn/fonesi/engine/PrivateGuestActivityClassPreflight.kt` to resolve signed explicit Activity target names via `Class.forName(target, false, privateLoader)` and verify exact class name, defining classloader ownership (never parent/host), Activity superclass, public concrete class and no unsupported nonstatic inner class. `LoadedGuestCode.preflightExplicitActivityClass` first applies the existing E70–E72 signed explicit component selection and binds it to the loaded private image, including version. It returns type/selection metadata only—**no guest Activity instantiation, lifecycle or launch**. Eight JVM protocol tests cover positive owner/type, signed alias target, wrong name, parent-delegated host class, unrelated/abstract/inner types, missing class and an actual signed PRIVATE manifest without code whose Activity declaration must not become executable.

## CI and provenance

Initial E73 [CI #40916136523](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40916136523) **FAILED** in inherited E70 source audit: strengthening a version check broke the audit's expected contiguous guard text, not the semantic rule. Source formatting was corrected at commit `1297292cf1b3afad71edea9bf24504d15322d35f` to preserve the existing E70 identity assertion **and** added version check; audit was not disabled or relaxed.

**E73 technical PASS** on exact source/test SHA `1297292cf1b3afad71edea9bf24504d15322d35f` [CI #40916252411](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40916252411) **SUCCESS**: checkout SHA, E40–E73 audits, cloud and candidate **510 tests per gate**, build/lint/SDK APK signatures and permissions, 8 source-engine JVM class-preflight tests executed and passed, pinned native/source AAR checks, no split/provider/garbage collection activation. [Sanitized test-only receipt artifact #12915584603](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40916252411/artifacts/12915584603). The reporting commit after this source SHA needs its own exact-HEAD CI before being called verified.

## Limits and next technical path

E74 is a separate opt-in Android synthetic-probe integration planned against E73. No Android guest Activity has been registered, attached or executed by E73. No complete split-capable provider, independent guest OS UID/accounts, physical-phone social app sessions, update retention or release signing accepted. JVM tests and Android AAR builds are **not** device execution. Weighted product readiness **28.00%**, real accepted third-party clone apps **0**. Keep Draft, `main` unchanged.
