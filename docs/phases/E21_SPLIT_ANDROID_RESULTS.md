# E21 — Actual signed isolated base/feature Android execution results

Date: 2026-10-28 (UTC). Follows the [pre-code E21 plan](E21_SPLIT_ANDROID_PLAN.md), the signed [E20 source graph test](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40649699477), and earlier E11–E17 synthetic Android proof.

## Exact green CI and artifact

- **Branch:** `phase/e21-android-split-code-proof-20261028`, based on E20, not yet merged to `main`.
- **Proven source SHA:** `2714148230815b90b57037e2aec0d4fa4a1271bc`.
- **CI:** [GitHub Actions run 40653400751](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40653400751), completed **SUCCESS**, 2026-10-28 08:02:34 UTC. The sole job `signed-isolated-split-proof` passed all required non-cleanup steps; no skipped required assertion was used to make a pass.
- **Uploaded test-only evidence:** `fonesi-E21-TEST-ONLY-isolated-signed-splits` artifact ID `12800284523`, containing bounded signed probe/fixture APKs, fixture hashes, synthetic Android receipts and sanitized crash diagnostics. The workflow limits artifact retention to seven days.
- **CI proof line:** “PASS: Android 34 executed independently loaded private signed base/feature DEX and verified feature resources. No third-party APK components or accounts tested.”

## Verified gates

1. Pinned Android SDK 36 generates independently signed, disposable synthetic APKs of a single package (`vn.fonesi.splitfixture`): a base and a feature `feature.alpha`, each with its own DEX entrypoint and distinct resource marker. Both pass `apksigner` and explicit SHA-256 manifest checks. No real app code, user data or production signing key is used.
2. Fresh **AOSP Android 34 x86_64 with KVM**, and explicitly owned emulator process, accepts `adb install-multiple` and `pm path` reports exactly two installed APK members.
3. The non-exported `:fonesi_guest_split` service verifies package version/signer and copies both members into a cryptographically re-audited **host-private** APK cluster. The root+feature isolated `GuestSplitGraphPlanner` has exact member and parent coverage; missing/altered members fail closed.
4. The test-only loader verifies separate base and feature `DexClassLoader` identities and nonvisibility of the feature class in the base/host. Both execute fresh synthetic SHA-256 challenges. A separate application-info Resources lookup verifies base-only resources do not expose the feature marker and the private base+feature resource set resolves both expected markers. Private APK and native state are checked again after execution.
5. The emulator runner accepts only an exact SHA-pinned four-APK input, UUID-bound JSON report with all signed class/resource flags true, false flags for guest Activity, third-party components, social accounts and UID isolation, and the dedicated named process/Android API identity. Nonzero exits, missing report, wrong run ID, wrong digest or mismatched flags fail.

## Earlier red runs and targeted corrections

- [40651911220](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40651911220): missing `-PincludeSourceEngine=true` on Gradle command.
- [40652195172](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40652195172): Android Lint `NewApi` on unguarded `Application.getProcessName()`, fixed by API-guarded local process identity.
- [40652666814](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40652666814): `aapt2 link` needed `--auto-add-overlay` for synthetic new resources.
- [40653057684](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40653057684): pinned SDK36 `aapt2` rejects unsupported `--feature-of`; feature resources now use its supported explicit `--package-id 0x80`.
- [40653400751](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40653400751): all E21 gated stages **PASS** with unchanged source/Android safety assertions.

## Product exclusions — remain open

This is **synthetic isolated base + exactly one feature**, not a generic third-party isolated split runtime or a completed clone provider. Multiple interdependent features/config splits, dynamically delivered Play Store APKs, ABI/16KB compatibility on physical devices, real guest Application/Activity/Service/Provider, PackageManager and Binder mediation, notifications, accounts/sessions, safe updates, multi-vendor performance and release signing remain **unverified**. The existing SpaceCore `0.5.0-candidate` still has `importsSplitApks=false`; the E21 test loader and signing key are never enabled as a production feature. Multiple guest-named processes under one host UID are **not** kernel-level account sandboxing.

Do not merge this slice into `main` as evidence of a finished product. Review as a distinct technical proof inside the existing 8-phase/32-step master roadmap.
