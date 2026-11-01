# E22 — Signed synthetic dependent feature code and resources on actual Android

Date: 2026-10-28. Based on the [pre-code E22 plan](E22_DEPENDENT_ANDROID_SPLITS_PLAN.md), the original [E21 Android proof](E21_SPLIT_ANDROID_RESULTS.md), and the [8-phase / 32-step roadmap](../ROADMAP.md).

## Exact CI receipts

- **Code SHA:** `1ec2b4d0271da1b5a9a77c00112b88086fe81a16` on cumulative `phase/e21-android-split-code-proof-20261028` branch. No new per-phase branch; `main` remains `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`.
- **E22 Actions:** [#40655822440](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40655822440), ended **success** at 2026-10-28 08:25:02 UTC. One job `android-dependent-feature` completed all required test/build/audit/emulator checks.
- **E21 regression on the same source SHA:** [#40655822472](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40655822472) completed **success**, retaining the original signed two-APK isolated split test.
- **Uploaded E22 test-only evidence:** `fonesi-E22-TEST-ONLY-dependent-signed-splits`, artifact ID `12802290075`, contains signed disposable APKs, SHA-256 fixture manifest and sanitized emulator receipt/log files, retained by workflow for seven days. This is **not** a release APK.
- **Exact Android proof:** “PASS: Android 34 executed signed private base/alpha/beta classloader parent DEX and verified staged beta resources. No third-party guest components/accounts.”

## Proof gates actually exercised

1. Pinned SDK36 `aapt2`, `d8`, JDK21 `javac`, and `apksigner` built a disposable 3-member `vn.fonesi.splitfixture` package: base, `feature.alpha`, and `feature.beta`. Beta's signed binary manifest declares `<uses-split android:name="feature.alpha"/>`, has its own DEX and resource ID `0x81`, and shares the ephemeral signer with base/alpha. Exact hashes were checked separately for each member.
2. Existing APK auditor/private store cryptographically reverified the installed source and published a host-private immutable cluster. The isolated graph planner required an exact **base → alpha → beta** parent DAG with three unique APK members. No flattened code loader, installed-app fallback, missing member or unsanctioned extra split was accepted.
3. On an owned, clean **AOSP Android 34 x86_64** emulator, `adb install-multiple` succeeded and the runner demanded exactly three `pm path` members. A non-exported service in `:fonesi_guest_split` performed the test, not a third-party guest app.
4. Dedicated base, alpha and beta `DexClassLoader` instances were linked parent-to-child. Classes in the wrong namespace were absent. **The actual beta challenge method called the alpha method from the parent loader** and the signed nonce-derived return value was compared exactly.
5. Using an `ApplicationInfo` pointing only at **private** APK paths, Resources were checked for base-only, base+alpha and base+alpha+beta views. Beta marker was absent from the earlier views and present with the correct value in the final view. Native routing and signed APK readback were checked after execution.
6. The runner required a new UUID-bound receipt, fixed three APK SHA-256 inputs, exact process/API/UID and dependent DEX/resource assertion flags. It required explicit `false` for third-party guest component execution, social account tests and kernel-level UID isolation. Any missing/mismatching/failing stage would fail CI.
7. Core/APK tests, source split graph/JVM regression, native probe build+lint and strict component/permission/ELF audit also succeeded. No production SpaceCore capability was modified.

## Limits and next gates

This demonstrates **one synthetic linear dependency chain only**. It does **not** prove real config split/resource classloader combinations, dynamic Play Feature Delivery, arbitrary package code/manifest resolution, Android guest Activity/Service/Provider/Binder mediation, notification/Play Services behavior, safe individual account storage, ARM64 Samsung compatibility, persistent user sessions across app updates, battery/performance targets or a signed release.

The app-facing SpaceCore `0.5.0-candidate` still supports only single complete ARM64 APKs on guarded API26–34/4 KB and retains `importsSplitApks=false`. Same-UID processes are not an Android kernel sandbox. Real user social accounts have never been used in this technical test. Do not merge or publish this as a complete multi-clone product.

**Next roadmap work** requires a separate checked plan: signed configuration split/resource behavior on Android and progressively verifiable component lifecycle/PackageManager/Binder compatibility before any production feature enablement.
