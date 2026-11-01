# E25 — private synthetic guest component manifest preflight

Date: 2026-10-28. This technical milestone verifies manifest metadata from a project-owned signed private APK. It does **not** register a guest component with Android.

## Exact green evidence

- **Code SHA:** `d911ee266a92a0c85ec368dbd8df77c0dba6af62`.
- **E25 CI:** [#40670346860](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346860), **SUCCESS**.
- **Same-source regressions:** E21 [#40670347156](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670347156), E22 [#40670346768](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346768), E23 [#40670346702](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346702), and E24 [#40670346892](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346892) all **SUCCESS** on the same SHA.
- **Test-only artifact:** `fonesi-E25-signed-private-component-catalog-TEST-ONLY`, ID `12807339727`, seven-day retention. Receipt confirms `private_component_manifest_verified=true`, `real_guest_activity_registered=false`, `actual_guest_activity_attached=false`, `kernel_uid_isolation=false`, and no third-party components/accounts tested.

## What was checked

1. The project-owned signed vn.fonesi.payload APK is published to the immutable private APK store and read back.
2. PrivateSyntheticComponentCatalog inspects manifest metadata using the private APK path and public PackageManager archive parsing, then checks the exact expected Application/Activity names, export and signature-protection declarations.
3. The E25 proof runs in the dedicated :fonesi_guest_ui process, verifies the signed private View/lifecycle proof and only then emits the UUID-bound receipt. The Android component catalog is a private **preflight record**, not a framework registration.
4. The emulator runner requires exact process/API/UID, lifecycle transcript, private View/resource flags and affirmative catalog verification; it requires explicit false flags for real guest Activity registration, third-party component execution, social accounts and kernel UID isolation.

## Remaining production work

No arbitrary guest Activity/Service/Receiver/Provider launch, ActivityThread registration, guest PackageManager/Intent resolution, Binder/permission mediation, Google Play Services, account isolation, ARM64/Samsung test, session-preserving update or release signing has been proven. E25 is one synthetic manifest/catalog proof. The candidate remains single-APK and does not enable split imports. Product readiness stays around 30%.
