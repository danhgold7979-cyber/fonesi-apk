# E84 — alternative split-provider feasibility and source/license audit results

Date: 2026-10-30. Repository: danhgold7979-cyber/fonesi-apk. Product mapping: Phase 1 steps 5–8.

## Decision

**PASS — source audit complete; no production provider selected.**

No candidate in this review is accepted as a production Fonesi split provider. The audit identifies one possible open-source remediation baseline (ISEKHON/VirtualApp), but it currently fails hard signature-integrity and update-transaction requirements. The maintained official VirtualApp source is commercial and is not available in the public tree under a license that authorizes Fonesi use. TeguFy BlackBox remains single-APK. No third-party source or binary was imported, built or run.

## Candidate findings

### 1. asLody/VirtualApp — public source vs commercial edition

Source: [asLody/VirtualApp](https://github.com/asLody/VirtualApp/tree/ba5abbc2774e9a039764aac07eae18a7fbadcdbc), exact revision ba5abbc2774e9a039764aac07eae18a7fbadcdbc.

The public README states that GitHub code stopped updating in December 2017 and that the maintained commercial source is provided after purchasing authorization. It describes the commercial edition's current split recovery/cache/signature work, Android 17 compatibility and 16 KB fixes, but these updates are not present in the accessible public source tree. The repository tree has no standalone LICENSE file authorizing use of the current commercial implementation.

Decision: not usable as the current provider without the maintained source and an authorized license. No purchase or vendor contact was made.

### 2. ISEKHON/VirtualApp — split-capable code path, rejected in current state

Source: [ISEKHON/VirtualApp](https://github.com/ISEKHON/VirtualApp/tree/b3c634ad7941765df3da84a207aca94b7861afae), exact revision b3c634ad7941765df3da84a207aca94b7861afae. Root LICENSE is Apache-2.0; dependency and native provenance still require a separate audit before any vendoring.

Positive source findings:
- VAppManagerService accepts a directory, parses it through PackageParserEx, copies base.apk and each split APK to an SDK-private package directory, and stores splitCodePaths in PackageSetting.
- PackageParserEx retains splitNames, baseCodePath and splitCodePaths; generated ApplicationInfo receives splitSourceDirs/splitPublicSourceDirs.
- PackageSetting and InstalledAppInfo parcel splitCodePaths, and VEnvironment provides per-virtual-user package data directories.
- PackageInstallerSession stages multiple files and passes the stage directory to VAppManagerService.

Hard blockers:
- PackageParserEx falls back to a fixed FAKE_SIG when collectCertificates fails, including a documented Android 14+ fallback. Its own docs/KNOWN-ISSUES.md acknowledges that this can break apps that verify their signing identity. This violates Fonesi's requirement to preserve and verify original APK signatures.
- VAppManagerService update deletes the live native-library directory and odex before the new base/split files are copied. It then copies files directly into the live package directory. A failure part-way through split copying can leave a partial replacement after old state was already removed. No atomic stage/swap transaction or exact post-copy SHA/length readback is implemented in the inspected path.
- The parser/install session does not itself establish Fonesi's exact cluster digest, full signer continuity and independent private-member readback contract.
- App build uses compileSdk 34/targetSdk 33; library build uses compileSdk 30/targetSdk 22 and NDK 21.4.7075529 with Android 21 native platform. The inspected configuration has no 16 KB page-alignment proof.
- docs/KNOWN-ISSUES.md records Android 14+ Xposed/SandHook incompatibility, Android 15–16 hidden API bypass fragility, host SELinux-context denials and fake-signature fallback. Build configuration also uses older Gradle/AGP and disables lint abort-on-error.
- The repository's app/test directory is a manual sample/test application, not an automated source-level regression suite covering cluster integrity, atomic update and real guest execution.

Decision: potential research baseline only. Do not integrate or enable it until fake-signature fallback is eliminated with a verified original signer path, updates become transactional, every member is hashed/read back, dependency provenance is cleared, native 16 KB/API support is demonstrated, and automated runtime tests pass.

### 3. TeguFy/blackbox-android — single APK

Source: [TeguFy/blackbox-android](https://github.com/TeguFy/blackbox-android/tree/bf1623b7d45157bbc87be46a29352dd6bbbde9fa), exact revision bf1623b7d45157bbc87be46a29352dd6bbbde9fa. Root LICENSE is Apache-2.0.

BPackageManagerService.installPackageAsUserLocked accepts one APK path, calls getPackageArchiveInfo on that file and parses one PackageParser.Package. BPackage persists baseCodePath but not splitNames/splitCodePaths; CopyExecutor copies the baseCodePath only. The source does not expose a complete split cluster import/readback protocol.

Decision: not a split provider.

## Cross-candidate conclusion

- No public candidate passed all signature, private-storage, atomic-update, dependency, ABI/page-size, API compatibility and license/provenance gates.
- The official VirtualApp commercial edition may warrant a future cost/license decision, but Fonesi must not use it without explicit authorization.
- ISEKHON/VirtualApp is the only source reviewed here with a visible base/split copy and metadata path, but its fake-signature and destructive-update behavior are hard blockers, not small polish tasks.
- Standard Android PackageInstaller multi-APK sessions install packages through the host Android PackageManager; that is not a virtual package manager and does not provide Fonesi's multi-instance guest namespace/data contract.

## Result boundary

E84 PASS means the source feasibility decision is complete and documented. It does not mean a provider was selected, built, integrated or run. Candidate supportsSplits remains false; SpaceCore remains single-APK; product readiness remains 28.00%; accepted real third-party clones remain 0.

## Next

E85 pre-code plan: [ManagedGuestEngine typed split-provider orchestration](E85_MANAGED_GUEST_ENGINE_SPLIT_ORCHESTRATION_PLAN.md). This will implement and test the generic engine-side provider contract while retaining fail-closed behavior for all adapters that do not implement and prove it.
