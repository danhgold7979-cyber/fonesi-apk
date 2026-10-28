#!/usr/bin/env python3
"""Run only the signed E11 synthetic code-image probe on a new, owned AOSP emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import uuid

from prepare_native_emulator import IMAGES, environment
from prepare_source_engine import ROOT, TOOLS

PROBE_PACKAGE = "vn.fonesi.nativeprobe"
PAYLOAD_PACKAGE = "vn.fonesi.payload"
PROBE_SHA256 = "4ab18e6408546fbc4effd1efad24c4fdf044212e4f6d26456089cf817fe5551c"
PAYLOAD_SHA256 = "c8296bb1d62af7186081e2180a22a721318ad4b0c3bc06101350aa69f0fbd114"
UUID_NAME = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.json$")
SDK_IMAGE = IMAGES["aosp34"]


def sha256(file):
    digest = hashlib.sha256()
    with file.open("rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def choose_port():
    for port in range(5580, 5650, 2):
        with socket.socket() as even, socket.socket() as odd:
            try:
                even.bind(("127.0.0.1", port))
                odd.bind(("127.0.0.1", port + 1))
                return port
            except OSError:
                continue
    raise RuntimeError("No unused dedicated AVD port available")


def main():
    parser = argparse.ArgumentParser(description="Synthetic Android code-image proof (no account data)")
    parser.add_argument("--apk-dir", type=Path, required=True)
    parser.add_argument("--reports", type=Path, default=ROOT / ".local/e11-emulator")
    parser.add_argument("--boot-timeout", type=int, default=480)
    parser.add_argument("--probe-timeout", type=int, default=180)
    args = parser.parse_args()
    if args.boot_timeout not in range(30, 601) or args.probe_timeout not in range(15, 301):
        parser.error("Timeout out of bounded range")
    env = environment()
    sdk = Path(env["ANDROID_HOME"])
    adb = sdk / "platform-tools/adb"
    manager = sdk / "cmdline-tools/19.0/bin/avdmanager"
    emulator_binary = sdk / "emulator/emulator"
    reports = args.reports.resolve()
    reports.mkdir(parents=True, exist_ok=True)
    attempt = str(uuid.uuid4())
    evidence = {
        "attempt_id": attempt,
        "source_commit": "a9aa5a8d53b0c82eabcc071fd0e0fa19fdcc612c",
        "result": "RUNNING",
        "android_boot_verified": False,
        "android_code_image_verified": False,
        "social_apps_tested": False,
        "samsung_or_physical_device_tested": False,
    }
    evidence_file = reports / "e11-evidence.json"

    def save():
        evidence_file.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    def run(cmd, timeout=45, check=True, input_data=None):
        return subprocess.run(
            [str(arg) for arg in cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            env=env, timeout=timeout, check=check, text=True, input=input_data,
        )

    serial = None
    process = None
    avd = None
    stage = "verify APK input"
    save()
    try:
        def unique_artifact(name):
            candidates = [p for p in args.apk_dir.rglob(name) if p.is_file() and not p.is_symlink()]
            if len(candidates) != 1:
                raise RuntimeError("Expected exactly one verified artifact: " + name)
            return candidates[0]

        probe_apk = unique_artifact("native-runtime-probe-debug.apk")
        payload_apk = unique_artifact("probe-payload-debug.apk")
        for label, file, expected in (
            ("probe", probe_apk, PROBE_SHA256), ("payload", payload_apk, PAYLOAD_SHA256)
        ):
            if not file.is_file() or file.is_symlink() or sha256(file) != expected:
                raise RuntimeError(label + " APK not present or hash differs from green CI artifact")
        evidence["probe_sha256"] = PROBE_SHA256
        evidence["payload_sha256"] = PAYLOAD_SHA256
        save()

        stage = "verify owned SDK and hardware acceleration"
        image_path = sdk.joinpath(*SDK_IMAGE.split(";"), "system.img")
        if not adb.is_file() or not emulator_binary.is_file() or not image_path.is_file():
            raise RuntimeError("Pinned emulator, AOSP image or adb is missing")
        if not (Path("/dev/kvm").exists() and os.access("/dev/kvm", os.R_OK | os.W_OK)):
            evidence["result"] = "BLOCKED"
            raise RuntimeError("GitHub runner has no accessible KVM; Android boot not attempted")
        evidence["emulator_acceleration"] = "KVM"

        stage = "create isolated emulator"
        avd = "fonesi_e11_" + attempt.split("-")[0]
        port = choose_port()
        serial = "emulator-" + str(port)
        evidence["owned_avd"] = avd
        evidence["owned_serial"] = serial
        save()
        created = run([manager, "create", "avd", "--name", avd,
                       "--package", SDK_IMAGE, "--device", "pixel_6"],
                      timeout=90, check=False, input_data="no\\n")
        if created.returncode:
            (reports / "avd-create.log").write_text(created.stderr[-8000:] + created.stdout[-4000:])
            raise RuntimeError("AOSP AVD creation failed")

        stage = "boot isolated Android 34"
        output_log = (reports / "emulator-startup.log").open("w")
        process = subprocess.Popen(
            [str(emulator_binary), "-avd", avd, "-port", str(port),
             "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot", "-no-metrics",
             "-accel", "on", "-gpu", "swiftshader_indirect", "-memory", "2048",
             "-cores", "2", "-skin", "480x800", "-dpi-device", "240"],
            env=env, stdout=output_log, stderr=subprocess.STDOUT, start_new_session=True,
        )
        output_log.close()
        evidence["owned_emulator_pid"] = process.pid
        save()

        def device(*argv, timeout=25, check=True):
            if serial is None or not serial.startswith("emulator-"):
                raise RuntimeError("No owned ADB serial")
            return run([adb, "-s", serial, *argv], timeout=timeout, check=check)

        deadline = time.monotonic() + args.boot_timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError("Owned emulator exited before Android boot")
            try:
                boot = device("shell", "getprop", "sys.boot_completed", timeout=8, check=False)
                if boot.returncode == 0 and boot.stdout.strip() == "1":
                    break
            except subprocess.TimeoutExpired:
                pass
            time.sleep(3)
        else:
            raise RuntimeError("Bounded Android boot timeout")

        if device("shell", "getprop", "ro.kernel.qemu").stdout.strip() != "1":
            raise RuntimeError("Serial does not identify an emulator")
        if device("shell", "getprop", "ro.product.cpu.abi").stdout.strip() != "x86_64":
            raise RuntimeError("Unexpected emulator ABI")
        for service in ("package", "activity"):
            if not device("shell", "service", "check", service).stdout.strip().endswith(": found"):
                raise RuntimeError("Android service not ready: " + service)
        evidence["android_boot_verified"] = True
        save()

        stage = "install two frozen synthetic APKs"
        for apk in (payload_apk, probe_apk):
            installed = device("install", "-r", apk, timeout=90, check=False)
            if installed.returncode or "Success" not in installed.stdout:
                raise RuntimeError("Synthetic APK install failed")
        evidence["android_apks_installed"] = True
        save()

        stage = "launch synthetic guest-code activity"
        result = device("shell", "am", "start", "-W", "-n",
                        PROBE_PACKAGE + "/.GuestCodeProbeActivity", timeout=90, check=False)
        if result.returncode:
            raise RuntimeError("Guest code probe Activity dispatch failed")
        # Brand-new AVD/app data: no previously saved run IDs or receipts may exist.
        stage = "read exact dedicated process receipt"
        deadline = time.monotonic() + args.probe_timeout
        report = None
        while time.monotonic() < deadline:
            listing = device("exec-out", "run-as", PROBE_PACKAGE, "ls", "-1",
                             "files/guest-code-probe", timeout=12, check=False)
            matches = sorted(line.strip() for line in listing.stdout.splitlines()
                             if UUID_NAME.fullmatch(line.strip()))
            if len(matches) > 1:
                raise RuntimeError("Multiple receipts in freshly installed isolated probe")
            if matches:
                name = matches[0]
                raw = device("exec-out", "run-as", PROBE_PACKAGE, "cat",
                             "files/guest-code-probe/" + name, timeout=12, check=False)
                if raw.returncode == 0 and len(raw.stdout) <= 16384:
                    try:
                        found = json.loads(raw.stdout)
                        if found.get("complete") is True:
                            if found.get("run_id") != name.removesuffix(".json"):
                                raise RuntimeError("Receipt run UUID does not match its file")
                            if str(uuid.UUID(found["run_id"])) != found["run_id"]:
                                raise RuntimeError("Receipt run UUID is not canonical")
                            report = found
                            break
                    except json.JSONDecodeError:
                        pass
            time.sleep(2)
        if report is None:
            raise RuntimeError("No complete Android guest-code receipt before timeout")
        (reports / "guest-code-receipt.json").write_text(json.dumps(report, indent=2) + "\n")
        expected_true = ("complete", "passed", "signed_private_cluster", "dex_class_loader",
                         "private_resources", "activation_pending")
        expected_false = ("guest_context_executed", "guest_activity_executed",
                          "binder_provider_tested", "social_accounts_tested")
        if not all(report.get(key) is True for key in expected_true) or not all(
            report.get(key) is False for key in expected_false
        ):
            raise RuntimeError("Android guest-code execution did not satisfy required assertions")
        evidence["android_code_image_verified"] = True
        evidence["result"] = "PASS_E11_SYNTHETIC_ONLY"
        save()
        print("PASS: Android 34 AVD loaded private signed payload DEX and resources. "
              "Guest Activity, Binder and account isolation remain unverified.", flush=True)
    except BaseException as failure:
        evidence["failure_stage"] = stage
        evidence["failure_type"] = type(failure).__name__
        evidence["failure_reason"] = str(failure)[:500]
        if evidence.get("result") != "BLOCKED":
            evidence["result"] = "FAIL"
        save()
        raise
    finally:
        # Stop only the process group started by this runner; never touch physical devices.
        if process is not None and process.poll() is None:
            try:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=15)
            except (ProcessLookupError, subprocess.TimeoutExpired):
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
        evidence["emulator_stopped"] = process is None or process.poll() is not None
        save()


if __name__ == "__main__":
    main()
