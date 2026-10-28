# E24 — Android-hosted View from signed private synthetic DEX and lifecycle relay

Date: 2026-10-28. Implementation follows [E24 pre-code plan](E24_SYNTHETIC_SCREEN_PLAN.md). Branch `phase/e21-android-split-code-proof-20261028` stacks over E21–E23; no separate app, no main-branch change.

## Exact green evidence

- **Code commit:** `66cd3a1d79fdd505b852db15f6b6213f8d0681a5`.
- **E24 CI:** [GitHub Actions #40660505892](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505892), completed **SUCCESS** at 2026-10-28 09:02:45 UTC. Test-only artifact `fonesi-E24-signed-private-synthetic-View-TEST-ONLY`, ID `12804670071`, contains signed debug APKs and sanitized Android receipt; configured retention seven days.
- **Proof log:** “PASS: Android 34 hosted an attached View created by signed private synthetic DEX and relayed CREATE→DESTROY lifecycle. No arbitrary guest Activity, account or UID isolation proved.”
- **Same-source Android regressions:** E21 [#40660505783](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505783) **SUCCESS**; E22 [#40660505960](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505960) **SUCCESS**; E23 [#40660505786](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505786) **SUCCESS**. All workflows use the same E24 code SHA.

## Actual implementation and coverage

1. Added project-owned `SyntheticGuestScreen` class **only inside the signed `vn.fonesi.payload` APK**, requiring a canonical fresh 32-hex challenge and main-thread View creation. The returned `TextView` includes a nonce-bound SHA-256 marker. It accepts the strict callback order `CREATE,START,RESUME,PAUSE,STOP,DESTROY` and has no networking, account, permission or real-app API.
2. Added exported **technical-test-only** `SyntheticGuestUiProbeActivity` in the separate `vn.fonesi.nativeprobe:fonesi_guest_ui` process, without adding services or privileges. Existing exact component audit checks its dedicated process. It independently verifies signed payload identity, publishes/re-audits an immutable private APK snapshot, activates synthetic-only native routing, loads DEX/Resources off the main thread, and refuses host classloader fallback.
3. The *Android-registered host Activity* constructs its screen through the private DEX class, attaches the actual View to the host Window, verifies a real window token, then finishes the host Activity and relays observed lifecycle events to the private synthetic callback. A final UUID-bound private receipt proves matched host and signed-code transcripts, resource verification and APK/native image readback.
4. A new `--proof-level e24` path in the isolated owned AOSP34 emulator runner accepts only SHA-pinned debug probe/payload input and a fresh matching report with exact proof flags, process/API identity and false flags for guest Activity registration, social accounts and kernel UID isolation. Absence, crash or wrong transcript is FAIL.
5. The E24 workflow executed core/APK/source regression, signed debug APK builds, lint, ELF/component/permission audit and actual AOSP34 KVM probe. No production Fonesi app capabilities were changed.

## Not proved / must not be claimed

**Only the HOST Activity was registered with Android.** This is a host proxy + synthetic private View factory and lifecycle relay; it does **not** attach/register Android guest Activity objects, run arbitrary third-party component lifecycle or virtualize guest PackageManager, Service, Provider or Binder. Android processes share a host UID. The test provides no real Facebook/Zalo/TikTok login/notification/Play Services evidence, no Samsung ARM64 execution and no release-signing or safe real-app update acceptance.

The SpaceCore `0.5.0-candidate` remains single-APK guarded with `importsSplitApks=false`. The weighted application-completion estimate remains **about 30%, not an empirical measurement**. Never merge into main, enable real guest accounts or publish the debug artifact as a release on E24 evidence alone.
