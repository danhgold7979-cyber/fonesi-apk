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


OPERATION_BUTTONS = frozenset({"import_apk", "create_clone", "launch_clone", "migrate_data"})
METADATA_BUTTONS = frozenset({"metadata_first", "metadata_next"})
EXPECTED_BUTTONS = OPERATION_BUTTONS | METADATA_BUTTONS


def packaged_resource_ids(resources):
    # Formatter reference (not captured SDK36 evidence): AOSP android-16.0.0_r1
    # tools/aapt2/Debug.cpp, Debug::PrintTable. Actual hosted output must pass.
    # https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/tools/aapt2/Debug.cpp
    require(resources.splitlines() and resources.splitlines()[0] == "Binary APK",
            "Expected binary APK resource dump")
    packages = re.findall(r"(?m)^Package name=([^\n]+)$", resources)
    require(packages == ["vn.fonesi.rebuild.debug id=7f"], "Unexpected resource package")
    ids, names, all_ids = {}, set(), set()
    for line in resources.splitlines():
        if not line.lstrip().startswith("resource "):
            continue
        match = re.fullmatch(r"\s+resource (0x[0-9a-fA-F]{8}) ([a-z_]+)/([A-Za-z0-9_.]+)(?: PUBLIC| _PRIVATE_)?", line)
        require(match is not None, "Unrecognized resource declaration")
        compiled, kind, name = match.groups()
        number = int(compiled, 16)
        require(number >> 24 == 0x7f and number not in all_ids, "Duplicate or foreign compiled resource ID")
        all_ids.add(number)
        if kind == "id":
            require(name not in names, "Duplicate packaged ID name")
            names.add(name)
            ids[number] = name
    require(ids, "No packaged ID resources found")
    return ids


def verify_disabled_buttons(layout, resources):
    # Bind each disabled Button to its actual resource name, never count alone.
    resource_ids = packaged_resource_ids(resources)
    blocks = re.split(r"(?m)^\s*E: ", layout)[1:]
    elements = [block.split(" ", 1)[0] for block in blocks]
    require(not any(name != "Button" and name.endswith("Button") for name in elements),
            "Unexpected Button class")
    buttons = [block for block in blocks if block.startswith("Button ")]
    require(len(buttons) == 6, "Expected exactly six packaged Button elements")
    names = []
    for block in buttons:
        enabled = re.findall(r"(?m)^\s*A: (?:android:|http://schemas\.android\.com/apk/res/android:)enabled\(0x0101000e\)=(.*)$", block)
        require(enabled == ["false"], "Packaged Button is not explicitly disabled")
        matches = re.findall(r"(?m)^\s*A: http://schemas\.android\.com/apk/res/android:id\(0x010100d0\)=@(0x[0-9a-fA-F]{8})$", block)
        require(len(matches) == 1, "Packaged Button lacks unique compiled ID")
        require("onClick(" not in block, "Packaged Button must not declare an XML callback")
        number = int(matches[0], 16)
        require(number in resource_ids, "Button ID is absent from packaged ID resources")
        names.append(resource_ids[number])
    require(len(set(names)) == 6 and set(names) == EXPECTED_BUTTONS,
            "Packaged Button names differ from exact operation/metadata allowlist")


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
    resources = run(str(tools / "aapt2"), "dump", "resources", str(apk))
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    for text in ("package: name='vn.fonesi.rebuild.debug'", "minSdkVersion:'26'", "targetSdkVersion:'36'",
                 "application-debuggable", "launchable-activity: name='vn.fonesi.rebuild.ManagerActivity'"):
        require(text in badging, "Missing APK evidence: " + text)
    require("uses-permission" not in badging, "Unexpected APK permission")
    require("allowBackup" in manifest and "allowBackup(0x01010280)=false" in manifest,
            "APK backup must be disabled")
    components = re.findall(r"(?m)^\s*E: (activity|activity-alias|service|receiver|provider) ", manifest)
    require(components == ["activity"], "Unexpected packaged component set")
    verify_disabled_buttons(layout, resources)
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
