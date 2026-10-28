# E25 — cryptographically verified private guest component manifest preflight (pre-code)

Date: 2026-10-28. Continue on cumulative branch `phase/e21-android-split-code-proof-20261028`, no extra per-phase branch. Reviewed master 8-phase/32-step roadmap, `docs/PRODUCT_PROGRESS.md` (~30% readiness estimate), E21–E23 signed split graph code, and E24 signed private View/host lifecycle proxy.

## Gap

E24's signed synthetic private DEX can create a real View in an Android registered **host Activity**, but it has not enumerated or resolved guest manifest Activity/Service/Receiver/Provider declarations from immutable **private** APK bytes. Android's `PackageManager` cannot register those private components just because the manifest parses. A production provider must eventually mediate these declarations and safely dispatch Android component lifecycle. E25 adds a strictly test-owned component **preflight catalog** (not a virtual Android registration).

## Implementation requirements

1. Add a `PrivateSyntheticComponentCatalog` in the opt-in source engine with a strict interface reading a `PrivateApkStore.open` audited cluster, using only the private base APK path and public `PackageManager.getPackageArchiveInfo` metadata flags (Activities, Services, Providers, Receivers, Permissions). Fail closed for unexpected package/version/signer/splits, missing or extra components, exported/permission deviations, missing synthetic Application identity or manifest metadata not matching project-owned payload. Do not use `getPackageInfo(packageName)` or `resolveActivity` as evidence for private guest metadata; those would query the **installed original**.
2. Add `e25` to E24's existing dedicated `:fonesi_guest_ui` host Activity. When explicitly requested, run the catalog on the background preparation thread *after* publishing a signed private payload and before rendering. Report `private_component_manifest_verified=true` only after exact class/permission set and full private bytes readback. Always set `real_guest_activity_registered=false`. Preserve E24 mode/default behavior.
3. Extend the bounded emulator runner to accept `e25` using existing SHA-pinned project-owned probe+payload inputs, fresh UUID and AOSP34 KVM emulator. Launch the same test-only host Activity with explicit proof mode and accept only matching, affirmative metadata + View/lifecycle flags and explicit false guest Activity registration/security claims.
4. Add dedicated `e25` GitHub Actions CI for signed payload and host build/lint, core/APK/source tests, exact component/permission/ELF audit, true Android 34 emulator proof and fresh evidence. Preserve E21–E24 regressions.
5. If CI passes, write exact results (SHA/run/artifact, limitations) and update PR. Do not increase product readiness solely because a synthetic metadata catalog passed.

## Explicit exclusions

No arbitrary APK component routing, hidden framework hooks, third-party lifecycle/intent dispatch, guest PackageManager/Binder mediation, Play Services, same-UID secret isolation, social-account test, ARM64 Samsung proof, safe session updates, independent background worker, or final release. SpaceCore production candidate remains single APK with `importsSplitApks=false`. Real guest component registration must be proven separately before product acceptance.
