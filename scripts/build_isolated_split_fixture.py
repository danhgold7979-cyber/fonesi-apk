#!/usr/bin/env python3
"""Build two project-owned signed synthetic APK splits with Java DEX and distinct resources.

NO external app code, account data, committed keystores, production signing or permissions.
"""
import argparse
import hashlib
import json
from pathlib import Path
import os
import subprocess
import zipfile

PACKAGE = "vn.fonesi.splitfixture"
FEATURE = "feature.alpha"
BASE_CLASS = "vn.fonesi.splitfixture.base.BaseEntrypoint"
FEATURE_CLASS = "vn.fonesi.splitfixture.feature.FeatureEntrypoint"


def run(args, **kw):
    return subprocess.run([str(a) for a in args], check=True, **kw)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(output: Path, sdk: Path, jdk: Path):
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    aapt2 = sdk / "build-tools/36.0.0/aapt2"
    d8 = sdk / "build-tools/36.0.0/d8"
    signer = sdk / "build-tools/36.0.0/apksigner"
    jar = sdk / "platforms/android-36/android.jar"
    javac = jdk / "bin/javac"
    keytool = jdk / "bin/keytool"
    for tool in (aapt2, d8, signer, javac, keytool, jar):
        if not tool.is_file():
            raise RuntimeError("Pinned split fixture tool not found: " + str(tool))

    p12 = output / ".synthetic-only.p12"
    if not p12.exists():
        run([keytool, "-genkeypair", "-keystore", p12, "-storetype", "PKCS12",
             "-storepass", "android", "-keypass", "android", "-alias", "fixture",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "3",
             "-dname", "CN=Fonesi isolated split disposable fixture"])

    definitions = [
        ("base", None, BASE_CLASS, "base_marker",
         "Fonesi signed isolated split base", "FONESI-E21-BASE:"),
        ("feature", FEATURE, FEATURE_CLASS, "feature_marker",
         "Fonesi signed isolated split feature", "FONESI-E21-FEATURE:"),
    ]
    output_apks = []
    for kind, split, java_name, resource_name, resource_value, domain in definitions:
        work = output / ("working-" + kind)
        work.mkdir(exist_ok=True)
        src = work / "src" / Path(*java_name.split(".")[:-1])
        src.mkdir(parents=True, exist_ok=True)
        class_name = java_name.split(".")[-1]
        java = src / (class_name + ".java")
        java.write_text(
            f"""package {'.'.join(java_name.split('.')[:-1])};
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
public final class {class_name} {{
  private {class_name}() {{}}
  public static String challenge(String nonce) throws Exception {{
    if (nonce == null || !nonce.matches("[0-9a-f]{{32}}")) throw new IllegalArgumentException("Bad nonce");
    byte[] data = MessageDigest.getInstance("SHA-256").digest(
      ("{domain}" + nonce).getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder(64);
    for (byte b : data) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return result.toString();
  }}
}}
""", encoding="utf-8")
        manifest = work / "AndroidManifest.xml"
        if split is None:
            manifest.write_text(
                f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                f'package="{PACKAGE}" android:versionCode="1" android:isolatedSplits="true">'
                '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>'
                '<application android:hasCode="true" android:debuggable="true" '
                'android:label="Fonesi synthetic isolated split" android:allowBackup="false" '
                'android:isolatedSplits="true"/></manifest>', encoding="utf-8")
        else:
            manifest.write_text(
                f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                f'package="{PACKAGE}" split="{split}" android:versionCode="1" '
                'android:isFeatureSplit="true">'
                '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>'
                '<application android:hasCode="true" android:debuggable="true"/>'
                '</manifest>', encoding="utf-8")

        values = work / "res/values"
        values.mkdir(parents=True, exist_ok=True)
        (values / "strings.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?><resources>'
            f'<string name="fonesi_{resource_name}">{resource_value}</string>'
            '</resources>', encoding="utf-8")
        compiled = work / "compiled"
        compiled.mkdir(exist_ok=True)
        run([aapt2, "compile", "--dir", work / "res", "-o", compiled])
        flats = sorted(compiled.glob("*.flat"))
        if not flats:
            raise RuntimeError("Synthetic resource compiler produced no resources")

        unsigned = work / "unsigned.apk"
        args = [aapt2, "link", "-o", unsigned, "-I", jar,
                "--manifest", manifest, "--min-sdk-version", "26",
                "--target-sdk-version", "34", "--auto-add-overlay"]
        if split is not None:
            # A genuine isolated feature must not share the base's resource ID namespace.
            args += ["--feature-of", output / "working-base/unsigned.apk",
                     "--package-id", "0x80"]
        args += [arg for flat in flats for arg in ("-R", flat)]
        run(args)
        classes = work / "classes"
        classes.mkdir(exist_ok=True)
        run([javac, "--release", "8", "-d", classes, java])
        dex = work / "dex"
        dex.mkdir(exist_ok=True)
        run([d8, "--min-api", "26", "--lib", jar, "--output", dex,
             *sorted(classes.rglob("*.class"))])
        dex_file = dex / "classes.dex"
        if not dex_file.is_file() or dex_file.stat().st_size < 100:
            raise RuntimeError("D8 did not produce synthetic split DEX")
        with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
            if "classes.dex" in z.namelist():
                raise RuntimeError("Unexpected duplicated DEX in linked split APK")
            z.write(dex_file, "classes.dex")
        target = output / ("synthetic-split-" + kind + ".apk")
        run([signer, "sign", "--min-sdk-version", "26",
             "--ks", p12, "--ks-pass", "pass:android", "--ks-key-alias", "fixture",
             "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
             "--v3-signing-enabled", "true", "--out", target, unsigned])
        run([signer, "verify", "--min-sdk-version", "26", "--verbose", target])
        output_apks.append(target)

    manifest = {"package": PACKAGE, "split": FEATURE,
                "base_sha256": digest(output_apks[0]), "feature_sha256": digest(output_apks[1]),
                "signed_synthetic_only": True, "guest_activity_executed": False}
    (output / "checksums.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("PASS: two individually signed disposable base/feature DEX/resource APKs built.")
    print("NOT VERIFIED: Android installation, feature classloader and resources.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", "/tmp/fonesi-tools/android-sdk")))
    parser.add_argument("--java-home", type=Path, default=Path(os.environ.get("JAVA_HOME", "/tmp/fonesi-tools/jdk-21.0.13+9")))
    args = parser.parse_args()
    build(args.output, args.sdk, args.java_home)
