# Hosted Android compile experiment — candidate plan, not executed

Date: 2026-10-30. Requires root coordination after read-only job inventory. No SDK license acceptance, sdkmanager install/update, phone deployment, provider purchase or system-security changes.

## Evidence and candidate versions

Official [Ubuntu24 runner inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md), read 2026-10-30, advertises android-36 rev2, build-tools36.0.0 and JDK17.0.21+9. This mutable inventory is **not evidence of packages on a particular job**. Previous own job [124220642307](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41101996789/job/124220642307) identifies image20261025.353.1; its linked pinned inventory returned404. Read actual SDK source.properties/android.jar/build-tool paths and installed JDK release file in a new diagnostic job before build.

Candidate pinned build: **AGP8.11.1 / Gradle8.13 / installed JDK17 / compileSdk36 / build-tools36.0.0**, minSdk26 retained. [AGP compatibility](https://developer.android.com/build/releases/agp-8-11-0-release-notes) supports API36 with Gradle8.13/JDK17. No use of runner's unrelated default Gradle9.x.

[Official Gradle checksums](https://gradle.org/release-checksums/) match local wrapper JAR `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f` and configured Gradle8.13 ZIP `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`. POSIX wrapper script inspected; local script SHA `734b3879d3501dce471cf0522d3bcbafe76873d9fc5129345b67fb43bd15e933`. Wrapper not executed in this investigation.

## Stage A — read-only own-CI inventory

Only inspect SDK package source.properties, required tool-file presence, installed JDK17 release, wrapper hashes/configuration and runner image identifier. No sdkmanager invocation, repository dependency resolution, Gradle execution, SDK writes or license reads/writes. Missing packages produce NOT_READY, never auto-install. Diagnostic proves inventory only, not compile.

## Stage B — bounded compile after inventory and root approval

Create an independent `rebuild-android/` project, not a copy of old runtime. Native Java Activity/View scaffold using conservative Android APIs, separate debug applicationId `vn.fonesi.rebuild.debug`, no Internet/SDK/clone privileges, backup disabled and unsupported actions visibly disabled. No dependency on Java21 rebuild-kernel or Python storage harness. Reuse only verified wrapper infrastructure, invoking it with `-p rebuild-android`; old root project must not be evaluated.

Use Java17 language/target for new scaffold and only Android platform API calls available at minSdk26. Keep production engine and migration wiring absent. Exact SDK/platform/build-tools preflight must pass. Set **android.builder.sdkDownload=false**, documented by [Android SDK update guidance](https://developer.android.com/studio/intro/update#download-with-gradle). No missing-package fallback, sdkmanager/licenses command or SDK setup action. Gradle ZIP download with pinned checksum and ordinary AGP dependencies from official Google Maven/Maven Central/Gradle registry are the only planned dependency downloads; no arbitrary binaries.

Compile debug + lint, inspect actual manifest/package/permissions/signature/artifact hash with already installed tools. No APK installation or account use. Capability/UI tests must prove unsupported clone actions stay disabled; an APK build is not runtime/clone acceptance. If runner lacks exact components, stop that build with a concrete missing-package report rather than accepting licenses or quietly changing target.

## Replacement boundary

A passing scaffold is an Android compile gate only. It enables a fresh manager implementation path; it does not yet replace working historical management/security/migration oracles. Old-source removal must be a later reviewable commit with tested corresponding replacement and preserved legal/history/probe specs. Full rebuild remains pending and tracked.

## Approval and frozen acceptance — 2026-10-30 18:17 UTC

Root approved the bounded hosted compile experiment, conditional on the same job's actual read-only inventory passing all exact checks. Missing tools must fail BLOCKED before Gradle. Only the official dependencies described above may resolve; no SDK installation/license action. Implement fresh scaffold now, but report compile as unexecuted until hosted evidence exists.

Acceptance: source contract tests enforce isolated applicationId, no permissions, backup off, only a launcher Activity and disabled unsupported actions; installed tool preflight gates Gradle; `assembleDebug lintDebug` succeed; built APK inspection verifies package/minSDK/targetSDK, zero requested permissions, debuggable marker, launcher, disabled-action resources, DEX presence, debug signing certificate and SHA256. Native UI behavior, device compatibility, provider execution and migration remain unaccepted. APK stays within CI review, never deployed/released.

## Independent review before checkpoint

Read-only reviewer found compile-plausible scaffold and appropriate conditional build controls. Corrections: inventory now requires executable tools/JDK; packaged layout checks bind disabled states to exactly four Button elements with distinct compiled IDs (source contract freezes the four semantic IDs); unexpected packaged components rejected; APK zip alignment checked. Synthetic verifier tests explicitly do not represent captured SDK output: literal aapt2 formatting must be confirmed by first hosted build, and any mismatch blocks acceptance. Debug certificate subject is a sanity check, not release signer provenance. UI edge-to-edge/system insets, accessibility and device behavior remain untested. Current CI prints source/inventory/badging/signature/hash evidence but does not upload APK artifacts; hosted workspace APK is transient, not a deliverable or release.
