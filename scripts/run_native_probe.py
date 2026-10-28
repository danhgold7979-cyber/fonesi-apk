#!/usr/bin/env python3
"""Boot a project-owned software AVD and run only the synthetic native probe on it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile
from prepare_native_emulator import IMAGES, environment
from prepare_source_engine import ROOT, TOOLS
from verify_probe_apks import inspect_apk, A

PACKAGE = "vn.fonesi.nativeprobe"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep-emulator", action="store_true")
    parser.add_argument("--reuse", action="store_true")
    parser.add_argument("--restart", action="store_true")
    parser.add_argument("--image", choices=IMAGES, default="aosp34")
    parser.add_argument("--audit-only", action="store_true")
    args = parser.parse_args()
    if args.reuse and args.restart: parser.error("Choose reuse or restart, not both")
    image = IMAGES[args.image]
    env = environment()
    sdk = Path(env["ANDROID_HOME"])
    reports = ROOT / "native-runtime-probe/build/reports/runtime"
    reports.mkdir(parents=True, exist_ok=True)
    if (reports / "probe-runner-status.json").exists():
        previous = json.loads((reports / "probe-runner-status.json").read_text())
        # Preserve earlier outcomes when choosing a different image or rerunning a fixed probe.
        (reports / ("previous-" + uuid.uuid4().hex + ".json")).write_text(json.dumps(previous, indent=2) + "\n")
    attempt_id = str(uuid.uuid4())
    source_apk = ROOT / "native-runtime-probe/build/outputs/apk/debug/native-runtime-probe-debug.apk"
    apk = reports / f"probe-input-{attempt_id}.apk"
    with apk.open("xb") as staged:
        staged.write(source_apk.read_bytes())
    apk_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
    manifest, permissions, signer = inspect_apk(apk, PACKAGE)
    assert not permissions, permissions
    assert manifest.find("uses-sdk").get(A + "minSdkVersion") == "26"
    app = manifest.find("application")
    assert app.get(A + "allowBackup") == "false" and app.get(A + "usesCleartextTraffic") == "false"
    # Keep the legacy A/B probe audit compatible with the explicitly declared
    # E11 code-image probe, without permitting arbitrary exported components.
    def component(name):
        assert name, "Component name missing"
        return PACKAGE + name if name.startswith(".") else name
    activities = {component(a.get(A + "name")): a for a in app.findall("activity")}
    assert set(activities) == {
        PACKAGE + ".MainActivity", PACKAGE + ".GuestCodeProbeActivity",
        PACKAGE + ".SyntheticChannelProbeActivity", PACKAGE + ".SplitCodeProbeActivity",
    }, "Unexpected exported synthetic probe Activity"
    assert all(a.get(A + "exported") == "true" for a in activities.values())
    services = {component(s.get(A + "name")): s for s in app.findall("service")}
    expected_services = {
        PACKAGE + ".ProbeService$A": ":fonesi_guest_a",
        PACKAGE + ".ProbeService$B": ":fonesi_guest_b",
        PACKAGE + ".GuestCodeProbeService": ":fonesi_guest_loader",
        PACKAGE + ".SyntheticGuestChannelService": ":fonesi_guest_channel",
        PACKAGE + ".SplitCodeProbeService": ":fonesi_guest_split",
    }
    assert set(services) == set(expected_services), "Unexpected native probe component"
    assert all(services[name].get(A + "exported") == "false" and
               services[name].get(A + "process") == expected
               for name, expected in expected_services.items()), "Guest services must be internal and isolated by name"
    with zipfile.ZipFile(apk) as z:
        assert [n for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")] == ["lib/x86_64/libfonesi_runtime.so"]
        blob = z.read("lib/x86_64/libfonesi_runtime.so")
        assert blob[:6] == b"\x7fELF\x02\x01"
        header = struct.unpack_from("<16sHHIQQQIHHHHHH", blob)
        assert header[1:3] == (3, 62)
        segments = [struct.unpack_from("<IIQQQQQQ", blob, header[5] + index * header[9]) for index in range(header[10])]
        loads = [p for p in segments if p[0] == 1]
        assert loads and all(p[7] >= 16384 and (p[3] - p[2]) % 16384 == 0 and p[1] & 3 != 3 for p in loads)
        for license_name in ("Dobby.txt", "TINYSTL.txt", "Fonesi-source-runtime.md", "apksig.md"):
            assert len(z.read("assets/licenses/" + license_name)) > 100
    lint = ET.parse(ROOT / "native-runtime-probe/build/reports/lint-results-debug.xml").getroot().findall("issue")
    assert not any(i.get("severity") in ("Error", "Fatal") for i in lint)
    static = {"apk_sha256": apk_sha256, "apk_bytes": apk.stat().st_size, "signer_sha256": signer,
              "permissions": [], "abi": "x86_64", "elf_sha256": hashlib.sha256(blob).hexdigest(),
              "load_alignments": [p[7] for p in loads], "lint_errors": 0,
              "lint_warnings": sum(i.get("severity") == "Warning" for i in lint), "android_execution_verified": False}
    (reports / "native-probe-static.json").write_text(json.dumps(static, indent=2) + "\n")
    if args.audit_only:
        print("PASS: signed synthetic probe APK, no permissions, exact components/ABI, ELF alignment, licenses and lint.")
        print("NOT VERIFIED: Android execution; audit-only does not start an emulator or phone.")
        return
    adb = sdk / "platform-tools/adb"
    ownership = reports / "emulator-ownership.json"
    emulator = None
    serial = None
    owned = None
    runner_status = {"attempt_id": attempt_id, "apk_sha256": apk_sha256, "running": True,
                     "image": image, "apk_static_audit_passed": True, "boot_completed": False, "android_native_sqlite_verified": False}
    (reports / "probe-runner-status.json").write_text(json.dumps(runner_status, indent=2) + "\n")

    def command(arguments, timeout=25, check=True):
        return subprocess.run([str(a) for a in arguments], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=check)

    def device(*arguments, timeout=25, check=True):
        assert serial and serial.startswith("emulator-")
        return command([adb, "-s", serial, *arguments], timeout, check)

    try:
        if args.reuse:
            owned = json.loads(ownership.read_text())
            serial = owned["serial"]
            assert owned["avd"].startswith("fonesi_native_")
            assert owned.get("image") == image, "Owned AVD uses a different image"
            assert device("emu", "avd", "name").stdout.decode().splitlines()[0] == owned["avd"]
        else:
            if args.restart:
                owned = json.loads(ownership.read_text())
                assert owned["avd"].startswith("fonesi_native_")
                assert owned.get("image") == image, "Owned AVD uses a different image"
                process = Path(f"/proc/{owned['pid']}/stat")
                assert not process.exists() or process.read_text().rpartition(")")[2].split()[0] == "Z", "Previous owned emulator must be stopped first"
                name = owned["avd"]
            else:
                name = "fonesi_native_" + uuid.uuid4().hex[:12]
            port = None
            for candidate in range(5580, 5660, 2):
                with socket.socket() as first, socket.socket() as second:
                    try: first.bind(("127.0.0.1", candidate)); second.bind(("127.0.0.1", candidate + 1)); port = candidate; break
                    except OSError: continue
            if port is None: raise RuntimeError("No free dedicated emulator port")
            serial = f"emulator-{port}"
            if not args.restart:
                manager = sdk / "cmdline-tools/19.0/bin/avdmanager"
                result = subprocess.run([str(manager), "create", "avd", "--name", name, "--package", image, "--device", "pixel_6"],
                    input="no\n", text=True, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                (reports / "avd-create.log").write_text(result.stdout + result.stderr)
                if result.returncode: raise RuntimeError("AVD creation failed; see avd-create.log")
            with (reports / ("emulator-warm.log" if args.restart else "emulator.log")).open("w") as log:
                emulator = subprocess.Popen([str(sdk / "emulator/emulator"), "-avd", name, "-port", str(port),
                    "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot", "-no-metrics", "-accel", "off",
                    "-gpu", "swiftshader_indirect", "-memory", "2048", "-cores", "2", "-skin", "480x800", "-dpi-device", "240"], env=env,
                    stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            owned = {"avd": name, "serial": serial, "pid": emulator.pid, "image": image, "synthetic_only": True}
            ownership.write_text(json.dumps(owned, indent=2) + "\n")
            print(f"Starting project-owned software emulator {serial}; no phone is targeted.", flush=True)
            deadline = time.monotonic() + 480
            last_notice = 0
            while True:
                if emulator.poll() is not None: raise RuntimeError("Emulator exited before boot; see emulator.log")
                try:
                    if device("shell", "getprop", "sys.boot_completed", timeout=8, check=False).stdout.strip() == b"1": break
                except subprocess.TimeoutExpired: pass
                if time.monotonic() >= deadline: raise RuntimeError("Software emulator boot timeout (480 seconds)")
                if time.monotonic() - last_notice > 30:
                    print("Waiting for Android boot; native execution is not yet verified.", flush=True); last_notice = time.monotonic()
                time.sleep(2)
        assert device("shell", "getprop", "ro.kernel.qemu").stdout.strip() == b"1", "Not an emulator"
        runner_status["boot_completed"] = device("shell", "getprop", "sys.boot_completed").stdout.strip() == b"1"
        assert device("shell", "getprop", "ro.product.cpu.abi").stdout.strip() == b"x86_64"
        for service in ("package", "activity"):
            assert device("shell", "service", "check", service).stdout.strip().endswith(b": found"), "Android framework service not ready"
        device("shell", "input", "keyevent", "82")
        assert hashlib.sha256(apk.read_bytes()).hexdigest() == apk_sha256, "Audited probe APK changed"
        device("install", "-r", apk, timeout=90)
        device("shell", "am", "force-stop", PACKAGE)
        run_id = str(uuid.uuid4())
        device("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity", "--es", "run_id", run_id, timeout=90)
        print("Probe installed/launched on the owned AVD; waiting for actual JNI/file/SQLite results.", flush=True)
        deadline = time.monotonic() + 180
        while True:
            output = device("exec-out", "run-as", PACKAGE, "cat", "files/native-probe/latest.json", check=False).stdout
            if output:
                try:
                    result = json.loads(output)
                    if result.get("complete") is True and result.get("run_id") == run_id: break
                except json.JSONDecodeError: pass
            if time.monotonic() >= deadline: raise RuntimeError("Native probe report timeout; a crash is not a pass")
            time.sleep(2)
        result.update({"apk_sha256": apk_sha256, "signer_sha256": signer,
                       "serial": serial, "image": image, "device_class": "software-emulator-x86_64", "samsung_arm64_verified": False})
        (reports / "native-probe-result.json").write_text(json.dumps(result, indent=2) + "\n")
        if result.get("passed") is not True: raise RuntimeError("Android native/SQLite probe failed; see native-probe-result.json")
        runner_status["android_native_sqlite_verified"] = True
        print("PASS: actual Android JNI/file/SQLite synthetic checks in independent processes; original data preserved.")
        print("NOT VERIFIED: full APK guest loading, social accounts or Samsung ARM64.")
    except Exception as failure:
        runner_status["error"] = type(failure).__name__ + ": " + str(failure)
        raise
    finally:
        runner_status["running"] = False
        (reports / "probe-runner-status.json").write_text(json.dumps(runner_status, indent=2) + "\n")
        if serial:
            try:
                logs = device("logcat", "-d", "-s", "FonesiNativeProbe:E", "AndroidRuntime:E", "libc:F", "DEBUG:F", "*:S", check=False).stdout
                (reports / "probe-logcat.txt").write_bytes(logs)
            except (subprocess.SubprocessError, OSError): pass
        if not args.keep_emulator:
            if emulator is not None and emulator.poll() is None:
                emulator.terminate()
                try: emulator.wait(timeout=15)
                except subprocess.TimeoutExpired: emulator.kill(); emulator.wait(timeout=10)
            elif args.reuse and serial:
                device("emu", "kill", check=False)


if __name__ == "__main__":
    main()
