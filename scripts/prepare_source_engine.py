#!/usr/bin/env python3
"""Fetch exact licensed source and optional pinned native tools; never load the engine."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.parse

ROOT = Path(__file__).resolve().parent.parent
TOOLS = Path(os.environ.get("FONESI_TOOLS_DIR", "/workspace/.fonesi-tools"))
DOBBY_COMMIT = "5dfc8546954ce3b3198132ab13fddb89ee92cdd7"
DOBBY = TOOLS / "source-engine" / f"dobby-{DOBBY_COMMIT}"
NDK = "29.0.13846066"
CMAKE = "3.22.1"


def git(*args, cwd=None):
    return subprocess.check_output(["git", *args], cwd=cwd, text=True).strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--install-tools", action="store_true")
    args = parser.parse_args()
    DOBBY.parent.mkdir(parents=True, exist_ok=True)
    if not DOBBY.exists():
        stage = Path(tempfile.mkdtemp(prefix="dobby-source-", dir=DOBBY.parent))
        git("init", str(stage))
        git("remote", "add", "origin", "https://github.com/jmpews/Dobby.git", cwd=stage)
        git("fetch", "--depth=1", "origin", DOBBY_COMMIT, cwd=stage)
        git("-c", "advice.detachedHead=false", "checkout", "--detach", "FETCH_HEAD", cwd=stage)
        stage.rename(DOBBY)
    if git("rev-parse", "HEAD", cwd=DOBBY) != DOBBY_COMMIT or git("ls-files", "--others", "--exclude-standard", cwd=DOBBY):
        raise RuntimeError("Dobby source differs from the pinned checkout")
    patch = ROOT / "engine-source/patches/dobby-compat.patch"
    expected = patch.read_text().strip()
    actual = git("diff", "--binary", "--unified=0", cwd=DOBBY)
    if not actual:
        subprocess.run(["git", "apply", "--unidiff-zero", "--check", str(patch)], cwd=DOBBY, check=True)
        subprocess.run(["git", "apply", "--unidiff-zero", str(patch)], cwd=DOBBY, check=True)
    if git("diff", "--binary", "--unified=0", cwd=DOBBY) != expected or git("diff", "--cached", cwd=DOBBY):
        raise RuntimeError("Dobby working changes do not match the reviewed source patch")
    if not (DOBBY / "LICENSE").is_file() or not (DOBBY / "external/TINYSTL/vector.h").is_file():
        raise RuntimeError("Missing upstream license/attribution source")
    sdk = TOOLS / "android-sdk"
    required = [sdk / f"ndk/{NDK}/build/cmake/android.toolchain.cmake", sdk / f"cmake/{CMAKE}/bin/cmake"]
    if not all(path.is_file() for path in required):
        if not args.install_tools:
            raise RuntimeError("Run python3 scripts/prepare_source_engine.py --install-tools to install pinned NDK/CMake")
        manager = sdk / "cmdline-tools/19.0/bin/sdkmanager"
        env = os.environ.copy()
        env["JAVA_HOME"] = str(TOOLS / "jdk-21.0.13+9")
        env["ANDROID_USER_HOME"] = str(TOOLS / "android-user")
        arguments = [str(manager), f"--sdk_root={sdk}"]
        proxy = urllib.parse.urlsplit(env.get("HTTPS_PROXY", env.get("https_proxy", "")))
        if proxy.hostname:
            if proxy.username or proxy.password:
                raise RuntimeError("Authenticated proxy requires supported SDK proxy configuration")
            arguments += ["--proxy=http", f"--proxy_host={proxy.hostname}", f"--proxy_port={proxy.port or 80}"]
        trust = Path("/etc/ssl/certs/java/cacerts")
        if trust.exists():
            env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + f" -Djavax.net.ssl.trustStore={trust}"
        print("Installing pinned NDK/CMake using SDK Manager with TLS verification.", flush=True)
        subprocess.run(arguments + [f"ndk;{NDK}", f"cmake;{CMAKE}"], input="y\n" * 200, text=True, env=env, check=True)
    if not all(path.is_file() for path in required):
        raise RuntimeError("Native tool installation is incomplete")
    print(f"Verified source {DOBBY_COMMIT}, NDK {NDK}, CMake {CMAKE}. Runtime not executed.")


if __name__ == "__main__":
    main()
