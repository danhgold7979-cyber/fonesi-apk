# E81 — signed PRIVATE synthetic Activity scoped instance-method proof (technical results)

Date: 2026-10-30. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Branch `phase/e81-private-activity-scoped-method-proof-20261030`, stacked on E80 [Draft PR #54](https://github.com/danhgold7979-cyber/fonesi-apk/pull/54); E81 [Draft PR #55](https://github.com/danhgold7979-cyber/fonesi-apk/pull/55). Original **Product Phase 1 steps 7–8** (test-owned controlled app portion of step 8), preparing Phase 3 step 15; E81 is a technical milestone, NOT a new product Phase. [Pre-code plan](E81_PRIVATE_ACTIVITY_SCOPED_METHOD_PLAN.md) was committed before implementation.

## Verified exact-source implementation

- Code/test source `35ebb2119fb771762865ec02a6d9a9a38b28f4dc`: [commit](https://github.com/danhgold7979-cyber/fonesi-apk/commit/35ebb2119fb771762865ec02a6d9a9a38b28f4dc), [GitHub Actions #41024151911](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41024151911) **SUCCESS**, all **17 substantive workflow steps**.
- **510 cloud + 510 candidate tests per gate** with pinned build/lint/APK signing/permission audit; **63 source-engine JVM tests**, **33 Python emulator/receipt/fingerprint tests**, **14 test-only payload JVM tests**, E40–E81 audits and owned AOSP API 34 x86_64 signed host/payload execution. Required suites have **0 failures, errors, or skips**.
- Exact source/APK-bound Android receipt is retained in [TEST-ONLY Actions artifact #12963223216](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41024151911/artifacts/12963223216). APKs are signed synthetic test fixtures, **not** a production release or independently isolated Android guest.
- A digits-only UUID in the first E81 JVM test fixture made its `toUpperCase()` negative mutation identical to a valid UUID. Before changing code, the issue and narrow fix were recorded in the E81 plan at `127b3c983cdafe5984ee46db33acad7dc190d587`; commit `35ebb2119fb771762865ec02a6d9a9a38b28f4dc` uses a lowercase-hex UUID fixture. All validation rules and fail-closed assertions remain enforced.

## Exact owned AOSP API 34 emulator receipt

| Observation | Write stage | Verify stage after force-stop |
| --- | --- | --- |
| Host process PID | 2620 | 3046 |
| Host Android UID | 10142 | 10142 (same UID) |
| E80 signed PRIVATE `PayloadActivity` Java object | Constructor once | Constructor once |
| Explicit E81 `e81_scoped_mode=enabled` | true | true |
| Bound synthetic Activity instance scope | A only | The same A UUID |
| Reject duplicate A and cross-scope B binding | true | true |
| Signed PRIVATE instance method reads A from SharedPreferences/file/SQLite | true | true |
| Synthetic Application A/B persisted `onCreate` start counters | 1 / 1 | 2 / 2 |
| Activity Android framework `getBaseContext()` | null | null |
| Framework guest Activity attach / guest Activity `onCreate` | false / false | false / false |

The runner confirmed `am force-stop` of the project-owned host, **absence of the prior dedicated process**, and restart with a new PID and new per-process token. Both receipt stages retained the exact same verified PRIVATE APK cluster ID, distinct immutable A/B UUID scope IDs and marker values. Stage receipt `source_commit` matched `35ebb2119fb771762865ec02a6d9a9a38b28f4dc`; signed **host APK SHA-256** `aa7b2ad9e0614ebf59c27313902c915cdc2a808f34d955fbc2162e67c65c6de4`; signed **payload APK SHA-256** `e084f765e4817784f34e5b9c21faad84ceaff585aa33d37d0b2695b745d85e6d` matched in both stages.

The test-only signed PRIVATE `PayloadActivity` instance was constructed with public `Instrumentation.newActivity`, then its **explicit test-owned instance method** was bound once to the already-verified `SyntheticGuestScopedContext` for A. Reflection verified the method was declared by the exact PRIVATE Activity class. Rebinding to A or B was rejected, A binding remained unchanged and its instance method read the original A marker, never B. The Android Activity itself remained **unattached**; its UI `onCreate` never ran.

## Acceptance boundary, product progress and next work

This is **same-UID synthetic Activity instance-method delegation**, not Android ActivityThread/LoadedApk/PackageManager registration, true guest Activity component lifecycle, guest Context/permission/Binder mediation, independent OS UID/accounts, or execution of a third-party app. `RuntimeBindings.supportsSplits=false` and the candidate `SpaceCoreBackend` single-full-APK guard are unchanged. No Facebook/Zalo/TikTok or physical Samsung session was tested; no production clone was accepted.

**Product Phase 1 step 8 is still not accepted. Weighted product completion stays 28.00% under unchanged [PRODUCT_PROGRESS](../PRODUCT_PROGRESS.md) rubric; accepted real third-party clone applications = 0.** Keep Draft PR #55, do not merge to `main` or release for a synthetic result. This **documentation/results commit is a new SHA**; it must independently pass exact-source CI before treating the reporting HEAD as CI-verified. Next work should address the real Android guest component framework/permission/IPC lifecycle gap rather than misclassify an unattached Activity method as a running clone.
