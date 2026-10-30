# E82 — investigate public AppComponentFactory host-stub launch of signed PRIVATE test Activity (pre-code)

Date: 2026-10-30. Repository `danhgold7979-cyber/fonesi-apk`.
**Product mapping: original Phase 1 steps 7–8 (controlled synthetic-app integration), preparing Phase 3 step 15.** E82 is a technical milestone, NOT a new Product Phase.

## Baseline / exact proofs

- E81 implementation `35ebb2119fb771762865ec02a6d9a9a38b28f4dc` [Actions #41024151911](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41024151911) **SUCCESS**: 510 cloud/candidate tests per gate, 63 source-engine JVM, 33 Python and 14 synthetic payload JVM; owned AOSP API34 x86_64 emulator PID 2620→3046, same UID 10142, preserved two synthetic scoped data stores, exact PRIVATE `PayloadActivity` Java instance method bound only to scope A; **Activity remained framework-unattached**.
- E81 reporting/docs `af9c51edd9d103fad6049db06fd0554ffb2f660e` has independent [Actions #41026248376](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41026248376) **in progress at plan time**. Do not treat reporting SHA as verified until it completes. [Draft PR #55](https://github.com/danhgold7979-cyber/fonesi-apk/pull/55) remains open.
- Android public API reference: [AppComponentFactory.instantiateActivity](https://developer.android.com/reference/android/app/AppComponentFactory#instantiateActivity(java.lang.ClassLoader,java.lang.String,android.content.Intent)) exists since API 28 and is a **component-instantiation hook**, **not** an Activity lifecycle attach API. The platform alone attaches returned instances after creation. [AOSP Instrumentation.newActivity](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/Instrumentation.java) obtains the process package's registered factory when the framework supplies a real ActivityThread; E80's manually constructed `new Instrumentation()` did not exercise that route. [AOSP ActivityThread](https://android.googlesource.com/platform/frameworks/base/+/d630f105e8bc0021541aacb4dc6498a49048ecea/core/java/android/app/ActivityThread.java) shows the distinct framework creation/attachment phases. This is a candidate **host-owned proxy**, not proof that a guest package is registered.
- Existing test host `native-runtime-probe` has no factory and a strict [manifest component audit](../../scripts/run_native_probe.py) that currently enumerates five exported host Activity classes. Adding any internal stub requires a **precise audit update**, not disabling the allowlist. Test-owned `probe-payload` Activity has synthetic local preferences and files and no account secrets.
- Production app candidate `RuntimeBindings.supportsSplits=false`, `SpaceCoreBackend` accepts one full APK; `main` remains `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`; weighted product readiness **28.00%**, accepted real third-party clones **0**.

## Goal

Find whether the **Android framework** can legitimately create and attach a **project-owned, signed PRIVATE** synthetic Activity Java instance through a **registered host-app, non-exported Activity stub** using the **public** `AppComponentFactory.instantiateActivity` hook on the owned AOSP API34 emulator. Treat any success strictly as a **host-stub/host-UID/context** Activity lifecycle; the host package's component identity for this launch is the non-exported stub. The signed `vn.fonesi.payload` APK is installed separately as a test-source fixture, so PackageManager may list that fixture package and its own manifest; do not claim the fixture package is globally absent. Test both positive lifecycle and negative eligibility before concluding feasibility.

## Ordered implementation and required gates

1. First inspect the existing manifest, host Activity lifecycle and native APK audits. Add an **opt-in E82-only** non-exported `vn.fonesi.nativeprobe` Activity stub in the dedicated `:fonesi_guest_ui` process. Add a host `android:appComponentFactory` class. For every ordinary host component, the factory must delegate exactly to the Android default factory, with no modification of E79–E81 behavior. On API below 28, E82 mode is unavailable; do not claim production support.
2. Create a **test-only one-shot authorization object** in the host process: exact signed PRIVATE class name `vn.fonesi.payload.PayloadActivity`, signed cluster ID, immutable source and APK SHA-256, run UUID, nonce, expected host-stub ComponentName, and privately held `LoadedGuestCode` handle. Arm only *after* `PrivateApkStore.open`, signer and classloader preflight, native ready and exact host-stub dispatch. Reject any second arm, stale/missing/replayed nonce, untrusted/external component, different process/package/loader or unexpected class; never fallback to original app or run a guest from arbitrary Intent extras.
3. The factory may return the test-owned signed PRIVATE `PayloadActivity` instance **only** for the exact host stub + one valid ticket; require the returned Java type/classloader equals the preflight signed PRIVATE class, not host loader. Default paths must never touch the signed payload. Fail closed on unsupported ABI/API, missing cluster, inconsistent APK, or same-process re-entry.
4. Let the **Android framework**, not a manual `Activity.attach` or `Instrumentation.callActivityOnCreate`, complete the host-stub Activity launch. Add a distinct E82 test-only receipt from signed PRIVATE `PayloadActivity.onCreate/onResume`: prove Android supplied non-null base Context/Window and a lifecycle callback, but prominently record `getPackageName()==vn.fonesi.nativeprobe`, same host UID, host package `ActivityInfo`, and `guest_package_registered_with_package_manager=false`. Do NOT route any external/third-party APK or credential.
5. Build a separate strict E82 Python receipt runner and AOSP emulator test. Require a fresh exact-source/APK-bound record, host factory ticket identity/one-shot, explicit signed PRIVATE class and PackageManager host stub, real framework callback, and negative production/UID/account/split claims. Retain E81 A/B process and storage checks where technically applicable; **if incompatible with factory creation**, explicitly separate this host-stub experiment rather than weakening E81 assertions.
6. Add JVM ticket identity/nonce/replay/cross-component/state tests, strict manifest+permission audit and Python false-claim tests. Update `scripts/run_native_probe.py` allowlist to enumerate exactly the **new non-exported internal stub**, while preserving the prior five exported Activities and all process/permission constraints. Do not ignore lint or change signing/target SDK to make test pass.
7. Add a dedicated exact-source E82 GitHub Actions workflow running E40–E82 audits, E81 Python, cloud/candidate/test build/lint, source-engine AAR/JVM and **actual owned AOSP API34** host-stub lifecycle receipt. If factory path is unsupported or fails after bounded experiments, **record the blocker**, and keep E82 NOT PASS rather than spoofing framework callbacks or switching to hidden APIs.
8. Only after every required gate succeeds on the exact source/test SHA, write results and update STATUS/README/PRODUCT_PROGRESS/EXECUTION_PLAN; documentation HEAD needs independently verified CI. Keep PR Draft and `main` unchanged.

## Critical non-acceptance

Even an actual framework `onCreate` of the test-owned signed PRIVATE Java Activity instantiated via **host AppComponentFactory** does **not** register the guest package or supply a guest PackageManager/Context/permission model, dedicated OS UID/account, service/provider/Binder mediation, production split APK, real third-party application execution, or Samsung acceptance. Do not call a host-owned stub a production guest Activity or report a clone completed. Product rubric remains **28.00%**, 0 accepted third-party clones. Never turn E82 opt-in on for cloud/candidate product builds.

## E82 implementation refinement — before code (30/10/2026)

This refinement is committed before any E82 source modification. It narrows the experiment to one host-owned Activity instance created by Android through the registered factory. It does not attempt to register a guest package or emulate a guest UID.

### Exact source changes

1. native-runtime-probe/src/main/AndroidManifest.xml
   - Declare .E82HostActivityStub as the single new non-exported Activity, in :fonesi_guest_ui, with no intent filter.
   - Set android:appComponentFactory to the host factory class.
   - Preserve all existing five exported Activities and five internal services exactly; add no permissions or exported entry points.
2. Add E82AppComponentFactory and E82FactoryAuthorization under the native probe package.
   - Delegate every non-E82 component to super.instantiateActivity(...); default paths must not inspect or load the payload.
   - Accept only the exact stub class name and ComponentName, process, host UID/package, run UUID, nonce, source SHA and APK SHA values matching a process-local armed authorization.
   - Authorization retains the exact LoadedGuestCode object and preflighted Activity Class in memory; neither the classloader nor authorization is reconstructed from Intent extras.
   - Consume authorization atomically before construction. Replay, duplicate arm, stale nonce, different process/UID/package, wrong stub, wrong signed class/loader or failed construction is terminal and fail-closed.
   - Instantiate only the already-preflighted public no-argument constructor. Do not call hidden APIs, Activity.attach, Instrumentation.callActivityOnCreate, framework reflection or original-app fallback.
3. Add an opt-in E82 preparation/dispatch path to SyntheticGuestUiProbeActivity.
   - Require API 28+, x86_64 test ABI, exact E82 proof mode, fresh run UUID and exact source/host APK/payload APK SHA-256 values.
   - Publish/open the test-owned signed PRIVATE APK with PrivateApkStore, verify signer/cluster readback, initialize the native test runtime, load one LoadedGuestCode and preflight vn.fonesi.payload.PayloadActivity through the existing trusted-internal resolver.
   - Arm the process-local authorization only after these checks, then launch the exact non-exported host stub explicitly with matching one-shot nonce and run identity. The original Activity polls for a receipt after the stub Activity finishes.
4. Add an E82-only branch to signed test payload PayloadActivity.
   - In onCreate, call super first, then require the expected E82 Intent identity and a real non-null framework base Context and Window.
   - Read PackageManager metadata for the exact registered host stub and record host package, host UID and host ActivityInfo class/process; prove the host package's launch component is only the non-exported stub. The separately installed signed payload fixture may appear in PackageManager and is not evidence of guest execution.
   - In onResume, record a separate lifecycle marker and finish the synthetic Activity; do not render the legacy interactive form or accept external APK/account data in E82 mode.
5. Extend scripts/run_guest_code_emulator.py and its Python tests with a separate E82 runner/receipt validator.
   - The receipt must bind the exact source SHA, both signed APK SHA-256 values, run UUID, nonce, private cluster, exact signed class and host stub ComponentName.
   - Require factory authorization armed/consumed once, framework onCreate and onResume, non-null base Context/Window, exact host PackageManager ActivityInfo, and same host package/UID/process. Record the signed payload APK as an installed source fixture.
   - Explicitly require guest component registration inside the host package, guest UID isolation, third-party execution, real social accounts, production clone acceptance and physical-device acceptance to remain false. Do not assert that the separately installed source-fixture package is globally absent from PackageManager.
   - Add negative tests for missing/replayed nonce, wrong component/class/loader/process, duplicate consume, wrong source/APK hashes, missing lifecycle, null Context/Window and every forbidden overclaim.
6. Extend scripts/run_native_probe.py with an exact allowlist entry for the new non-exported stub and exact appComponentFactory value. Keep the existing five exported Activity assertions, five internal service assertions, zero-permission policy, ABI/ELF checks and backup/network restrictions.
7. Add a dedicated E82 exact-source workflow based on E81. It must run the inherited E40–E81 audits/tests, cloud and candidate checks, source-engine AAR/JVM checks, synthetic host/payload build/lint/manifest audit, then boot the owned AOSP API 34 x86_64 emulator and run the E82 receipt. No audit-only fallback can satisfy the Android receipt.

### Acceptance and stop conditions

- First prove ordinary host Activity construction delegates unchanged to the default factory; prove the exact stub is non-exported and isolated to :fonesi_guest_ui.
- The positive path passes only if the Android framework invokes the factory for the registered stub and subsequently invokes the signed PRIVATE Activity's real onCreate and onResume with non-null host Context and Window.
- If Android rejects a foreign-loader Activity, factory selection is skipped, framework attachment fails, or the exact receipt cannot be collected, preserve the failure evidence and mark E82 BLOCKED/NOT PASS. Do not replace the framework path with manual attachment or synthetic callback calls.
- The payload class must remain a project-owned synthetic fixture. The Android component remains registered as vn.fonesi.nativeprobe.E82HostActivityStub under the host package/UID/Context; the host package does not register the private payload Activity as a host component. The signed payload fixture remains separately installed for source acquisition; its own package entry is not a guest-runtime receipt.
- No production candidate binding, split capability change, main merge, release APK, account test or product-readiness increase is authorized by this experiment.

### Verification sequence

1. Run the new pure-JVM authorization state tests and Python receipt/negative-claim tests.
2. Run the strict manifest/APK audit and all inherited E40–E81 source audits.
3. Run cloud and candidate test/build/lint/signature/permission checks.
4. Build the opt-in signed synthetic host and payload APKs; verify the exact stub/factory manifest and no new permissions.
5. Execute the actual AOSP API 34 x86_64 host-stub launch; retain the source/APK-bound raw receipt and logs as TEST-ONLY artifact.
6. Re-run the full E82 workflow on the exact source SHA. Only then may an E82 results document be written and STATUS/README/PRODUCT_PROGRESS updated.

### Branch and progress controls

- Continue on phase/e82-public-component-factory-feasibility-20261030, based on the E81 reporting SHA; keep the new PR Draft and stack it on the existing E81 Draft PR.
- Keep main at c055df600f87ee9ddf0cd5d6bfc67d80d145e79d.
- Product mapping remains Product Phase 1 steps 7–8, preparing Phase 3 step 15; E82 is not a product Phase.
- Weighted readiness remains 28.00%, accepted real third-party clones remain 0. No E82 technical PASS until every required gate and the actual emulator receipt pass on one exact source SHA.