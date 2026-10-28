#!/usr/bin/env python3
"""Summarize real Gradle outputs; never treats zero tests or an unverified APK as ready."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

from verify_probe_apks import inspect_apk, ROOT, A


def suite(folder):
    files = sorted(folder.glob("TEST-*.xml"))
    if not files:
        raise RuntimeError(f"Missing test reports: {folder}")
    total = {key: 0 for key in ["tests", "failures", "errors", "skipped"]}
    for file in files:
        xml = ET.parse(file).getroot()
        for key in total:
            total[key] += int(xml.get(key, "0"))
    if total["failures"] or total["errors"] or total["tests"] - total["skipped"] <= 0:
        raise RuntimeError(f"Test results do not demonstrate a passing run: {total}")
    return total


def main():
    core = suite(ROOT / "core/build/test-results/test")
    apk_runtime = suite(ROOT / "apk-runtime/build/test-results/test")
    android = suite(ROOT / "app/build/test-results/testDebugUnitTest")
    lint = ET.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot()
    issues = lint.findall("issue")
    if any(i.get("severity") in ["Error", "Fatal"] for i in issues):
        raise RuntimeError("Android lint has unresolved errors")
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    manifest, permissions, _ = inspect_apk(apk, "vn.fonesi.app")
    expected_permissions = {
        "vn.fonesi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT",
        "android.permission.ACCESS_NETWORK_STATE", "android.permission.WAKE_LOCK",
        "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.FOREGROUND_SERVICE",
    }
    if permissions != expected_permissions:
        raise RuntimeError(f"Unexpected permissions in the main APK: {sorted(permissions ^ expected_permissions)}")
    application = manifest.find("application")
    if application.get(A + "allowBackup") != "false":
        raise RuntimeError("Unexpected backup policy")
    public_components = set()
    route = None
    system_guards = {
        ("service", "androidx.work.impl.background.systemjob.SystemJobService"): "android.permission.BIND_JOB_SERVICE",
        ("receiver", "androidx.work.impl.background.systemjob.RescheduleReceiver"): "android.permission.RECEIVE_BOOT_COMPLETED",
    }
    for tag in ["activity", "activity-alias", "service", "receiver", "provider"]:
        for component in application.findall(tag):
            if component.get(A + "exported") != "true":
                continue
            name = component.get(A + "name")
            if name.startswith("."):
                name = manifest.get("package") + name
            permission = component.get(A + "permission", application.get(A + "permission"))
            if (tag, name, permission) == ("receiver", "androidx.profileinstaller.ProfileInstallReceiver", "android.permission.DUMP"):
                continue
            if permission:
                if system_guards.get((tag, name)) == permission:
                    continue
                raise RuntimeError(f"Unexpected exported component guard: {name}")
            public_components.add((tag, name))
            if name == "vn.fonesi.app.shortcuts.CloneLaunchActivity":
                route = component
    if public_components != {("activity", "vn.fonesi.app.MainActivity"), ("activity", "vn.fonesi.app.shortcuts.CloneLaunchActivity")}:
        raise RuntimeError(f"Unexpected unguarded exported main-app components: {sorted(public_components)}")
    if route is None or route.findall("intent-filter") or any(route.get(A + key) != value for key, value in {
        "noHistory": "false", "excludeFromRecents": "true", "taskAffinity": "",
    }.items()):
        raise RuntimeError("Shortcut destination differs from the reviewed explicit-only routing policy")
    with zipfile.ZipFile(apk) as archive:
        if any("libfcore.so" in name for name in archive.namelist()):
            raise RuntimeError("Experimental SDK was unexpectedly bundled into the main app")
    report = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "Cloud logic, filesystem, native SQLite under Robolectric, build/lint and static APK verification only",
        "engine_runtime_verified": False,
        "core": core, "apk_runtime": apk_runtime, "android_simulated": android,
        "lint_warnings": sum(i.get("severity") == "Warning" for i in issues),
        "package": manifest.get("package"),
        "version_name": manifest.get(A + "versionName"),
        "version_code": manifest.get(A + "versionCode"),
        "apk_bytes": apk.stat().st_size,
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "permissions": sorted(permissions),
        "signature_verified": True,
        "unguarded_exported_components": sorted(public_components),
    }
    output = ROOT / "app/build/reports/cloud-verification.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    passed = sum(group["tests"] - group["skipped"] for group in [core, apk_runtime, android])
    print(f"PASS: {passed} tests; build artifacts, lint and APK signature/permissions verified.")
    print("NOT VERIFIED: social apps, account isolation, real updates or phone behavior.")


if __name__ == "__main__":
    main()
