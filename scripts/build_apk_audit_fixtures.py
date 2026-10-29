#!/usr/bin/env python3
"""Build actual signed, resource-only APK clusters for integrity tests; never run guests."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def run(arguments):
    subprocess.run([str(arg) for arg in arguments], check=True, stdout=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME"))
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    if args.sdk is None or args.java_home is None:
        parser.error("Pass --sdk/--java-home or set ANDROID_HOME/JAVA_HOME")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    sdk = args.sdk
    tools = sdk / "build-tools/36.0.0"
    keytool = args.java_home / "bin/keytool"
    for name in ["primary", "other"]:
        key = output / f"{name}.p12"
        if not key.exists():
            run([keytool, "-genkeypair", "-keystore", key, "-storetype", "PKCS12", "-storepass", "android",
                 "-keypass", "android", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048",
                 "-validity", "3", "-dname", f"CN=Fonesi disposable {name} test key"])

    def apk(name, split=None, feature=False, config=None, uses=None, version=1,
            major=0, package="vn.fonesi.fixture", signer="primary", split_required=False, required_types=None, provided_types=None, isolated=False, application=None):
        staging = output / name
        staging.mkdir(exist_ok=True)
        attributes = [f'package="{package}"', 'xmlns:android="http://schemas.android.com/apk/res/android"',
                      f'android:versionCode="{version}"', f'android:versionCodeMajor="{major}"']
        if split is not None:
            attributes.append(f'split="{split}"')
        if feature:
            attributes.append('android:isFeatureSplit="true"')
        if config is not None:
            attributes.append(f'configForSplit="{config}"')
        if split_required:
            attributes.append('android:isSplitRequired="true"')
        if isolated:
            attributes.append('android:isolatedSplits="true"')
        if required_types:
            attributes.append(f'android:requiredSplitTypes="{required_types}"')
        if provided_types:
            attributes.append(f'android:splitTypes="{provided_types}"')
        dependency = f'<uses-split android:name="{uses}" />' if uses else ""
        manifest = staging / "AndroidManifest.xml"
        manifest.write_text('<manifest ' + ' '.join(attributes) + '><uses-sdk android:minSdkVersion="26" '
                            'android:targetSdkVersion="34" />' + dependency +
                            (application or '<application android:hasCode="false" />') + '</manifest>')
        unsigned = staging / "unsigned.apk"
        run([tools / "aapt2", "link", "--manifest", manifest, "-I", sdk / "platforms/android-36/android.jar", "-o", unsigned])
        target = output / f"{name}.apk"
        run([tools / "apksigner", "sign", "--ks", output / f"{signer}.p12", "--ks-pass", "pass:android",
             "--ks-key-alias", "fixture", "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
             "--v3-signing-enabled", "true", "--out", target, unsigned])
        return target, unsigned

    base, unsigned = apk("main-disguised")
    shutil.copyfile(unsigned, output / "unsigned.apk")
    apk("base", split="feature.chat", feature=True)  # Deliberately misleading file name.
    apk("chat-config", split="feature.chat.en", config="feature.chat")
    apk("base-config", split="config.en")
    apk("isolated-base", isolated=True)
    apk("new-version", version=2)
    apk("new-version-config", split="config.en", version=2)
    apk("version-major", version=7, major=1)
    apk("version-major-config", split="config.en", version=7, major=1)
    apk("wrong-package", split="config.en", package="vn.fonesi.other")
    apk("wrong-signer", split="config.en", signer="other")
    apk("missing-dependency", split="feature.pay", feature=True, uses="feature.missing")
    apk("missing-config-target", split="feature.missing.en", config="feature.missing")
    apk("dependent-feature", split="feature.pay", feature=True, uses="feature.chat")
    apk("cycle-a", split="feature.a", feature=True, uses="feature.b")
    apk("cycle-b", split="feature.b", feature=True, uses="feature.a")
    apk("requires-any-split", split_required=True)
    apk("requires-abi-type", split_required=True, required_types="base__abi")
    apk("provides-abi-type", split="config.arm64_v8a", provided_types="base__abi")

    # E68: signed component fixtures are a manifest catalog only, never guest execution.
    apk("catalog-base", application=(
        '<application android:name=".GuestApplication" android:hasCode="false">'
        '<activity android:name=".EntryActivity" android:exported="true" android:process=":guest" />'
        '<activity android:name=".ImplicitActivity" />'
        '<activity-alias android:name=".EntryAlias" android:targetActivity=".EntryActivity" android:exported="false" />'
        '<service android:name=".WorkerService" android:permission="vn.fonesi.fixture.ACCESS" '
        'android:enabled="true" android:exported="false" />'
        '<receiver android:name=".EventReceiver" android:exported="false" />'
        '<provider android:name=".AccountProvider" android:authorities="vn.fonesi.fixture.accounts;vn.fonesi.fixture.files" '
        'android:exported="false" />'
        '</application>'))
    apk("catalog-feature", split="feature.catalog", feature=True, application=(
        '<application android:hasCode="false">'
        '<activity android:name=".FeatureActivity" android:exported="false" />'
        '</application>'))
    apk("catalog-config", split="config.de")
    apk("catalog-duplicate", split="feature.duplicate", feature=True, application=(
        '<application android:hasCode="false">'
        '<activity android:name=".EntryActivity" android:exported="false" />'
        '</application>'))
    apk("catalog-illegal-config", split="feature.catalog.fr", config="feature.catalog", application=(
        '<application android:hasCode="false">'
        '<activity android:name=".ConfigAttack" android:exported="true" />'
        '</application>'))
    apk("catalog-duplicate-authority", split="feature.collision", feature=True, application=(
        '<application android:hasCode="false">'
        '<provider android:name=".CollisionProvider" android:authorities="vn.fonesi.fixture.accounts" '
        'android:exported="false" />'
        '</application>'))
    # E70 only selects explicitly named signed/private Activity metadata. No runtime launch.
    apk("catalog-public", application=(
        '<application android:hasCode="false">'
        '<activity android:name=".PublicActivity" android:exported="true" android:enabled="true" />'
        '<activity android:name=".LocalActivity" android:exported="false" android:enabled="true" />'
        '<activity android:name=".UnknownExported" android:enabled="true" />'
        '<activity android:name=".UnknownEnabled" android:exported="true" />'
        '<activity android:name=".DisabledActivity" android:exported="true" android:enabled="false" />'
        '<activity android:name=".ProtectedActivity" android:exported="true" android:enabled="true" '
        'android:permission="vn.fonesi.fixture.ACCESS" />'
        '<activity android:name=".AliasTarget" android:exported="false" android:enabled="true" />'
        '<activity-alias android:name=".PublicAlias" android:targetActivity=".AliasTarget" '
        'android:exported="true" android:enabled="true" />'
        '<activity-alias android:name=".DisabledAlias" android:targetActivity=".AliasTarget" '
        'android:exported="true" android:enabled="false" />'
        '<activity android:name=".DisabledTarget" android:enabled="false" android:exported="true" />'
        '<activity-alias android:name=".AliasToDisabled" android:targetActivity=".DisabledTarget" '
        'android:enabled="true" android:exported="true" />'
        '<service android:name=".NotActivity" android:exported="true" android:enabled="true" />'
        '</application>'))
    # E70 inherited Application authorization is distinct from Activity attributes.
    apk("catalog-app-protected", application=(
        '<application android:hasCode="false" android:permission="vn.fonesi.fixture.ACCESS">'
        '<activity android:name=".AppProtectedActivity" android:exported="true" android:enabled="true" />'
        '</application>'))
    apk("catalog-app-disabled", application=(
        '<application android:hasCode="false" android:enabled="false">'
        '<activity android:name=".AppDisabledActivity" android:exported="true" android:enabled="true" />'
        '</application>'))
    apk("catalog-split-app-permission", split="feature.overrides", feature=True, application=(
        '<application android:hasCode="false" android:permission="vn.fonesi.fixture.ACCESS">'
        '<activity android:name=".InjectedActivity" android:exported="true" android:enabled="true" />'
        '</application>'))
    apk("catalog-public-feature", split="feature.public", feature=True, application=(
        '<application android:hasCode="false">'
        '<activity android:name=".FeatureActivity" android:enabled="true" android:exported="true" />'
        '</application>'))
    apk("catalog-bad-alias", application=(
        '<application android:hasCode="false">'
        '<activity-alias android:name=".OrphanAlias" android:targetActivity=".NotDeclared" android:exported="false" />'
        '</application>'))

    # Repack signed content: signature metadata no longer authenticates the modified archive.
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(output / "tampered.apk", "w") as changed:
        for item in original.infolist():
            changed.writestr(item, original.read(item))
        changed.writestr("assets/modified.txt", b"not covered by original signature")
    with zipfile.ZipFile(unsigned) as original:
        oversized_manifest = original.read("AndroidManifest.xml") + b' ' * (4 * 1024 * 1024)
    for name, manifest_bytes in [
        ("text-manifest", b'<manifest package="vn.fonesi.fixture"/>'),
        ("oversized-manifest", oversized_manifest),
    ]:
        malformed = output / f"{name}-unsigned.apk"
        with zipfile.ZipFile(unsigned) as original, zipfile.ZipFile(malformed, "w") as changed:
            for item in original.infolist():
                changed.writestr(item, manifest_bytes if item.filename == "AndroidManifest.xml" else original.read(item))
        run([tools / "apksigner", "sign", "--min-sdk-version", "26", "--ks", output / "primary.p12",
             "--ks-pass", "pass:android", "--ks-key-alias", "fixture", "--v1-signing-enabled", "false",
             "--v2-signing-enabled", "true", "--v3-signing-enabled", "true", "--out", output / f"{name}.apk", malformed])
    print("Built signed APK set fixtures with disposable test keys; no engine or account executed.")


if __name__ == "__main__":
    main()
