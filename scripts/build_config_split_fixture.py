#!/usr/bin/env python3
"""E23: disposable signed resources-only French configuration split for synthetic alpha."""
import argparse
import json
import os
from pathlib import Path
import zipfile

from build_dependent_split_fixture import build as build_dependent
from build_isolated_split_fixture import PACKAGE, digest, run

CONFIG = "feature.alpha.fr"
TARGET = "feature.alpha"


def build(output: Path, sdk: Path, jdk: Path):
    output = output.resolve()
    build_dependent(output, sdk, jdk)
    aapt2 = sdk / "build-tools/36.0.0/aapt2"
    signer = sdk / "build-tools/36.0.0/apksigner"
    jar = sdk / "platforms/android-36/android.jar"
    if not all(p.is_file() for p in (aapt2, signer, jar)):
        raise RuntimeError("Missing pinned Android SDK36 config split build tools")
    work = output / "working-alpha-config"
    work.mkdir(exist_ok=True)
    manifest = work / "AndroidManifest.xml"
    manifest.write_text(
        f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        f'package="{PACKAGE}" split="{CONFIG}" configForSplit="{TARGET}" '
        'android:versionCode="1">'
        '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>'
        '<application android:hasCode="false" android:debuggable="true" android:allowBackup="false"/>'
        '</manifest>', encoding="utf-8"
    )
    default_values = work / "res/values"
    french_values = work / "res/values-fr"
    default_values.mkdir(parents=True, exist_ok=True)
    french_values.mkdir(parents=True, exist_ok=True)
    default_values.joinpath("strings.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?><resources>'
        '<string name="fonesi_feature_marker">Fonesi signed isolated split feature</string>'
        '</resources>', encoding="utf-8"
    )
    french_values.joinpath("strings.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?><resources>'
        '<string name="fonesi_feature_marker">Fonesi signed alpha French config</string>'
        '</resources>', encoding="utf-8"
    )
    compiled = work / "compiled"
    compiled.mkdir(exist_ok=True)
    run([aapt2, "compile", "--dir", work / "res", "-o", compiled])
    flats = sorted(compiled.glob("*.flat"))
    if len(flats) < 2:
        raise RuntimeError("French and default signed configuration resources were not compiled")
    unsigned = work / "unsigned.apk"
    run([aapt2, "link", "-o", unsigned, "-I", jar, "--manifest", manifest,
         "--min-sdk-version", "26", "--target-sdk-version", "34",
         "--auto-add-overlay", "--package-id", "0x80",
         *[item for flat in flats for item in ("-R", flat)]])
    with zipfile.ZipFile(unsigned) as archive:
        if "classes.dex" in archive.namelist() or "resources.arsc" not in archive.namelist():
            raise RuntimeError("Configuration split must be resource-only")
    target = output / "synthetic-split-config.apk"
    key = output / ".synthetic-only.p12"
    if not key.is_file():
        raise RuntimeError("Missing disposable signing fixture key")
    run([signer, "sign", "--min-sdk-version", "26",
         "--ks", key, "--ks-pass", "pass:android", "--ks-key-alias", "fixture",
         "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true", "--out", target, unsigned])
    run([signer, "verify", "--min-sdk-version", "26", "--verbose", target])
    paths = {
        "base": output / "synthetic-split-base.apk",
        "feature": output / "synthetic-split-feature.apk",
        "dependent": output / "synthetic-split-dependent.apk",
        "config": target,
    }
    receipt = {
        "package": PACKAGE, "version": 1,
        "base_sha256": digest(paths["base"]),
        "feature_sha256": digest(paths["feature"]),
        "dependent_sha256": digest(paths["dependent"]),
        "config_sha256": digest(paths["config"]),
        "config_name": CONFIG,
        "config_for": TARGET,
        "has_config_code": False,
        "signed_synthetic_only": True,
        "production_guest_engine_enabled": False,
        "third_party_accounts_tested": False,
    }
    (output / "config-checksums.json").write_text(
        json.dumps(receipt, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print("PASS: signed synthetic 4-APK base/alpha/beta/alpha-FR configuration fixture built.")
    print("NOT VERIFIED YET: Android config split installation and locale-specific private Resources.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path,
                        default=Path(os.environ.get("ANDROID_HOME", "/tmp/fonesi-tools/android-sdk")))
    parser.add_argument("--java-home", type=Path,
                        default=Path(os.environ.get("JAVA_HOME", "/tmp/fonesi-tools/jdk-21.0.13+9")))
    args = parser.parse_args()
    build(args.output, args.sdk, args.java_home)
