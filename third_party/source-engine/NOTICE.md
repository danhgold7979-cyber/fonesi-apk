# Fonesi source runtime: experimental native component

The Fonesi native routing/JNI/host-test code lives in `engine-source`. It is built separately and is not linked into the current Fonesi app. It does not constitute a complete Android virtualization provider.

The source component also uses Fonesi's shared `apk-runtime` verifier/store and a Kotlin native-library preparer. Android apksig 8.11.1 is a dependency of the shared verifier; its license/notice is recorded in `third_party/apksig/NOTICE.md` and included in the AAR assets as `licenses/apksig.md`.

The component links Dobby built from source, not a third-party archive:

- Source: https://github.com/jmpews/Dobby
- Commit: `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`.
- License: Apache-2.0; original LICENSE is retained in the fetched checkout and packaged in the AAR assets.
- The bundled TINYSTL headers credit Matthew Endsley, Copyright 2012–2018, under the BSD two-clause terms in their source headers. That complete notice is also packaged in the AAR assets.
- Reviewed Fonesi source modifications are recorded in `engine-source/patches/dobby-compat.patch`. The preparation script checks both the base commit and the exact working diff; it rejects other changes.

The patch fixes stale field/header references, a circular include, checked input reads, bounded path copying, ELF ARM64 relocation and honoring the configured logging option. It does not change APK signatures, spoof device identifiers or import the closed SpaceCore native binary.

BlackBox forks, FreeReflection and their demo binaries/keys were read during design but are not dependencies of this native module. No VirtualSpoof/AntiDetection/crash-swallowing/log-sending code from those forks is compiled here. Root privileges and external telemetry are not part of this module.

The routing policy is confined to one configured process. It is not a kernel/UID security sandbox, does not mediate direct syscalls, and is not a guarantee against hostile code sharing the host UID. Android guest lifecycle, Binder/component virtualization, APK cluster loading and real account compatibility remain separate work and device-validation gates.
