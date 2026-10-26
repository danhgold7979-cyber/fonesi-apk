#!/usr/bin/env python3
"""Reproducible candidate overlay derived from the verified offline SDK overlay."""
import argparse
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = "{http://schemas.android.com/apk/res/android}"
T = "{http://schemas.android.com/tools}"
ET.register_namespace("android", A[1:-1])
ET.register_namespace("tools", T[1:-1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    subprocess.run([sys.executable, str(ROOT / "scripts/generate_probe_manifest.py"), "--check"], check=True)
    root = ET.parse(ROOT / "engine-probe/src/main/AndroidManifest.xml").getroot()
    kept = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.CAMERA", "android.permission.RECORD_AUDIO"}
    for item in list(root):
        if item.tag == "queries" or (item.tag == "uses-permission" and item.get(A + "name") in kept):
            root.remove(item)
    for name in sorted(kept):
        ET.SubElement(root, "uses-permission", {A + "name": name})
    for name in ["android.hardware.camera", "android.hardware.camera.autofocus", "android.hardware.microphone"]:
        ET.SubElement(root, "uses-feature", {A + "name": name, A + "required": "false"})
    app = root.find("application")
    app.attrib.clear()
    app.set(A + "permission", "${applicationId}.permission.INTERNAL_SDK")
    app.set(A + "usesCleartextTraffic", "false")
    app.set(A + "largeHeap", "false")
    app.set(T + "replace", "android:usesCleartextTraffic,android:largeHeap")
    for activity in list(app.findall("activity")):
        app.remove(activity)
    for name in ["vn.fonesi.app.MainActivity", "vn.fonesi.app.shortcuts.CloneLaunchActivity"]:
        ET.SubElement(app, "activity", {A + "name": name, A + "permission": ""})
    for prop in app.findall("service/property"):
        prop.set(A + "value", "Isolated app sessions started explicitly by the user")
    ET.indent(root, space="    ")
    content = "<!-- Candidate SDK permission/component overlay; audited on final APK. -->\n" + ET.tostring(root, encoding="unicode") + "\n"
    out = ROOT / "app/src/candidate/AndroidManifest.xml"
    if args.check:
        if out.read_bytes() != content.encode("utf-8"):
            raise RuntimeError("Candidate overlay differs from pinned generator")
        print("Candidate manifest overlay verified; no files changed.")
    else:
        out.write_text(content)


if __name__ == "__main__":
    main()
