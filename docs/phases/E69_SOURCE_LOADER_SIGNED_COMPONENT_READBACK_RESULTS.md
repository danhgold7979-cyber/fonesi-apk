# E69 — source GuestCodeLoader signed PRIVATE component inventory binding results

2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. Original Product **Phase 1 steps 7–8** preparing Phase 3 guest component lifecycle, not Product Phase 69. Branch `phase/e69-source-loader-signed-component-readback-20261029`, [Draft PR #43](https://github.com/danhgold7979-cyber/fonesi-apk/pull/43) stacked on E68 [Draft PR #42](https://github.com/danhgold7979-cyber/fonesi-apk/pull/42). [E69 pre-code plan](E69_SOURCE_LOADER_SIGNED_COMPONENT_READBACK_PLAN.md) committed before source.

## Implemented

- `engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt` now obtains the E68 generic signed `PrivateApkComponentCatalog.inspect` inventory from **the exact PRIVATE cluster** before constructing private DexClassLoader/Resources, and rereads/rechecks it after native/resource source verification. `LoadedGuestCode` exposes this read-only verified inventory alongside its code/resource handle.
- `PrivateGuestComponentPreflight.requireMatches` rejects changed cluster UUID/digest, package/version/signers, missing/extra split names, mismatched signed private split paths or components attributed to unknown signed splits. No original-installed-app metadata fallback, guest Activity registration, native UID or account manipulation.
- Six new source/JVM signed private cluster regressions verify base, base+feature+config, same-package different cluster UUID, mismatched digest/version/signers/split metadata, isolated split graph-only without enabling the production loader, and tampered private APK. All source-engine native/probe restrictions remain.
- Initial E69 CI identified a real minSdk=26 lint violation from an inherited `Application.getProcessName()` call in `IsolatedSplitCodeProbeLoader.kt` (API 28). An explicit `Build.VERSION.SDK_INT < Build.VERSION_CODES.P` rejection was added without disabling lint or suppressing a genuine issue. Static audit now checks the explicit API guard and rejects actual component dispatch, not read-only `getResourcesForApplication`.

## Exact-SHA technical acceptance

**E69 technical PASS** at exact code/test SHA `44d9ca97e092bb8f4c8fb5b7e87d84680d60e73e`: [GitHub Actions #40908213615](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40908213615) **SUCCESS**, verified exact source, full E40–E69 audits, **492 tests in each cloud and candidate gate**, pinned Android SDK/JDK/Gradle build, lint, APK signatures/permissions and disabled production split/provider/GC. E69 also executed six new signed `PrivateGuestComponentPreflightTest` source tests, passed source-built ARM64 AAR, 16-KB ELF alignment, JNI export/dependency/license checks, and actual native host libc routing tests in two isolated subprocesses. [Test-only artifact #12912583203](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40908213615/artifacts/12912583203).

The **documentation/integration commit** after this exact-source acceptance has its own SHA and requires independent CI. E68 code `26fdfbe57981a6bfef9672f4708b529710620ee8` [CI #40904953294](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40904953294) and reporting SHA `f4ae0d7e9164342b2326b0cb72a410a41db3062d` [CI #40906774402](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40906774402) independently succeeded.

## Product blockers

These are Android source AAR build/JVM/native-host/synthetic component-metadata tests, **not a verified real guest Activity/Service/Provider**, UID/account separation, full split provider, Facebook/Zalo/TikTok on Samsung or other phones, or release signing. Candidate `supportsSplits=false`, `importsSplitApks=false` remain. Weighted product readiness **28.00%** per original rubric; accepted real third-party clone apps **0**. Keep Draft, do not merge or release.
