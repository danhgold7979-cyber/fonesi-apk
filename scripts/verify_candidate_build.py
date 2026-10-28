#!/usr/bin/env python3
"""Audit the integrated candidate APK; does not assert native runtime compatibility."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile
from verify_main_build import suite
from verify_probe_apks import inspect_apk, ROOT, A


def main():
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    manifest, permissions, signer = inspect_apk(apk, "vn.fonesi.app")
    internal = "vn.fonesi.app.permission.INTERNAL_SDK"
    expected = {internal, "vn.fonesi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"} | {
        "android.permission." + name for name in ["INTERNET", "ACCESS_NETWORK_STATE", "WAKE_LOCK",
            "RECEIVE_BOOT_COMPLETED", "CAMERA", "RECORD_AUDIO", "FOREGROUND_SERVICE",
            "FOREGROUND_SERVICE_SPECIAL_USE", "POST_NOTIFICATIONS",
            "USE_BIOMETRIC", "USE_FINGERPRINT"]}
    if permissions != expected:
        raise RuntimeError(f"Unexpected candidate permissions: {sorted(permissions ^ expected)}")
    app = manifest.find("application")
    if app.get(A + "permission") != internal or app.get(A + "allowBackup") != "false" or app.get(A + "usesCleartextTraffic") != "false":
        raise RuntimeError("Candidate application protection differs from policy")
    definition = next(p for p in manifest.findall("permission") if p.get(A + "name") == internal)
    if definition.get(A + "protectionLevel") not in ["signature", "0x2"]:
        raise RuntimeError("Internal SDK guard must be signature-only")
    public = set()
    guarded = 0
    system_guards = {
        ("receiver", "androidx.work.impl.diagnostics.DiagnosticsReceiver"): "android.permission.DUMP",
        ("service", "com.fvbox.lib.client.proxy.ProxyJobService"): "android.permission.BIND_JOB_SERVICE",
        ("receiver", "androidx.profileinstaller.ProfileInstallReceiver"): "android.permission.DUMP",
        ("service", "androidx.work.impl.background.systemjob.SystemJobService"): "android.permission.BIND_JOB_SERVICE",
        ("receiver", "androidx.work.impl.background.systemjob.RescheduleReceiver"): "android.permission.RECEIVE_BOOT_COMPLETED",
    }
    for tag in ["activity", "activity-alias", "service", "provider", "receiver"]:
        for component in app.findall(tag):
            if component.get(A + "exported") != "true": continue
            name = component.get(A + "name")
            if name.startswith("."): name = manifest.get("package") + name
            permission = component.get(A + "permission", app.get(A + "permission"))
            if not permission: public.add((tag, name))
            elif permission == internal or permission == system_guards.get((tag, name)): guarded += 1
            else: raise RuntimeError(f"Unexpected exported candidate guard: {name}")
    if public != {("activity", "vn.fonesi.app.MainActivity"), ("activity", "vn.fonesi.app.shortcuts.CloneLaunchActivity")}:
        raise RuntimeError(f"Unexpected public SDK entry points: {sorted(public)}")
    route = next(c for c in app.findall("activity") if c.get(A + "name") in ["vn.fonesi.app.shortcuts.CloneLaunchActivity", ".shortcuts.CloneLaunchActivity"])
    if route.findall("intent-filter") or any(route.get(A + key) != value for key, value in {
        "noHistory": "false", "excludeFromRecents": "true", "taskAffinity": "",
    }.items()):
        raise RuntimeError("Candidate route must retain credential result without becoming browsable/public history")
    if manifest.get(A + "versionName") != "0.5.0-candidate": raise RuntimeError("Wrong candidate version")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if not any(name.endswith("/libfcore.so") for name in names): raise RuntimeError("Actual engine is missing")
        abis = {name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')}
        if abis != {"arm64-v8a"}: raise RuntimeError(f"Unexpected native ABIs: {abis}")
    lint = ET.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot().findall("issue")
    if any(i.get("severity") in ["Error", "Fatal"] for i in lint): raise RuntimeError("Candidate lint errors remain")
    core = suite(ROOT / "core/build/test-results/test")
    apk_runtime = suite(ROOT / "apk-runtime/build/test-results/test")
    simulated = suite(ROOT / "app/build/test-results/testDebugUnitTest")
    report = {"version": manifest.get(A + "versionName"), "engine_runtime_verified": False,
        "core": core, "apk_runtime": apk_runtime, "android_simulated": simulated, "permissions": sorted(permissions),
        "public_components": sorted(public), "guarded_exported_components": guarded,
        "lint_warnings": sum(i.get("severity") == "Warning" for i in lint),
        "apk_bytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "signer_sha256": signer}
    out = ROOT / "app/build/reports/candidate-verification.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print("PASS: integrated candidate APK, native SDK, manifest guards, signature, tests and lint.")
    print("NOT VERIFIED: native SDK execution, social apps, split imports, accounts or phone compatibility.")


if __name__ == "__main__":
    main()
