#!/usr/bin/env python3
"""Prepare tools for an isolated synthetic Android native probe, without using a phone."""
import argparse
import os
from pathlib import Path
import subprocess
import urllib.parse
from prepare_source_engine import TOOLS

IMAGES = {
    "aosp34": "system-images;android-34;default;x86_64",
    "google34": "system-images;android-34;google_apis;x86_64",
}


def environment():
    env = os.environ.copy()
    env["JAVA_HOME"] = str(TOOLS / "jdk-21.0.13+9")
    env["ANDROID_HOME"] = str(TOOLS / "android-sdk")
    env["ANDROID_USER_HOME"] = str(TOOLS / "android-user")
    env["ANDROID_EMULATOR_HOME"] = str(TOOLS / "emulator-user")
    env["ANDROID_AVD_HOME"] = str(TOOLS / "avd")
    for key in ("ANDROID_USER_HOME", "ANDROID_EMULATOR_HOME", "ANDROID_AVD_HOME"):
        Path(env[key]).mkdir(parents=True, exist_ok=True)
    trust = Path("/etc/ssl/certs/java/cacerts")
    if trust.exists(): env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + f" -Djavax.net.ssl.trustStore={trust}"
    return env


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--image", choices=IMAGES, default="aosp34")
    args = parser.parse_args()
    env = environment()
    sdk = Path(env["ANDROID_HOME"])
    image = IMAGES[args.image]
    required = [sdk / "emulator/emulator", sdk.joinpath(*image.split(";"), "system.img")]
    if not all(path.is_file() for path in required):
        if not args.install: parser.error("Run with --install to install the selected emulator image")
        arguments = [str(sdk / "cmdline-tools/19.0/bin/sdkmanager"), f"--sdk_root={sdk}"]
        proxy = urllib.parse.urlsplit(env.get("HTTPS_PROXY", env.get("https_proxy", "")))
        if proxy.hostname:
            if proxy.username or proxy.password: raise RuntimeError("Authenticated proxy requires supported SDK configuration")
            arguments += ["--proxy=http", f"--proxy_host={proxy.hostname}", f"--proxy_port={proxy.port or 80}"]
        subprocess.run(arguments + ["emulator", image], env=env, input="y\n" * 200, text=True, check=True)
    if not all(path.is_file() for path in required): raise RuntimeError("Emulator/image installation is incomplete")
    print(f"Prepared {image}. Boot and Android native execution remain unverified.")


if __name__ == "__main__":
    main()
