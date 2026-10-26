#!/usr/bin/env python3
"""Install a pinned Android build toolchain outside the checkout, with checksums."""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
import zipfile

TOOLS = Path(os.environ.get("FONESI_TOOLS_DIR", "/workspace/.fonesi-tools"))
GRADLE_SHA256 = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"


def download(url, name, digest, algorithm="sha256"):
    cache = TOOLS / "downloads" / name
    cache.parent.mkdir(parents=True, exist_ok=True)
    if not cache.exists():
        temp = cache.with_suffix(cache.suffix + ".part")
        print(f"Downloading {name}", flush=True)
        with urllib.request.urlopen(url, timeout=90) as src, temp.open("wb") as dest:
            shutil.copyfileobj(src, dest)
        temp.replace(cache)
    actual = hashlib.new(algorithm, cache.read_bytes()).hexdigest()
    if actual != digest:
        raise RuntimeError(f"Checksum mismatch for {name}; artifact will not be used")
    return cache


def unzip(archive, destination):
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            target = (destination / item.filename).resolve()
            if not target.is_relative_to(destination.resolve()):
                raise RuntimeError("Unsafe archive path")
            z.extract(item, destination)
            mode = (item.external_attr >> 16) & 0o777
            if mode:
                target.chmod(mode)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--accept-sdk-license", action="store_true")
    args = parser.parse_args()
    if not args.accept_sdk_license:
        parser.error("Pass --accept-sdk-license to accept Google's Android SDK licenses")
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        parser.error("This bootstrap targets Linux x86_64 cloud machines")
    TOOLS.mkdir(parents=True, exist_ok=True)
    jdk = TOOLS / "jdk-21.0.13+9"
    if not (jdk / "bin/javac").exists():
        archive = download(
            "https://github.com/adoptium/temurin21-binaries/releases/download/"
            "jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz",
            "temurin-21.0.13.tar.gz",
            "d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c",
        )
        with tarfile.open(archive) as t:
            t.extractall(TOOLS, filter="data")
    sdk = TOOLS / "android-sdk"
    manager = sdk / "cmdline-tools/19.0/bin/sdkmanager"
    if not manager.exists():
        # Checksum published in Google's repository2-1.xml over verified HTTPS.
        archive = download(
            "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip",
            "android-commandlinetools-13114758.zip",
            "5fdcc763663eefb86a5b8879697aa6088b041e70", "sha1",
        )
        staging = TOOLS / "sdk-staging"
        staging.mkdir(exist_ok=True)
        unzip(archive, staging)
        manager.parent.parent.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(staging / "cmdline-tools"), str(sdk / "cmdline-tools/19.0"))
        staging.rmdir()
    env = os.environ.copy()
    env["JAVA_HOME"] = str(jdk)
    env["ANDROID_HOME"] = str(sdk)
    android_user = TOOLS / "android-user"
    android_user.mkdir(exist_ok=True)
    env["ANDROID_USER_HOME"] = str(android_user)
    env["PATH"] = str(jdk / "bin") + os.pathsep + env.get("PATH", "")
    proxy = urllib.parse.urlsplit(env.get("HTTPS_PROXY", env.get("https_proxy", "")))
    sdk_args = [str(manager), f"--sdk_root={sdk}"]
    if proxy.hostname:
        if proxy.username or proxy.password:
            raise RuntimeError("Authenticated proxy requires supported SDK proxy configuration")
        sdk_args += ["--proxy=http", f"--proxy_host={proxy.hostname}",
                     f"--proxy_port={proxy.port or 80}"]
    # Preserve TLS verification; the system Java trust store includes managed CA certificates.
    truststore = Path("/etc/ssl/certs/java/cacerts")
    if truststore.exists():
        env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + f" -Djavax.net.ssl.trustStore={truststore}"
    with (TOOLS / "sdk-install.log").open("w") as log:
        subprocess.run(sdk_args + ["--licenses"], input="y\n" * 200, text=True,
                       stdout=log, stderr=subprocess.STDOUT, env=env, check=True)
        subprocess.run(sdk_args + ["platforms;android-36", "build-tools;36.0.0", "platform-tools"],
                       stdout=log, stderr=subprocess.STDOUT, env=env, check=True)
    gradle = TOOLS / "gradle-8.13/bin/gradle"
    if not gradle.exists():
        archive = download("https://services.gradle.org/distributions/gradle-8.13-bin.zip",
                           "gradle-8.13-bin.zip", GRADLE_SHA256)
        unzip(archive, TOOLS)
    print("Android SDK 36, Temurin JDK 21 and Gradle 8.13 installed.")


if __name__ == "__main__":
    main()
