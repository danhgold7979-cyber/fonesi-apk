#!/usr/bin/env python3
"""Audit the actual signed APKs, independently of manifest-source lint."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(os.environ.get("FONESI_TOOLS_DIR", "/workspace/.fonesi-tools"))
A = "{http://schemas.android.com/apk/res/android}"


def command(args):
    env = os.environ.copy()
    env["JAVA_HOME"] = str(TOOLS / "jdk-21.0.13+9")
    return subprocess.check_output([str(a) for a in args], text=True, env=env)


def inspect_apk(path, package):
    manifest = ET.fromstring(command([
        TOOLS / "android-sdk/cmdline-tools/19.0/bin/apkanalyzer", "manifest", "print", path,
    ]))
    if manifest.get("package") != package:
        raise RuntimeError("Unexpected APK package identity")
    if manifest.find("uses-sdk").get(A + "targetSdkVersion") != "36":
        raise RuntimeError("Probe must keep target SDK 36")
    signatures = command([
        TOOLS / "android-sdk/build-tools/36.0.0/apksigner", "verify", "--print-certs", path,
    ])
    certificate = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", signatures)
    if len(certificate) != 1:
        raise RuntimeError("Expected one verified APK signer")
    permissions = {p.get(A + "name") for p in manifest.findall("uses-permission")}
    return manifest, permissions, certificate[0].lower()


def main():
    probe = ROOT / "engine-probe/build/outputs/apk/debug/engine-probe-debug.apk"
    payload = ROOT / "probe-payload/build/outputs/apk/debug/probe-payload-debug.apk"
    base = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    manifest, permissions, probe_cert = inspect_apk(probe, "vn.fonesi.probe")
    _, payload_permissions, payload_cert = inspect_apk(payload, "vn.fonesi.payload")
    _, base_permissions, _ = inspect_apk(base, "vn.fonesi.app")
    if base_permissions != {"vn.fonesi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}:
        raise RuntimeError("Base/scanner app has unexpected permissions")
    with zipfile.ZipFile(base) as archive:
        if any("libfcore.so" in name for name in archive.namelist()):
            raise RuntimeError("Experimental engine leaked into the base application")
    internal = "vn.fonesi.probe.permission.INTERNAL_SDK"
    expected = {
        internal,
        "vn.fonesi.probe.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.POST_NOTIFICATIONS",
    }
    if permissions != expected:
        raise RuntimeError(f"Probe APK permission allowlist mismatch: {sorted(permissions ^ expected)}")
    if payload_permissions:
        raise RuntimeError(f"Synthetic payload unexpectedly requests permissions: {sorted(payload_permissions)}")
    if probe_cert != payload_cert:
        raise RuntimeError("Probe and payload are not signed by the same development key")
    definition = next((p for p in manifest.findall("permission") if p.get(A + "name") == internal), None)
    level = definition.get(A + "protectionLevel") if definition is not None else None
    # apkanalyzer prints the compiled value as 0x2; source XML uses "signature".
    signature_only = level == "signature" or (level is not None and level.startswith("0x") and int(level, 16) == 2)
    if not signature_only:
        raise RuntimeError("SDK component guard is not a signature permission")
    app = manifest.find("application")
    if app.get(A + "permission") != internal:
        raise RuntimeError("SDK components do not inherit the signature permission")
    if app.get(A + "allowBackup") != "false" or app.get(A + "usesCleartextTraffic") != "false":
        raise RuntimeError("Probe backup/network policy differs from reviewed policy")
    public_components = []
    protected_count = 0
    # Exact standard-component exceptions, not arbitrary SDK-defined permission names.
    allowed_system_components = {
        ("service", "com.fvbox.lib.client.proxy.ProxyJobService"): "android.permission.BIND_JOB_SERVICE",
        ("receiver", "androidx.profileinstaller.ProfileInstallReceiver"): "android.permission.DUMP",
    }
    for tag in ["activity", "activity-alias", "service", "provider", "receiver"]:
        for component in app.findall(tag):
            if component.get(A + "exported") != "true":
                continue
            name = component.get(A + "name")
            permission = component.get(A + "permission", app.get(A + "permission"))
            if not permission:
                public_components.append((tag, name))
            elif permission == internal or permission == allowed_system_components.get((tag, name)):
                protected_count += 1
            else:
                raise RuntimeError(f"Unexpected exported component guard: {name}: {permission}")
    if public_components != [("activity", "vn.fonesi.probe.ProbeActivity")]:
        raise RuntimeError(f"Unexpected unprotected components: {public_components}")
    with zipfile.ZipFile(probe) as archive:
        abis = {n.split("/")[1] for n in archive.namelist() if n.startswith("lib/") and n.endswith(".so")}
    if abis != {"arm64-v8a"}:
        raise RuntimeError(f"Unexpected native ABI set: {abis}")
    report = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "Static APK/signature checks only; engine runtime and data isolation not tested",
        "probe_sha256": hashlib.sha256(probe.read_bytes()).hexdigest(),
        "payload_sha256": hashlib.sha256(payload.read_bytes()).hexdigest(),
        "base_sha256": hashlib.sha256(base.read_bytes()).hexdigest(),
        "base_permissions": sorted(base_permissions),
        "verified_same_signer": True,
        "probe_permissions": sorted(permissions),
        "payload_permissions": sorted(payload_permissions),
        "unprotected_exported_components": public_components,
        "guarded_exported_components": protected_count,
        "native_abis": sorted(abis),
    }
    output = ROOT / "engine-probe/build/reports/probe-apk-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print("PASS: actual APK permissions, exported component guards, ABI, target SDK and signatures.")
    print("NOT TESTED: SDK initialization, clone launch, data isolation or social applications.")


if __name__ == "__main__":
    main()
