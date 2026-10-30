# Android compatibility inventory — 2026-10-30

## Actual local environment

Java 21 runtime/compiler module and Python/SQLite are available. No Android SDK, adb, sdkmanager, Gradle executable, ANDROID_HOME/ANDROID_SDK_ROOT or local.properties was found in the selected workspace/tool locations. Wrapper 8.13 and pinned distribution checksum remain in the old repository; no wrapper download/execution or SDK license acceptance occurred.

## Official documentation checked

- [AGP 8.11 compatibility](https://developer.android.com/build/releases/agp-8-11-0-release-notes): API36 ceiling, Gradle8.13 and JDK17 baseline. Retaining the existing pins is a candidate build configuration, not an executed rebuild.
- [Java versions in Android builds](https://developer.android.com/build/jdks): build JDK/language version is distinct from Android's available runtime APIs. compileSdk/minSdk and desugaring need separate validation.
- [Java language/API desugaring](https://developer.android.com/studio/write/java8-support) and [supported Java11+ API table](https://developer.android.com/studio/write/java11-default-support-table): only listed APIs/configurations are covered; no blanket Java21 runtime promise.
- [HexFormat API reference](https://developer.android.com/reference/java/util/HexFormat): must verify exact API availability or replace with a portable implementation before older-minSdk integration; do not infer it from desktop Java compilation.

## New-kernel integration risks

Records, switch expressions and Java21 class files require actual D8/R8/toolchain testing. HexFormat, Stream.toList, Map/Set.copyOf, String.isBlank and newer stream/collection helpers require per-API/desugaring review. FileChannel directory fsync, atomic rename, fileKey, NOFOLLOW_LINKS and process/file-lock behavior require Android/Linux filesystem tests; desktop passing tests do not prove OEM behavior. No Android dependency on rebuild-kernel is enabled.

## Next feasible gate

R3.1 uses actual SQLite transactions against synthetic schema1–6 fixtures and a new quarantined catalog contract. This is executable storage proof, not an Android migration or replaced app. Android adapter/build gate remains pending: provision reviewed toolchain without hidden license acceptance, choose an Android-compatible API surface, run lint/D8 plus minSdk execution, then implement and test fresh manager scaffold. Existing app code remains labeled historical until a tested replacement is ready; full replacement remains pending, not abandoned.
