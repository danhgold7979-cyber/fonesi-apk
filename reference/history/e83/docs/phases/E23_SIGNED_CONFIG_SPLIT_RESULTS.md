# E23 — signed French feature configuration split on actual Android

Date: 2026-10-28. Project source: `danhgold7979-cyber/fonesi-apk`, cumulative branch `phase/e21-android-split-code-proof-20261028`.

## Verified source and CI evidence

- **Exact proved code SHA:** `e9be7c7cc211df80c27d7c811d1463244ab4e4db`.
- **E23:** [GitHub Actions #40658194704](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40658194704), **SUCCESS**, 2026-10-28 08:43:24 UTC. CI built and signature/hash-verified four disposable synthetic APKs, compiled and linted the probe, audited exact components/no new permissions, and actually ran the proof on Android 34 AOSP x86_64/KVM.
- **E22 regression on same SHA:** [#40658194520](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40658194520), **SUCCESS**.
- **E21 regression on same SHA:** [#40658194522](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40658194522), **SUCCESS**.
- **E23 bounded signed test artifact:** `fonesi-E23-TEST-ONLY-alpha-FR-config-split`, artifact ID `12803367060`, 7-day retention; this is *not* a user-facing release APK.
- **Android runtime proof log:** “PASS: Android 34 verified signed private alpha-targeted French config resources and base/alpha/beta DEX parent calls. No third-party accounts tested.”

## What E23 proves

1. Build and cryptographic verification of **four project-owned APK members** with one disposable signing identity and exact SHA-256 allowlist: base; `feature.alpha`; dependent `feature.beta`; `feature.alpha.fr` whose binary manifest identifies `configForSplit="feature.alpha"`. The configuration APK has **no DEX and no native libraries** and carries default/French string resources.
2. A new, separately guarded synthetic-only `ConfigurationSplitProbeLoader` verifies all signed code images and the isolated feature loader graph. The signed config belongs to the alpha node, not the base or beta node. No source paths or code are taken from the installed original after private snapshot publication.
3. A dedicated non-exported guest-named Android service executes the base→alpha→beta signed DEX challenge, including beta invoking alpha via parent classloader; foreign DEX visibility is rejected.
4. An Android `Resources` view built with only signed *private* package paths resolves alpha default English text, has no French override without the config member, and resolves the **French override only when the signed config APK is included**. Unrelated beta resource remains intact. A final private snapshot/native readback verifies unchanged inputs.
5. Fresh owned AOSP Android 34 emulator, `adb install-multiple` and exact 4-member `pm path` proof, UUID-bound report and strict pass flags are required for success; a missing configuration member, a false-positive attempt or a stale receipt fails closed.
6. Original E21 and E22 Android runtime test workflows passed on the same source SHA, confirming no regression to 2-/3-member signed fixtures.

## What is NOT proved

**No functional third-party Android app clone is verified.** No arbitrary split/config set, Play-delivered configuration or dynamic feature, guest Activity/Service/Provider/PackageManager/Binder mediation, Play Services, real multi-account login, kernel UID separation, Samsung ARM64 device execution, session-preserving app updates, performance benchmark, release signing or full software delivery is accepted by this test.

The current SpaceCore candidate remains guarded to a single complete ARM64 APK, API26–34, 4 KB pages; `importsSplitApks=false`. The separate synthetic probe is not shipped as the app's production runtime and must never be used to claim 100% product readiness. The master plan stays **8 product phases / 32 steps**. See [E23 pre-code plan](E23_SIGNED_CONFIG_SPLIT_PLAN.md), [product progress rubric](../PRODUCT_PROGRESS.md), and [main roadmap](../ROADMAP.md).
