"""Inspect a built debug scaffold using preinstalled SDK tools; never install it."""
import hashlib
import os
import re
from pathlib import Path
import subprocess
import zipfile


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def verify_disabled_buttons(layout):
    # Strict aapt2 xmltree text contract; unexpected tool formatting fails closed.
    # Bind enabled=false and a unique compiled ID within each Button element.
    blocks = re.split(r"(?m)^\s*E: ", layout)[1:]
    buttons = [block for block in blocks if block.startswith("Button ")]
    require(len(buttons) == 4, "Expected exactly four packaged Button elements")
    ids = []
    for block in buttons:
        require("enabled(0x0101000e)=false" in block, "Packaged Button is not disabled")
        match = re.search(r"http://schemas\.android\.com/apk/res/android:id\(0x010100d0\)=@(0x[0-9a-fA-F]+)", block)
        require(match is not None, "Packaged Button lacks compiled ID")
        ids.append(match.group(1))
    require(len(set(ids)) == 4, "Packaged Button IDs must be distinct")


def verify(repo, sdk):
    apk = repo / "rebuild-android/app/build/outputs/apk/debug/app-debug.apk"
    tools = sdk / "build-tools/36.0.0"
    def run(*args):
        result = subprocess.run(args, check=True, text=True, capture_output=True)
        print("SDK EVIDENCE: " + " ".join(args[1:]), flush=True)
        print(result.stdout, flush=True)
        if result.stderr:
            print(result.stderr, flush=True)
        return result.stdout
    badging = run(str(tools / "aapt2"), "dump", "badging", str(apk))
    manifest = run(str(tools / "aapt2"), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml")
    run(str(tools / "zipalign"), "-c", "4", str(apk))
    layout = run(str(tools / "aapt2"), "dump", "xmltree", str(apk), "--file", "res/layout/manager.xml")
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    for text in ("package: name='vn.fonesi.rebuild.debug'", "minSdkVersion:'26'", "targetSdkVersion:'36'",
                 "application-debuggable", "launchable-activity: name='vn.fonesi.rebuild.ManagerActivity'"):
        require(text in badging, "Missing APK evidence: " + text)
    require("uses-permission" not in badging, "Unexpected APK permission")
    require("allowBackup" in manifest and "allowBackup(0x01010280)=false" in manifest,
            "APK backup must be disabled")
    components = re.findall(r"(?m)^\s*E: (activity|activity-alias|service|receiver|provider) ", manifest)
    require(components == ["activity"], "Unexpected packaged component set")
    verify_disabled_buttons(layout)
    require("CN=Android Debug" in signature, "Expected debug signer, never release")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require("classes.dex" in names and "AndroidManifest.xml" in names, "Missing APK payload")
        require(not any(n.startswith(("lib/", "assets/")) for n in names), "Unexpected native or asset payload")
    print(badging)
    print(signature)
    print("APK SHA256=" + hashlib.sha256(apk.read_bytes()).hexdigest())
    print("COMPILE/PACKAGE EVIDENCE ONLY; NO DEVICE EXECUTION OR ISOLATION ACCEPTANCE")


if __name__ == "__main__":
    verify(Path(__file__).resolve().parents[1], Path(os.environ["ANDROID_HOME"]))
