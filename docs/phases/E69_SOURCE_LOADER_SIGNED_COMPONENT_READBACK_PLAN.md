# E69 — bind private signed component inventory into opt-in source GuestCodeLoader (pre-code)

Date 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`, branch `phase/e69-source-loader-signed-component-readback-20261029` from E68 interim source HEAD `26fdfbe57981a6bfef9672f4708b529710620ee8`, [E68 Draft PR #42](https://github.com/danhgold7979-cyber/fonesi-apk/pull/42). E68 exact-source CI [#40904953294](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40904953294) was **in progress** at planning; do not mark E68 PASS or assume compile/test results. E67 source and report commits independently passed [CI #40900943479](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40900943479) and [#40902513320](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40902513320). Original **Product Phase 1 steps 7–8** (source-built engine, guest code metadata), with Phase 3 step 14 dependency; E69 is NOT a product phase.

## Verified gap and proposed bounded bridge

E68 introduces a signed binary-manifest `PrivateApkComponentCatalog` in APK-runtime, but the opt-in Android `GuestCodeLoader.load` currently returns only package/version/DexClassLoader/Resources. Its caller cannot verify that loaded code image and its manifest component inventory refer to the **same** private cryptographically signed cluster. A future guest component dispatcher cannot safely derive the manifest from the original installed app or hardcoded synthetic catalog. The source-built engine must bind exact signed manifest metadata to the code/resources handle before any attempt to run guest components.

## Ordered implementation and proof

1. Commit this plan and EXECUTION_PLAN entry before code. Confirm source/PR/CI and stable Room/SDK/split guards; no enabling production provider.
2. Add a read-only signed `componentInventory` to `LoadedGuestCode` and have `GuestCodeLoader.load` obtain `PrivateApkComponentCatalog.inspect(apks, clusterId, api)` from PRIVATE bytes. Validate cluster ID/digest/package/version/signers and exact signed base/split path identities against the already verified `GuestCodeLayout` before loading Dex/Resources. Re-read inventory and compare at the end alongside existing `apks.open` and native verification. Preserve Android/native dedicated-process/one-attempt guards and isolatedSplits block.
3. Extract a small JVM-testable `PrivateGuestComponentPreflight` object for matching `GuestCodeLayout` to `VerifiedPrivateComponentInventory` with cluster identity, digest, package/version/signers and full split names. It does **not** call a component, attach an Application or invent an Android guest UID. Tests with signed E68 base/feature/config fixtures, tampered metadata/digest/split list, different same-package cluster ID, and isolated split graph-only with explicit no-execution claim.
4. Pinned E69 exact-HEAD CI includes E40–E69 audit, cloud/candidate existing suite, signed fixture parser regressions, opt-in `engine-source:testDebugUnitTest` plus Android source AAR build/lint with pinned audited native source. Existing Cloud/Candidate `supportsSplits=false`/`importsSplitApks=false` and no provider/GC activation gates remain. No emulator or Samsung runtime proof can be declared from JVM/build-only CI.
5. Only after exact source SHA CI SUCCESS, record E69 technical PASS and update STATUS/PRODUCT_PROGRESS/results; later reporting SHA requires own CI. If E68 parent fails, fix it and integrate before claiming E69.
6. Keep PR Draft stacked on E68; do not merge `main`, enable split import, advertise support or ship APK as production clone engine.

## Product blockers

Still no real third-party Activity/Service/Provider framework registration, independent Android UID/accounts, full split-capable provider activation, actual Facebook/Zalo/TikTok sessions, Samsung/non-Samsung phone tests or release signing. Weighted product estimate stays **28.00%**, **0** accepted real third-party clone apps.
