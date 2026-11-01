# Current local maintenance candidate — 2026-11-01

Gradle **8.14.6** replaces the historical8.13 pin below; AGP8.13.2/JDK17/min26/compile36/target36/BT35.0.0 remain unchanged. Independent review and exact-source Android compilation of this batch are pending. No SDK installation, license acceptance or public push.

The official release checksum page matches distribution `7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24` and wrapper JAR `ad16bca46cb71bb5887c546860d93992aeee35c1fdaf4a242b0a612375d937e0`. Scripts were generated offline from that verified distribution in a separate empty project, not copied from old implementation. The GitHub v8.14.6 repo tag itself still uses an8.14.3 bootstrap wrapper, so that mismatching JAR was deliberately not installed. Current script/property hashes are enforced by tools/check_toolchain.py and recorded in infrastructure-review.json. Distribution LICENSE exactly matches retained Apache2 notice.

Official AGP8.13 compatibility specifies minimum Gradle8.13/JDK17 and maximumAPI36.1; this maintenance version stays within Gradle8, but this is not a claim of completed AGP integration testing.

Security motivation: [worker/daemon channel advisory](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8) and [repository SSL fallback advisory](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5) identify versions below8.14.6. Local/shared host and malicious later-repository preconditions matter; no evidence of prior exploitation is asserted. Fixes do not prove all dependencies secure.

## Historical initial foundation proposal and receipts (superseded pins)

# Pinned foundation toolchain

Compile proposal: Gradle8.13, AGP8.13.2, JDK17, compile/targetAPI36, minAPI26, Build Tools35.0.0. No NDK, provider or AndroidX dependency in foundation. These are build pins, not supported-device claims.

[Official AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) supports this Gradle/JDK/API range. [Official Gradle checksums](https://gradle.org/release-checksums/) establish wrapper `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f` and distribution `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.

Wrapper scripts are downloaded byte-for-byte from Gradle upstream tag v8.13.0 (Apache2 header retained), not imported from the archived app. SHA256 gradlew `c76f16038b4e70b84ed0b3b9e08e82a8ecaf66ec0efdfdecdece92520fa3cecb`, gradlew.bat `0f3ed8f03b50934cb8c48b15a470d5c20a30a5385825e48b55bcc8ea3d8f8e18`. Archived scripts differ by omission of upstream UTF8 default; they were not reused. Official upstream wrapper JAR downloaded and checksum matched. All build settings, application and tests independently authored.

Run `python3 tools/check_toolchain.py` BEFORE wrapper execution. For Android builds use `--android`; only an already-authorized existing SDK with exact platform/build-tools is accepted. No sdkmanager license acceptance, generated license hashes or downloading third-party SDK/provider payloads. `android.builder.sdkDownload=false` and `org.gradle.java.installations.auto-download=false` prohibit implicit SDK/JDK download fallback. Required existing platform/build-tools file sets and source.properties API/revision identities are checked before Gradle, without claiming general runtime support. CI uses runner JDK17 and existing SDK; if missing it fails honestly, not changes pins silently. Actions checkout4.2.2 and upload-artifact4.6.2 are pinned to independently queried official tag SHAs; checkout credentials not retained.

Local executor has a stripped JDK21.0.12.1 runtime image and no Android SDK/JDK17 installation or javac executable. Module compiler exists, but `--release17` fails because release symbol tables are absent. Optional `bash tools/test_core.sh --limited-jre` uses source17/target17 under actualJDK21: HOST synthetic execution only, not JDK17 API compatibility or Android runtime proof. Default `bash tools/test_core.sh` requires full javac --release17; CI must establish exact-source JDK17/HOST and COMPILE evidence separately.

Build: `python3 tools/check_toolchain.py --android && ./gradlew --no-daemon :app:assembleDebug :app:lintDebug`; then `python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`. Verification checks the packaged manifest and debug signature, not just source XML. Output is a debug foundation artifact, **not a working clone engine or upgrade/release approval**. Version1/0.1.0-foundation is not an installed-user upgrade guarantee; release version/signing/upgrade migration belongs to T32 with owner authority. No keys are committed or uploaded.

Local bootstrap observation: hash check PASS, but `GRADLE_USER_HOME=/tmp/fonesi-apk-clean-gradle ./gradlew --version` failed exit1 at distribution HTTPS with `java.net.SocketException: Network is unreachable`. No Gradle distribution executed. Do not treat this as source/build failure or weaken TLS/checksum; exact-source hosted CI is the next authorized build route. Local `check_toolchain.py --android` separately fails honestly because SDK is absent.
