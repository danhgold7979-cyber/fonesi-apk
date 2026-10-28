# E33 — source acquisition and provenance results

Date: 2026-10-29. This phase rechecked the public source trees and build inputs for the strongest E32 leads. No third-party build scripts were executed; no `moon.jar`, `lib-release.aar`, APK, or native library was loaded into Fonesi.

## Decision

**NO-GO: required source/provenance is still unavailable in the reviewed public trees.** E32's most promising lead, WaxMoon/MultiApp, exposes an API that accepts a directory of APKs for split installation, but the actual engine call is delegated through an opaque `moon.jar`. The ServenScorpion/VirtualApp tree includes an opaque `app/sdk/lib-release.aar` without an established root license or corresponding source/build provenance. No candidate currently meets the minimum legal, source-completeness and security gates.

## Source/provenance checks

### WaxMoon/MultiApp + opensdk

- MultiApp source pin: `3de55af64ec925de390cbf30bb82f279a51945e0`.
- Submodule `opensdk` pin: `eaab1645e57c0be6d3c1b0f5bf586984f4de141f`.
- Both repositories identify AGPL-3.0. The MultiApp README says the repository is no longer maintained since 2023, even though a later repository update changed the APK; the engine source itself was not shown to have been refreshed by that APK update.
- `HackApi.installApkFiles(String apkPathOrDir, int userId, boolean forceInstall)` explicitly documents a directory containing all APK files for a split application. It delegates through `Cmd.INSTANCE().exec(CMD_INSTALL_PACKAGE, ...)`.
- `opensdk` contains `src/main/assets/moon.jar`; the inspected source tree does not contain the complete engine implementation that would let us audit how it parses split dependencies, checks signers, loads resources/native libraries, dispatches guest components, isolates data, or reads the installed cluster back.
- `HackApplication` installs the runtime in `attachBaseContext`, so a future isolated proof would also need a carefully bounded lifecycle/security review. No runtime was executed.
- **Decision:** useful API reference, but not a candidate to integrate until the full engine source, native build inputs, dependency/license inventory, reproducible build instructions and AGPL distribution review are available.

### ServenScorpion/VirtualApp

- Source pin: `23aa5ec82a62c5128006e5c1eaa9ca7fe15b96d8`.
- Tree includes package manager and activity stack sources plus `app/sdk/lib-release.aar`.
- The pinned repository metadata did not report an SPDX license, and no root `LICENSE` file was found at the inspected commit. The AAR's source, license and build provenance remain unknown.
- Repository activity around a README update does not prove that the bundled engine AAR was rebuilt or that it supports current Android versions, complete split clusters or 16 KB pages.
- **Decision:** no import without a full source/license/provenance package and independently reproducible build.

### Additional repository discovery screen

- `BlackBoxing/BlackBox` pin `a0a330732c51dd9476c618ede31ec7d4a168a5d5`: broad source tree and Apache-2.0 root license, but the last source commit is 2022-11-19. Existing audit finds base-centric cluster/resource paths, risky fallback metadata, crash/log/spoof paths and native prebuilt provenance gaps.
- `black-binary/BlackBox` pin `34c2622c4b2ce3ba77edfce0396f2e5b8698cb72`: repository metadata showed last source push in 2021; its recent `updated_at` field is not evidence of recent source maintenance. No new code evidence resolved the existing base/split and native-provenance blockers.
- `xxxyanchenxxx/VirtualAppEx` pin `70bf11c632b2a59f44f61b377ab25a23e71778bc`: source commit dates to 2018 and is described as Android 9 adaptation; not current enough to establish target SDK 36, current guest lifecycle or account isolation.
- `CrackerCat/Virtualapp11` pin `5547052902a1b50dabff9221155872b04bd3efb4`: last source commit dates to 2021; no current Android/platform or security acceptance evidence.
- `codehz/container` pin `a348ace3ec591dac4468dec5249e6d6b1b033fce`: GPL-3.0 and last source commit in 2017; explicitly uses the VirtualApp framework and is not an independently maintained modern provider.
- These metadata/source checks are enough to reject them for immediate integration; they are not a claim that no future release or private licensed source exists.

## E33 outcome

The public repositories reviewed do not provide both a complete, auditable engine source tree and a proven route to the required cluster/component/isolation contracts. We have not contacted maintainers or made requests on the user's behalf. To revisit WaxMoon or ServenScorpion, the minimum new evidence is a source release for the opaque engine artifact, native/dependency provenance and licenses, reproducible build recipe, and explicit license/distribution approval.

## Next phase

E34 — first-party engine architecture plan. Define the smallest maintainable provider boundary and its testable contracts without pretending the engine already exists. Compare a clean-room implementation cost against a properly licensed provider. Keep production clone features disabled until a real provider passes owned-fixture tests for cluster import, guest lifecycle, framework mediation and account isolation.

## Product completion

The product score remains **28.00% weighted / ~30% rounded estimate**. E33 is a source/provenance gate, not shipped functionality. Accepted real third-party clone applications: **0**. The original 8 product phases / 32 steps remain incomplete.
