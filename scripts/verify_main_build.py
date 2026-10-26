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
    android = suite(ROOT / "app/build/test-results/testDebugUnitTest")
    lint = ET.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot()
    issues = lint.findall("issue")
    if any(i.get("severity") in ["Error", "Fatal"] for i in issues):
        raise RuntimeError("Android lint has unresolved errors")
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    manifest, permissions, _ = inspect_apk(apk, "vn.fonesi.app")
    if permissions != {"vn.fonesi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}:
        raise RuntimeError("Unexpected permissions in the main APK")
    if manifest.find("application").get(A + "allowBackup") != "false":
        raise RuntimeError("Unexpected backup policy")
    with zipfile.ZipFile(apk) as archive:
        if any("libfcore.so" in name for name in archive.namelist()):
            raise RuntimeError("Experimental SDK was unexpectedly bundled into the main app")
    report = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "scope": "Cloud logic, filesystem, native SQLite under Robolectric, build/lint and static APK verification only",
        "engine_runtime_verified": False,
        "core": core, "android_simulated": android,
        "lint_warnings": sum(i.get("severity") == "Warning" for i in issues),
        "package": manifest.get("package"),
        "version_name": manifest.get(A + "versionName"),
        "version_code": manifest.get(A + "versionCode"),
        "apk_bytes": apk.stat().st_size,
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "permissions": sorted(permissions),
        "signature_verified": True,
    }
    output = ROOT / "app/build/reports/cloud-verification.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    passed = sum(group["tests"] - group["skipped"] for group in [core, android])
    print(f"PASS: {passed} tests; build artifacts, lint and APK signature/permissions verified.")
    print("NOT VERIFIED: social apps, account isolation, real updates or phone behavior.")


if __name__ == "__main__":
    main()
