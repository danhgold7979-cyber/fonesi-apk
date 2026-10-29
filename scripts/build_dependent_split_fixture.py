#!/usr/bin/env python3
"""E22: build signed synthetic feature.beta depending on feature.alpha (no real apps/accounts)."""
import argparse
import json
import os
from pathlib import Path
import zipfile

from build_isolated_split_fixture import build as build_base_alpha, digest, run, PACKAGE

BETA = "feature.beta"
BETA_CLASS = "vn.fonesi.splitfixture.dependent.DependentEntrypoint"


def build(output: Path, sdk: Path, jdk: Path):
    output = output.resolve()
    build_base_alpha(output, sdk, jdk)
    aapt2 = sdk / "build-tools/36.0.0/aapt2"
    d8 = sdk / "build-tools/36.0.0/d8"
    apksigner = sdk / "build-tools/36.0.0/apksigner"
    android_jar = sdk / "platforms/android-36/android.jar"
    javac = jdk / "bin/javac"
    for binary in (aapt2, d8, apksigner, android_jar, javac):
        if not binary.is_file():
            raise RuntimeError("Missing exact E22 pinned tool: " + str(binary))

    work = output / "working-dependent"
    java_root = work / "src/vn/fonesi/splitfixture/dependent"
    java_root.mkdir(parents=True, exist_ok=True)
    source = java_root / "DependentEntrypoint.java"
    source.write_text("""package vn.fonesi.splitfixture.dependent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import vn.fonesi.splitfixture.feature.FeatureEntrypoint;
public final class DependentEntrypoint {
  private DependentEntrypoint() {}
  public static String challenge(String nonce) throws Exception {
    if (nonce == null || !nonce.matches("[0-9a-f]{32}"))
      throw new IllegalArgumentException("Bad nonce");
    // This is a *real* call to feature.alpha's class; beta's APK must not embed that class.
    String inherited = FeatureEntrypoint.challenge(nonce);
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(
      ("FONESI-E22-BETA:" + nonce).getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(64);
    for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
    return inherited + ":" + hex;
  }
}
""", encoding="utf-8")
    manifest = work / "AndroidManifest.xml"
    manifest.write_text(
        f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        f'package="{PACKAGE}" split="{BETA}" android:versionCode="1" android:isFeatureSplit="true">'
        '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>'
        '<uses-split android:name="feature.alpha"/>'
        '<application android:hasCode="true" android:debuggable="true" android:allowBackup="false"/>'
        '</manifest>', encoding="utf-8")
    values = work / "res/values"
    values.mkdir(parents=True, exist_ok=True)
    (values / "strings.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?><resources>'
        '<string name="fonesi_beta_marker">Fonesi signed dependent beta feature</string>'
        '</resources>', encoding="utf-8")
    compiled = work / "compiled"
    compiled.mkdir(exist_ok=True)
    run([aapt2, "compile", "--dir", work / "res", "-o", compiled])
    flats = sorted(compiled.glob("*.flat"))
    if not flats:
        raise RuntimeError("E22 beta resources were not compiled")
    unsigned = work / "unsigned.apk"
    run([aapt2, "link", "-o", unsigned, "-I", android_jar,
         "--manifest", manifest, "--min-sdk-version", "26",
         "--target-sdk-version", "34", "--auto-add-overlay", "--package-id", "0x81",
         *[v for f in flats for v in ("-R", f)]])
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    alpha_classes = output / "working-feature/classes"
    if not (alpha_classes / "vn/fonesi/splitfixture/feature/FeatureEntrypoint.class").is_file():
        raise RuntimeError("Signed alpha dependency class missing from E21 fixture build")
    run([javac, "--release", "8", "-classpath", alpha_classes,
         "-d", classes, source])
    dex = work / "dex"
    dex.mkdir(exist_ok=True)
    run([d8, "--min-api", "26", "--lib", android_jar, "--output", dex,
         *sorted(classes.rglob("*.class"))])
    dex_file = dex / "classes.dex"
    if not dex_file.is_file() or dex_file.stat().st_size < 100:
        raise RuntimeError("D8 did not produce beta-only synthetic DEX")
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as archive:
        if "classes.dex" in archive.namelist():
            raise RuntimeError("Duplicate classes.dex in dependent feature")
        archive.write(dex_file, "classes.dex")
    output_apk = output / "synthetic-split-dependent.apk"
    p12 = output / ".synthetic-only.p12"
    if not p12.is_file():
        raise RuntimeError("Short-lived fixture signing key missing")
    run([apksigner, "sign", "--min-sdk-version", "26",
         "--ks", p12, "--ks-pass", "pass:android", "--ks-key-alias", "fixture",
         "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true", "--out", output_apk, unsigned])
    run([apksigner, "verify", "--min-sdk-version", "26", "--verbose", output_apk])

    base = output / "synthetic-split-base.apk"
    alpha = output / "synthetic-split-feature.apk"
    receipts = {
        "package": PACKAGE,
        "base": "base.apk",
        "parent": "feature.alpha",
        "dependent": BETA,
        "base_sha256": digest(base),
        "feature_sha256": digest(alpha),
        "dependent_sha256": digest(output_apk),
        "signed_synthetic_only": True,
        "production_guest_engine_enabled": False,
        "third_party_accounts_tested": False,
    }
    (output / "dependency-checksums.json").write_text(
        json.dumps(receipts, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print("PASS: signed synthetic base + alpha + dependent beta DEX/resource fixtures assembled.")
    print("NOT VERIFIED HERE: Android installation, dependent private classloader and resources.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path,
                        default=Path(os.environ.get("ANDROID_HOME", "/tmp/fonesi-tools/android-sdk")))
    parser.add_argument("--java-home", type=Path,
                        default=Path(os.environ.get("JAVA_HOME", "/tmp/fonesi-tools/jdk-21.0.13+9")))
    args = parser.parse_args()
    build(args.output, args.sdk, args.java_home)
