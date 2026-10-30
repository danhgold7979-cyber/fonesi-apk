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
SPLIT_PACKAGE = "vn.fonesi.splitfixture"
PROBE_SHA256 = "4ab18e6408546fbc4effd1efad24c4fdf044212e4f6d26456089cf817fe5551c"
PAYLOAD_SHA256 = "c8296bb1d62af7186081e2180a22a721318ad4b0c3bc06101350aa69f0fbd114"
UUID_NAME = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.json$")
SDK_IMAGE = IMAGES["aosp34"]

UI_CONFIGURATION_COMMAND = """set -eu
printf '%s\\n' '[wm-size]'
wm size
printf '%s\\n' '[wm-density]'
wm density
printf 'font-scale=%s\\n' "$(settings get system font_scale)"
printf 'user-rotation=%s\\n' "$(settings get system user_rotation)"
printf 'locale=%s\\n' "$(getprop persist.sys.locale)"
printf 'language=%s\\n' "$(getprop persist.sys.language)"
printf 'country=%s\\n' "$(getprop persist.sys.country)"
"""
UI_CONFIGURATION_READ_TIMEOUT_SECONDS = 20
UI_CONFIGURATION_STABILITY_TIMEOUT_SECONDS = 120
UI_CONFIGURATION_SAMPLE_INTERVAL_SECONDS = 2
UI_CONFIGURATION_REQUIRED_STABLE_TRANSITIONS = 3


def read_ui_configuration(device, timeout_seconds=UI_CONFIGURATION_READ_TIMEOUT_SECONDS):
    """Read and validate the full UI/display/locale fingerprint through one ADB shell command."""
    if not isinstance(timeout_seconds, int) or not 1 <= timeout_seconds <= UI_CONFIGURATION_READ_TIMEOUT_SECONDS:
        raise ValueError("UI configuration read timeout must be between 1 and 20 seconds")
    result = device(
        "shell", UI_CONFIGURATION_COMMAND,
        timeout=timeout_seconds, check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("Cannot read Android UI configuration fingerprint")
    snapshot = tuple(line.strip() for line in result.stdout.splitlines() if line.strip())
    if not snapshot:
        raise RuntimeError("Android UI configuration fingerprint is empty")

    # adb shell accepts one remote command string. Do not wrap it in "sh -c":
    # adb's remote-shell argument handling can otherwise execute "set" alone and
    # contaminate the fingerprint with volatile shell environment variables.
    if snapshot[0] != "[wm-size]":
        raise RuntimeError("Unexpected Android UI fingerprint prefix; shell environment or command output leaked")
    density_markers = [i for i, line in enumerate(snapshot) if line == "[wm-density]"]
    if len(density_markers) != 1:
        raise RuntimeError("Android UI fingerprint must contain exactly one density marker")
    density_index = density_markers[0]
    size_lines = snapshot[1:density_index]
    if not size_lines or any(
        not (line.startswith("Physical size:") or line.startswith("Override size:"))
        for line in size_lines
    ):
        raise RuntimeError("Android UI fingerprint contains invalid display-size output")

    scalar_labels = ("font-scale=", "user-rotation=", "locale=", "language=", "country=")
    scalar_positions = []
    for label in scalar_labels:
        matches = [i for i, line in enumerate(snapshot) if line.startswith(label)]
        if len(matches) != 1:
            raise RuntimeError("Android UI fingerprint must contain exactly one " + label + " value")
        scalar_positions.append(matches[0])
    density_lines = snapshot[density_index + 1:scalar_positions[0]]
    if not density_lines or any(
        not (line.startswith("Physical density:") or line.startswith("Override density:"))
        for line in density_lines
    ):
        raise RuntimeError("Android UI fingerprint contains invalid density output")
    if scalar_positions != sorted(scalar_positions):
        raise RuntimeError("Android UI fingerprint scalar values are missing or out of order")
    if len(snapshot) != scalar_positions[-1] + 1:
        raise RuntimeError("Android UI fingerprint contains unexpected trailing output")
    return snapshot


def synthetic_install_failure(apk, result):
    """Keep bounded ADB install diagnostics so a failed emulator install is actionable."""
    stdout = str(getattr(result, "stdout", "") or "")[-2000:]
    stderr = str(getattr(result, "stderr", "") or "")[-2000:]
    return (
        "Synthetic APK install failed for " + Path(apk).name +
        " (exit=" + str(getattr(result, "returncode", "unknown")) + ")" +
        "; stdout=" + (stdout.strip() or "<empty>") +
        "; stderr=" + (stderr.strip() or "<empty>")
    )


def software_emulation_proof_allowed(proof_level):
    """Only bounded synthetic E77/E78/E79 proofs may use software AVD."""
    return proof_level in ("e77", "e78", "e79", "e80")


def validate_e78_receipt(report, source_commit, probe_apk_sha256, payload_apk_sha256):
    """Strict exact-source receipt validation for synthetic Application attach and A/B data only."""
    if not isinstance(report, dict):
        raise RuntimeError("E78 receipt must be a JSON object")
    required_true = (
        "complete", "passed", "real_host_activity_lifecycle_verified",
        "signed_private_guest_view_rendered", "private_guest_view_attached_to_window",
        "private_guest_lifecycle_relay_verified", "signed_private_resources_verified",
        "signed_private_image_rechecked", "same_android_uid",
        "private_activity_class_preflight_verified",
        "private_activity_constructor_preflight_verified",
        "private_application_class_preflight_verified",
        "synthetic_guest_application_attached",
        "synthetic_guest_application_on_create_verified",
        "synthetic_guest_application_on_main_thread",
        "synthetic_guest_application_instances_distinct",
        "synthetic_application_on_create_once_per_scope",
        "synthetic_scope_a_preferences_file_sqlite_verified",
        "synthetic_scope_b_preferences_file_sqlite_verified",
        "synthetic_scope_persistence_after_context_reconstruction_verified",
        "synthetic_scope_cross_readback_isolated",
    )
    required_false = (
        "guest_application_registered_with_activity_thread",
        "guest_application_registered_with_package_manager",
        "full_guest_application_lifecycle_verified",
        "actual_guest_activity_attached",
        "real_guest_activity_registered",
        "third_party_component_executed",
        "third_party_guest_components_tested",
        "kernel_uid_isolation",
        "social_accounts_tested",
        "production_clone_accepted",
    )
    missing_true = [key for key in required_true if report.get(key) is not True]
    missing_false = [key for key in required_false if report.get(key) is not False]
    if missing_true or missing_false:
        raise RuntimeError(
            "E78 receipt assertions failed: true=" + ",".join(missing_true) +
            "; false=" + ",".join(missing_false)
        )
    if report.get("proof_level") != "e78":
        raise RuntimeError("E78 receipt proof level mismatch")
    if report.get("source_commit") != source_commit:
        raise RuntimeError("E78 receipt source SHA differs from checked-out source")
    if report.get("probe_apk_sha256") != probe_apk_sha256:
        raise RuntimeError("E78 receipt host probe APK SHA-256 mismatch")
    if report.get("payload_apk_sha256") != payload_apk_sha256:
        raise RuntimeError("E78 receipt signed payload APK SHA-256 mismatch")
    if report.get("synthetic_application_class_name") != PAYLOAD_PACKAGE + ".SyntheticGuestApplication":
        raise RuntimeError("E78 receipt signed Application class identity mismatch")
    if report.get("api") != 34 or report.get("process_name") != PROBE_PACKAGE + ":fonesi_guest_ui":
        raise RuntimeError("E78 receipt Android API/process identity mismatch")
    if type(report.get("pid")) is not int or report["pid"] <= 0:
        raise RuntimeError("E78 receipt PID is invalid")
    if type(report.get("uid")) is not int or report["uid"] < 0:
        raise RuntimeError("E78 receipt UID is invalid")
    run_id = report.get("run_id")
    if not isinstance(run_id, str) or str(uuid.UUID(run_id)) != run_id:
        raise RuntimeError("E78 receipt run UUID is not canonical")
    scope_a, scope_b = report.get("synthetic_scope_a_id"), report.get("synthetic_scope_b_id")
    if not isinstance(scope_a, str) or not isinstance(scope_b, str):
        raise RuntimeError("E78 receipt lacks both synthetic scope UUIDs")
    if str(uuid.UUID(scope_a)) != scope_a or str(uuid.UUID(scope_b)) != scope_b or scope_a == scope_b:
        raise RuntimeError("E78 receipt scope IDs must be distinct canonical UUIDs")
    if report.get("lifecycle_transcript") != "CREATE,START,RESUME,PAUSE,STOP,DESTROY":
        raise RuntimeError("E78 host Activity lifecycle transcript is incomplete")
    return scope_a, scope_b


def _validate_e79_stage(report, stage, source_commit, probe_apk_sha256, payload_apk_sha256, expected_proof="e79"):
    if not isinstance(report, dict):
        raise RuntimeError("E79 stage receipt must be a JSON object")
    required_true = (
        "complete", "passed", "real_host_activity_lifecycle_verified",
        "signed_private_guest_view_rendered", "private_guest_view_attached_to_window",
        "private_guest_lifecycle_relay_verified", "signed_private_resources_verified",
        "signed_private_image_rechecked", "same_android_uid",
        "private_activity_class_preflight_verified",
        "private_activity_constructor_preflight_verified",
        "private_application_class_preflight_verified",
        "synthetic_guest_application_attached", "synthetic_guest_application_on_create_verified",
        "synthetic_guest_application_on_main_thread", "synthetic_guest_application_instances_distinct",
        "synthetic_application_on_create_once_per_scope",
        "synthetic_scope_a_preferences_file_sqlite_verified",
        "synthetic_scope_b_preferences_file_sqlite_verified",
        "synthetic_scope_persistence_after_context_reconstruction_verified",
        "synthetic_scope_cross_readback_isolated",
    )
    required_false = (
        "guest_application_registered_with_activity_thread",
        "guest_application_registered_with_package_manager",
        "full_guest_application_lifecycle_verified", "actual_guest_activity_attached",
        "real_guest_activity_registered", "third_party_component_executed",
        "third_party_guest_components_tested", "kernel_uid_isolation",
        "social_accounts_tested", "production_clone_accepted",
    )
    missing_true = [key for key in required_true if report.get(key) is not True]
    missing_false = [key for key in required_false if report.get(key) is not False]
    if missing_true or missing_false:
        raise RuntimeError("E79 stage assertions failed: true=" + ",".join(missing_true) +
                           "; false=" + ",".join(missing_false))
    if report.get("proof_level") != expected_proof or report.get("e79_stage") != stage:
        raise RuntimeError("E79 proof/stage identity mismatch")
    if report.get("source_commit") != source_commit:
        raise RuntimeError("E79 receipt source SHA mismatch")
    if report.get("probe_apk_sha256") != probe_apk_sha256:
        raise RuntimeError("E79 host APK SHA-256 mismatch")
    if report.get("payload_apk_sha256") != payload_apk_sha256:
        raise RuntimeError("E79 payload APK SHA-256 mismatch")
    if report.get("synthetic_application_class_name") != PAYLOAD_PACKAGE + ".SyntheticGuestApplication":
        raise RuntimeError("E79 signed Application class identity mismatch")
    if report.get("api") != 34 or report.get("process_name") != PROBE_PACKAGE + ":fonesi_guest_ui":
        raise RuntimeError("E79 Android API/process identity mismatch")
    for key in ("pid", "uid"):
        if type(report.get(key)) is not int or report[key] < (1 if key == "pid" else 0):
            raise RuntimeError("E79 receipt has invalid " + key)
    if report.get("lifecycle_transcript") != "CREATE,START,RESUME,PAUSE,STOP,DESTROY":
        raise RuntimeError("E79 host Activity lifecycle transcript incomplete")
    for key in ("run_id", "synthetic_scope_a_id", "synthetic_scope_b_id", "e79_private_cluster_id", "e79_process_token"):
        value = report.get(key)
        if not isinstance(value, str) or str(uuid.UUID(value)) != value:
            raise RuntimeError("E79 receipt has noncanonical UUID: " + key)
    if report["synthetic_scope_a_id"] == report["synthetic_scope_b_id"]:
        raise RuntimeError("E79 scope UUIDs are not distinct")
    for key in ("e79_marker_a", "e79_marker_b"):
        if not isinstance(report.get(key), str) or re.fullmatch(r"[0-9a-f]{32}", report[key]) is None:
            raise RuntimeError("E79 receipt marker is invalid: " + key)
    if report["e79_marker_a"] == report["e79_marker_b"]:
        raise RuntimeError("E79 scope markers are not distinct")
    expected_starts = 1 if stage == "write" else 2
    for key in ("e79_start_count_a", "e79_start_count_b"):
        if report.get(key) != expected_starts:
            raise RuntimeError("E79 Application onCreate count mismatch: " + key)
    if stage == "write":
        if report.get("e79_process_restart_verified") is not False or report.get("e79_scoped_data_after_process_restart_verified") is not False:
            raise RuntimeError("E79 write stage cannot claim a process restart")
    else:
        if report.get("e79_process_restart_verified") is not True or report.get("e79_scoped_data_after_process_restart_verified") is not True:
            raise RuntimeError("E79 verify stage lacks process-restart persistence evidence")
        if type(report.get("e79_prior_pid")) is not int or report["e79_prior_pid"] <= 0:
            raise RuntimeError("E79 verify receipt prior PID invalid")
        for key in ("e79_prior_process_token", "e79_prior_run_id"):
            value = report.get(key)
            if not isinstance(value, str) or str(uuid.UUID(value)) != value:
                raise RuntimeError("E79 verify receipt prior UUID invalid: " + key)
    return report


def validate_e79_receipts(write_report, verify_report, source_commit, probe_apk_sha256, payload_apk_sha256, expected_proof="e79"):
    if expected_proof not in ("e79", "e80"):
        raise RuntimeError("Only E79/E80 exact synthetic process proofs are accepted")
    write = _validate_e79_stage(write_report, "write", source_commit, probe_apk_sha256, payload_apk_sha256, expected_proof)
    verify = _validate_e79_stage(verify_report, "verify", source_commit, probe_apk_sha256, payload_apk_sha256, expected_proof)
    if write["run_id"] == verify["run_id"]:
        raise RuntimeError("E79 stages must have different run UUIDs")
    if write["pid"] == verify["pid"] or write["e79_process_token"] == verify["e79_process_token"]:
        raise RuntimeError("E79 verify stage reused the original process identity")
    if write["uid"] != verify["uid"]:
        raise RuntimeError("E79 same-package host UID changed across process restart")
    for key in ("e79_private_cluster_id", "synthetic_scope_a_id", "synthetic_scope_b_id", "e79_marker_a", "e79_marker_b"):
        if write[key] != verify[key]:
            raise RuntimeError("E79 process restart changed persisted scope identity/marker: " + key)
    if verify.get("e79_prior_pid") != write["pid"] or verify.get("e79_prior_process_token") != write["e79_process_token"] or verify.get("e79_prior_run_id") != write["run_id"]:
        raise RuntimeError("E79 verify receipt is not bound to completed write-stage process")
    return write, verify



def _validate_e80_constructor_stage(report):
    required = (
        "e80_private_activity_constructor_verified",
        "e80_private_activity_main_thread_verified",
        "e80_private_activity_private_loader_verified",
        "e80_private_activity_unattached_verified",
    )
    bad = [key for key in required if report.get(key) is not True]
    if bad:
        raise RuntimeError("E80 signed PRIVATE Activity proof missing: " + ",".join(bad))
    if report.get("e80_private_activity_constructor_count") != 1 or type(report.get("e80_private_activity_constructor_count")) is not int:
        raise RuntimeError("E80 constructor must be executed exactly once per process")
    if report.get("e80_private_activity_class_name") != PAYLOAD_PACKAGE + ".PayloadActivity":
        raise RuntimeError("E80 constructed Activity must match exact signed private class")
    if report.get("e80_guest_activity_oncreate_called") is not False or report.get("actual_guest_activity_attached") is not False:
        raise RuntimeError("E80 must not report guest Activity attach/onCreate")


def validate_e80_receipts(write_report, verify_report, source_commit, probe_apk_sha256, payload_apk_sha256):
    """Require real E80 stage identity and all inherited E79 process/data checks, plus constructor evidence."""
    write, verify = validate_e79_receipts(
        write_report, verify_report, source_commit, probe_apk_sha256, payload_apk_sha256,
        expected_proof="e80",
    )
    for stage, report in (("write", write), ("verify", verify)):
        _validate_e80_constructor_stage(report)
    return write, verify


def _validate_e81_scoped_stage(report):
    """E81 method delegation is not Android Activity attachment."""
    required_true = (
        "e81_scoped_mode", "e81_private_activity_scope_bound_verified",
        "e81_private_activity_rebinding_rejected",
        "e81_private_activity_scoped_readback_verified",
        "e81_private_activity_base_context_null",
    )
    missing = [key for key in required_true if report.get(key) is not True]
    if missing:
        raise RuntimeError("E81 scoped PRIVATE Activity method proof missing: " + ",".join(missing))
    if report.get("e81_bound_scope_id") != report.get("synthetic_scope_a_id"):
        raise RuntimeError("E81 PRIVATE Activity scope must equal signed A scope")
    for key in ("e81_guest_activity_oncreate_called", "e81_framework_activity_attached"):
        if report.get(key) is not False:
            raise RuntimeError("E81 must not claim guest Activity onCreate/framework attachment")


def validate_e81_receipts(write_report, verify_report, source_commit, probe_apk_sha256, payload_apk_sha256):
    """Inherit E80/E79 source/APK/PID/scope constraints and positive E81 evidence."""
    write, verify = validate_e80_receipts(
        write_report, verify_report, source_commit, probe_apk_sha256, payload_apk_sha256)
    _validate_e81_scoped_stage(write)
    _validate_e81_scoped_stage(verify)
    return write, verify


def read_e79_stage_receipt(device, run_id, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        raw = device("exec-out", "run-as", PROBE_PACKAGE, "cat",
                     "files/guest-ui-probe/" + run_id + ".json", timeout=12, check=False)
        if raw.returncode == 0 and len(raw.stdout) <= 16384:
            try:
                report = json.loads(raw.stdout)
                if report.get("complete") is True:
                    if report.get("run_id") != run_id:
                        raise RuntimeError("E79 stage receipt UUID mismatch")
                    return report
            except json.JSONDecodeError:
                pass
        time.sleep(2)
    raise RuntimeError("E79 stage receipt missing before bounded timeout: " + run_id)


def launch_e79_stage(device, stage, run_id, args, extras=None):
    launch = [
        "shell", "am", "start", "-W", "-n",
        PROBE_PACKAGE + "/.SyntheticGuestUiProbeActivity",
        "--es", "run_id", run_id,
        "--es", "proof_level", args.proof_level,
        "--es", "e79_stage", stage,
        "--es", "source_commit", args.source_commit,
        "--es", "probe_apk_sha256", args.expected_probe_sha256,
        "--es", "payload_apk_sha256", args.expected_payload_sha256,
    ]
    if args.e81_scoped_activity:
        launch.extend(["--es", "e81_scoped_mode", "enabled"])
    for key, value in (extras or {}).items():
        launch.extend(["--ei" if isinstance(value, int) else "--es", key, str(value)])
    started = device(*launch, timeout=90, check=False)
    if started.returncode:
        raise RuntimeError("E79 " + stage + " Activity launch failed")
    return read_e79_stage_receipt(device, run_id, args.probe_timeout)


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
    parser.add_argument("--expected-probe-sha256", default=PROBE_SHA256)
    parser.add_argument("--expected-payload-sha256", default=PAYLOAD_SHA256)
    parser.add_argument("--split-fixture-dir", type=Path, help="Directory containing only E21 synthetic signed base/feature fixtures")
    parser.add_argument("--expected-split-base-sha256")
    parser.add_argument("--expected-split-feature-sha256")
    parser.add_argument("--expected-split-dependent-sha256")
    parser.add_argument("--expected-split-config-sha256")
    parser.add_argument("--source-commit", default="a9aa5a8d53b0c82eabcc071fd0e0fa19fdcc612c")
    parser.add_argument("--boot-timeout", type=int, default=480)
    parser.add_argument("--probe-timeout", type=int, default=180)
    parser.add_argument("--allow-software-emulation", action="store_true",
                        help="E77/E78/E79/E80-only bounded synthetic proof fallback when GitHub has no KVM")
    parser.add_argument("--e81-scoped-activity", action="store_true",
                        help="Test-only PRIVATE Activity method scope proof; requires --proof-level e80")
    parser.add_argument("--proof-level", choices=("e11", "e12", "e13", "e14", "e15", "e16", "e17", "e21", "e22", "e23", "e24", "e25", "e29", "e74", "e77", "e78", "e79", "e80"), default="e11",
                        help="E13 additionally verifies independent synthetic native file/SQLite routes")
    args = parser.parse_args()
    if args.e81_scoped_activity and args.proof_level != "e80":
        parser.error("E81 scoped Activity method requires explicit e80 proof level")
    if args.boot_timeout not in range(30, 601) or args.probe_timeout not in range(15, 301):
        parser.error("Timeout out of bounded range")
    if args.allow_software_emulation and not software_emulation_proof_allowed(args.proof_level):
        parser.error("Software-emulation fallback is restricted to bounded E77-E80 synthetic proofs")
    for digest in (args.expected_probe_sha256, args.expected_payload_sha256):
        if re.fullmatch(r"[0-9a-f]{64}", digest) is None:
            parser.error("Expected SHA-256 must be 64 lowercase hexadecimal characters")
    if args.proof_level in ("e21", "e22", "e23"):
        if args.split_fixture_dir is None:
            parser.error("E21 must specify its dedicated fixture directory")
        for digest in (args.expected_split_base_sha256, args.expected_split_feature_sha256):
            if not digest or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
                parser.error("Split proof requires exact SHA-256 for base and feature APKs")
        if args.proof_level in ("e22", "e23") and (not args.expected_split_dependent_sha256 or
                re.fullmatch(r"[0-9a-f]{64}", args.expected_split_dependent_sha256) is None):
            parser.error("E22/E23 requires an exact SHA-256 for the dependent beta APK")
        if args.proof_level == "e23" and (not args.expected_split_config_sha256 or
                re.fullmatch(r"[0-9a-f]{64}", args.expected_split_config_sha256) is None):
            parser.error("E23 requires exact SHA-256 for the signed config split APK")
    if re.fullmatch(r"[0-9a-f]{40}", args.source_commit) is None:
        parser.error("Source commit must be a full Git SHA-1")
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
        "source_commit": args.source_commit,
        "result": "RUNNING",
        "android_boot_verified": False,
        "android_ui_configuration_stable": False,
        "android_ui_configuration_sample_count": 0,
        "android_ui_configuration_consecutive_equal_transitions": 0,
        "android_ui_configuration_last_fingerprint": [],
        "android_code_image_verified": False,
        "android_guest_bytecode_verified": False,
        "android_native_ab_sqlite_verified": False,
        "android_scoped_storage_verified": False,
        "android_internal_binder_verified": False,
        "android_binder_private_bytecode_verified": False,
        "android_synthetic_application_on_create_verified": False,
        "android_private_application_class_preflight_verified": False,
        "android_synthetic_application_attached": False,
        "android_synthetic_scoped_storage_ab_verified": False,
        "android_synthetic_scope_persistence_verified": False,
        "android_synthetic_scope_cross_readback_verified": False,
        "android_private_isolated_split_code_verified": False,
        "android_private_isolated_split_resources_verified": False,
        "android_dependent_feature_code_verified": False,
        "android_dependent_feature_resources_verified": False,
        "android_feature_configuration_locale_verified": False,
        "android_signed_private_hosted_view_lifecycle_verified": False,
        "android_signed_private_guest_component_manifest_verified": False,
        "android_synthetic_component_dispatch_verified": False,
        "android_private_activity_class_preflight_verified": False,
        "android_private_activity_constructor_preflight_verified": False,
        "android_production_clone_accepted": False,
        "android_third_party_component_executed": False,
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
            ("probe", probe_apk, args.expected_probe_sha256),
            ("payload", payload_apk, args.expected_payload_sha256)
        ):
            if not file.is_file() or file.is_symlink() or sha256(file) != expected:
                raise RuntimeError(label + " APK not present or hash differs from green CI artifact")
        evidence["probe_sha256"] = args.expected_probe_sha256
        evidence["payload_sha256"] = args.expected_payload_sha256
        split_base = None
        split_feature = None
        split_dependent = None
        split_config = None
        if args.proof_level in ("e21", "e22", "e23"):
            stage = "verify signed synthetic split APK input"
            fixture_dir = args.split_fixture_dir.resolve()
            split_base = fixture_dir / "synthetic-split-base.apk"
            split_feature = fixture_dir / "synthetic-split-feature.apk"
            split_dependent = fixture_dir / "synthetic-split-dependent.apk"
            split_config = fixture_dir / "synthetic-split-config.apk"
            members = [("base", split_base, args.expected_split_base_sha256),
                       ("feature", split_feature, args.expected_split_feature_sha256)]
            if args.proof_level in ("e22", "e23"):
                members.append(("dependent", split_dependent, args.expected_split_dependent_sha256))
            if args.proof_level == "e23":
                members.append(("config", split_config, args.expected_split_config_sha256))
            for label, file, expected in members:
                if not file.is_file() or file.is_symlink() or sha256(file) != expected:
                    raise RuntimeError("E21 signed " + label + " APK hash mismatch")
            receipt_name = {"e21": "checksums.json", "e22": "dependency-checksums.json",
                            "e23": "config-checksums.json"}[args.proof_level]
            fixture_receipt = fixture_dir / receipt_name
            if not fixture_receipt.is_file() or fixture_receipt.is_symlink():
                raise RuntimeError("E21 fixture checksum manifest missing")
            source_receipt = json.loads(fixture_receipt.read_text(encoding="utf-8"))
            if (source_receipt.get("package") != SPLIT_PACKAGE or
                    source_receipt.get("signed_synthetic_only") is not True or
                    source_receipt.get("base_sha256") != args.expected_split_base_sha256 or
                    source_receipt.get("feature_sha256") != args.expected_split_feature_sha256):
                raise RuntimeError("Synthetic split fixture receipt does not match exact input APKs")
            if args.proof_level == "e23":
                if (source_receipt.get("version") != 1 or
                        source_receipt.get("config_name") != "feature.alpha.fr" or
                        source_receipt.get("config_for") != "feature.alpha" or
                        source_receipt.get("has_config_code") is not False or
                        source_receipt.get("third_party_accounts_tested") is not False or
                        source_receipt.get("production_guest_engine_enabled") is not False or
                        source_receipt.get("dependent_sha256") != args.expected_split_dependent_sha256 or
                        source_receipt.get("config_sha256") != args.expected_split_config_sha256):
                    raise RuntimeError("E23 signed config fixture manifest is not an exact match")
                evidence["split_config_sha256"] = args.expected_split_config_sha256
                evidence["split_dependent_sha256"] = args.expected_split_dependent_sha256
            elif args.proof_level == "e22":
                if (source_receipt.get("parent") != "feature.alpha" or
                        source_receipt.get("dependent") != "feature.beta" or
                        source_receipt.get("production_guest_engine_enabled") is not False or
                        source_receipt.get("dependent_sha256") != args.expected_split_dependent_sha256):
                    raise RuntimeError("E22 dependent feature fixture is not the pinned signed graph")
                evidence["split_dependent_sha256"] = args.expected_split_dependent_sha256
            elif source_receipt.get("split") != "feature.alpha":
                raise RuntimeError("E21 fixture must contain exactly its original alpha feature")
            evidence["split_base_sha256"] = args.expected_split_base_sha256
            evidence["split_feature_sha256"] = args.expected_split_feature_sha256
        save()

        stage = "verify owned SDK and hardware acceleration"
        image_path = sdk.joinpath(*SDK_IMAGE.split(";"), "system.img")
        if not adb.is_file() or not emulator_binary.is_file() or not image_path.is_file():
            raise RuntimeError("Pinned emulator, AOSP image or adb is missing")
        has_kvm = Path("/dev/kvm").exists() and os.access("/dev/kvm", os.R_OK | os.W_OK)
        if not has_kvm and not (software_emulation_proof_allowed(args.proof_level) and args.allow_software_emulation):
            evidence["result"] = "BLOCKED"
            raise RuntimeError("GitHub runner has no accessible KVM; Android boot not attempted")
        # Keep older proof levels fail-closed without KVM. E77 verifies signed Activity
        # constructor shape; E78 executes only a project-owned synthetic Application and
        # two same-UID scopes. Neither proves guest Activity execution or phone compatibility.
        evidence["emulator_acceleration"] = "KVM" if has_kvm else "software"

        stage = "create isolated emulator"
        avd = "fonesi_e11_" + attempt.split("-")[0]
        port = choose_port()
        serial = "emulator-" + str(port)
        evidence["owned_avd"] = avd
        evidence["owned_serial"] = serial
        save()
        created = run([manager, "create", "avd", "--name", avd,
                       "--package", SDK_IMAGE, "--device", "pixel_6"],
                      timeout=90, check=False, input_data="no\n")
        if created.returncode:
            (reports / "avd-create.log").write_text(created.stderr[-8000:] + created.stdout[-4000:])
            raise RuntimeError("AOSP AVD creation failed")

        stage = "boot isolated Android 34"
        output_log = (reports / "emulator-startup.log").open("w")
        process = subprocess.Popen(
            [str(emulator_binary), "-avd", avd, "-port", str(port),
             "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot", "-no-metrics",
             "-accel", "on" if evidence["emulator_acceleration"] == "KVM" else "off", "-gpu", "swiftshader_indirect", "-memory", "2048",
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
                raise RuntimeError(synthetic_install_failure(apk, installed))
        evidence["android_apks_installed"] = True
        if args.proof_level in ("e24", "e25", "e29", "e74", "e77", "e78", "e79", "e80"):
            # AOSP first boot can deliver late display/locale configuration changes after
            # sys.boot_completed. Preserve the complete seven-value fingerprint and require
            # four identical consecutive snapshots before dispatching the host Activity.
            # A single shell call avoids seven serial ADB process/transport round trips,
            # which can exhaust the stability budget under E77 software emulation.
            stage = "wait for stable Android UI configuration before synthetic Activity probe"
            deadline = time.monotonic() + UI_CONFIGURATION_STABILITY_TIMEOUT_SECONDS
            previous = None
            stable_samples = 0
            sample_count = 0
            stable = False
            while True:
                remaining = deadline - time.monotonic()
                if remaining < 1:
                    break
                command_timeout = min(UI_CONFIGURATION_READ_TIMEOUT_SECONDS, int(remaining))
                current = read_ui_configuration(device, timeout_seconds=command_timeout)
                sample_count += 1
                if current == previous:
                    stable_samples += 1
                else:
                    previous = current
                    stable_samples = 0
                evidence["android_ui_configuration_sample_count"] = sample_count
                evidence["android_ui_configuration_consecutive_equal_transitions"] = stable_samples
                evidence["android_ui_configuration_last_fingerprint"] = list(current)
                save()
                if stable_samples >= UI_CONFIGURATION_REQUIRED_STABLE_TRANSITIONS:
                    evidence["android_ui_configuration_stable"] = True
                    save()
                    stable = True
                    break
                remaining = deadline - time.monotonic()
                if remaining > 0:
                    time.sleep(min(UI_CONFIGURATION_SAMPLE_INTERVAL_SECONDS, remaining))
            if not stable:
                evidence["android_ui_configuration_timeout"] = {
                    "timeout_seconds": UI_CONFIGURATION_STABILITY_TIMEOUT_SECONDS,
                    "required_identical_snapshots": UI_CONFIGURATION_REQUIRED_STABLE_TRANSITIONS + 1,
                    "sample_count": sample_count,
                    "consecutive_equal_transitions": stable_samples,
                    "last_fingerprint": list(previous or ()),
                }
                save()
                raise RuntimeError(
                    "Android UI configuration did not stabilize within bounded "
                    + str(UI_CONFIGURATION_STABILITY_TIMEOUT_SECONDS) + "-second window"
                )
        if args.proof_level in ("e21", "e22", "e23"):
            stage = "install exact signed isolated synthetic split members"
            install_members = [split_base, split_feature]
            if args.proof_level in ("e22", "e23"):
                install_members.append(split_dependent)
            if args.proof_level == "e23":
                install_members.append(split_config)
            installed = device("install-multiple", "-r", *install_members, timeout=90, check=False)
            if installed.returncode or "Success" not in installed.stdout:
                raise RuntimeError("Android PackageManager rejected the signed isolated split APK fixture")
            paths = device("shell", "pm", "path", SPLIT_PACKAGE).stdout.splitlines()
            package_paths = [line for line in paths if line.startswith("package:")]
            if len(package_paths) != len(install_members) or len(set(package_paths)) != len(install_members):
                raise RuntimeError("PackageManager did not report the exact signed split member count")
            evidence["android_isolated_fixture_installed"] = True
        save()

        e79_write_report = None
        report = None
        expected_run_id = None
        if args.proof_level in ("e79", "e80"):
            stage = "E79 write-stage signed synthetic scopes and Application markers"
            write_run_id = str(uuid.uuid4())
            e79_write_report = launch_e79_stage(device, "write", write_run_id, args)
            _validate_e79_stage(e79_write_report, "write", args.source_commit,
                                args.expected_probe_sha256, args.expected_payload_sha256, args.proof_level)
            if args.proof_level == "e80":
                _validate_e80_constructor_stage(e79_write_report)
                if args.e81_scoped_activity:
                    _validate_e81_scoped_stage(e79_write_report)
            stage = "E79 force-stop original synthetic host process"
            device("shell", "am", "force-stop", PROBE_PACKAGE, timeout=30, check=False)
            evidence["android_host_process_force_stop_requested"] = True
            process_deadline = time.monotonic() + 30
            process_stopped = False
            while time.monotonic() < process_deadline:
                old_pid = device("shell", "pidof", PROBE_PACKAGE + ":fonesi_guest_ui",
                                 timeout=8, check=False)
                if old_pid.returncode == 1 and not old_pid.stdout.strip():
                    process_stopped = True
                    break
                if old_pid.returncode not in (0, 1):
                    raise RuntimeError("E79 cannot reliably query the prior dedicated process PID")
                time.sleep(1)
            if not process_stopped:
                raise RuntimeError("E79 force-stop did not terminate the dedicated host process")
            evidence["android_host_process_absence_confirmed"] = True
            stage = "E79 relaunch fresh process and verify persisted scopes"
            verify_run_id = str(uuid.uuid4())
            handoff = {
                "e79_private_cluster_id": e79_write_report["e79_private_cluster_id"],
                "e79_scope_a": e79_write_report["synthetic_scope_a_id"],
                "e79_scope_b": e79_write_report["synthetic_scope_b_id"],
                "e79_marker_a": e79_write_report["e79_marker_a"],
                "e79_marker_b": e79_write_report["e79_marker_b"],
                "e79_prior_pid": e79_write_report["pid"],
                "e79_prior_process_token": e79_write_report["e79_process_token"],
                "e79_prior_run_id": e79_write_report["run_id"],
            }
            report = launch_e79_stage(device, "verify", verify_run_id, args, handoff)
            if args.proof_level == "e80":
                if args.e81_scoped_activity:
                    validate_e81_receipts(e79_write_report, report, args.source_commit,
                                          args.expected_probe_sha256, args.expected_payload_sha256)
                else:
                    validate_e80_receipts(e79_write_report, report, args.source_commit,
                                          args.expected_probe_sha256, args.expected_payload_sha256)
            else:
                validate_e79_receipts(e79_write_report, report, args.source_commit,
                                      args.expected_probe_sha256, args.expected_payload_sha256)
            receipt_name = "e81-process-restart-receipt.json" if args.e81_scoped_activity else (
                args.proof_level + "-process-restart-receipt.json")
            (reports / receipt_name).write_text(
                json.dumps({"force_stop_requested": True, "prior_process_absent_before_relaunch": process_stopped, "write_stage": e79_write_report, "verify_stage": report}, indent=2) + "\n")
            evidence["android_synthetic_scope_process_restart_verified"] = True
            evidence["android_synthetic_application_new_process_oncreate_verified"] = True
            evidence["android_synthetic_scope_data_after_process_restart_verified"] = True
            evidence["android_synthetic_scope_cross_readback_verified"] = True
            save()
        if args.proof_level in ("e79", "e80"):
            pass
        elif args.proof_level in ("e24", "e25", "e29", "e74", "e77", "e78"):
            stage = "launch dedicated synthetic UI Activity with signed private View factory"
            expected_run_id = str(uuid.uuid4())
            launch_args = [
                "shell", "am", "start", "-W", "-n",
                PROBE_PACKAGE + "/.SyntheticGuestUiProbeActivity",
                "--es", "run_id", expected_run_id,
                "--es", "proof_level", args.proof_level,
            ]
            if args.proof_level == "e78":
                launch_args.extend([
                    "--es", "source_commit", args.source_commit,
                    "--es", "probe_apk_sha256", args.expected_probe_sha256,
                    "--es", "payload_apk_sha256", args.expected_payload_sha256,
                ])
            started = device(*launch_args, timeout=90, check=False)
            if started.returncode:
                raise RuntimeError("Synthetic E24/E78 host Activity dispatch failed")
            stage = "read signed private guest View/Application attachment and scoped data receipt"
        elif args.proof_level in ("e21", "e22", "e23"):
            stage = "dispatch dedicated isolated synthetic split Activity"
            expected_run_id = str(uuid.uuid4())
            started = device("shell", "am", "start", "-W", "-n",
                             PROBE_PACKAGE + "/.SplitCodeProbeActivity",
                             "--es", "run_id", expected_run_id,
                             "--es", "proof_level", args.proof_level, timeout=90, check=False)
            if started.returncode:
                raise RuntimeError("Synthetic split host Activity dispatch failed")
            stage = "read fresh private signed base and feature receipt"
        elif args.proof_level in ("e15", "e16", "e17"):
            stage = "bind dedicated synthetic guest channel by explicit host Activity"
            expected_run_id = str(uuid.uuid4())
            started = device("shell", "am", "start", "-W", "-n",
                             PROBE_PACKAGE + "/.SyntheticChannelProbeActivity",
                             "--es", "run_id", expected_run_id, timeout=90, check=False)
            if started.returncode:
                raise RuntimeError("Synthetic Binder host Activity dispatch failed")
            stage = "read exact remote same-UID Binder round-trip receipt"
        elif args.proof_level == "e13":
            stage = "dispatch synthetic two-process native A/B probe"
            expected_run_id = str(uuid.uuid4())
            started = device("shell", "am", "start", "-W", "-n",
                             PROBE_PACKAGE + "/.MainActivity", "--es", "run_id", expected_run_id,
                             timeout=90, check=False)
            if started.returncode:
                raise RuntimeError("Synthetic A/B host activity dispatch failed")
            stage = "read exact original/A/B filesystem and SQLite receipt"
        else:
            stage = "launch synthetic guest-code activity"
            started = device("shell", "am", "start", "-W", "-n",
                             PROBE_PACKAGE + "/.GuestCodeProbeActivity", timeout=90, check=False)
            if started.returncode:
                raise RuntimeError("Guest code probe Activity dispatch failed")
            stage = "read exact dedicated process receipt"
        deadline = time.monotonic() if args.proof_level in ("e79", "e80") else time.monotonic() + args.probe_timeout
        report = report if args.proof_level in ("e79", "e80") else None
        while time.monotonic() < deadline:
            if args.proof_level in ("e24", "e25", "e29", "e74", "e77", "e78"):
                raw = device("exec-out", "run-as", PROBE_PACKAGE, "cat",
                             "files/guest-ui-probe/" + expected_run_id + ".json",
                             timeout=12, check=False)
                if raw.returncode == 0 and len(raw.stdout) <= 16384:
                    try:
                        found = json.loads(raw.stdout)
                        if found.get("complete") is True:
                            if found.get("run_id") != expected_run_id:
                                raise RuntimeError("E24 private View receipt UUID mismatch")
                            report = found
                            break
                    except json.JSONDecodeError:
                        pass
            elif args.proof_level in ("e21", "e22", "e23"):
                raw = device("exec-out", "run-as", PROBE_PACKAGE, "cat",
                             "files/split-code-probe/" + expected_run_id + ".json",
                             timeout=12, check=False)
                if raw.returncode == 0 and len(raw.stdout) <= 16384:
                    try:
                        found = json.loads(raw.stdout)
                        if found.get("complete") is True:
                            if found.get("run_id") != expected_run_id:
                                raise RuntimeError("E21 split proof receipt UUID mismatch")
                            report = found
                            break
                    except json.JSONDecodeError:
                        pass
            elif args.proof_level in ("e15", "e16", "e17"):
                raw = device("exec-out", "run-as", PROBE_PACKAGE, "cat",
                             "files/binder-probe/" + expected_run_id + ".json",
                             timeout=12, check=False)
                if raw.returncode == 0 and len(raw.stdout) <= 16384:
                    try:
                        found = json.loads(raw.stdout)
                        if found.get("complete") is True:
                            if found.get("run_id") != expected_run_id:
                                raise RuntimeError("Synthetic Binder receipt UUID differs from requested run")
                            report = found
                            break
                    except json.JSONDecodeError:
                        pass
            elif args.proof_level == "e13":
                raw = device("exec-out", "run-as", PROBE_PACKAGE, "cat",
                             "files/native-probe/latest.json", timeout=12, check=False)
                if raw.returncode == 0 and len(raw.stdout) <= 16384:
                    try:
                        found = json.loads(raw.stdout)
                        if found.get("complete") is True:
                            if found.get("run_id") != expected_run_id:
                                raise RuntimeError("Synthetic native receipt UUID differs from launched run")
                            report = found
                            break
                    except json.JSONDecodeError:
                        pass
            else:
                # Brand-new AVD/app data: no previously saved run IDs or receipts may exist.
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
            raise RuntimeError("No complete Android synthetic receipt before timeout")
        if args.proof_level in ("e24", "e25", "e29", "e74", "e77", "e78", "e79", "e80"):
            (reports / "synthetic-ui-receipt.json").write_text(json.dumps(report, indent=2) + "\n")
            expected_true = (
                "complete", "passed", "real_host_activity_lifecycle_verified",
                "signed_private_guest_view_rendered", "private_guest_view_attached_to_window",
                "private_guest_lifecycle_relay_verified", "signed_private_resources_verified",
                "signed_private_image_rechecked", "same_android_uid",
            )
            expected_false = ("kernel_uid_isolation", "actual_guest_activity_attached",
                              "third_party_guest_components_tested", "social_accounts_tested")
            if args.proof_level == "e29":
                expected_true = expected_true + (
                    "private_component_manifest_verified", "synthetic_component_dispatch_verified",
                )
                expected_false = expected_false + (
                    "real_guest_activity_registered", "production_clone_accepted",
                    "third_party_component_executed",
                )
            if args.proof_level in ("e74", "e77", "e78"):
                expected_true = expected_true + ("private_activity_class_preflight_verified",)
                expected_false = expected_false + (
                    "real_guest_activity_registered", "production_clone_accepted",
                    "third_party_component_executed",
                )
            if report.get("passed") is False:
                stage = re.sub(r"[^A-Za-z0-9_]", "", str(report.get("failure_stage", "unknown")))[:96]
                reason = re.sub(r"[^A-Za-z0-9_]", "", str(report.get("failure_type", "unknown")))[:64]
                transcript = re.sub(r"[^A-Za-z0-9_,]", "", str(
                    report.get("android_lifecycle_transcript", "unknown")))[:128]
                raise RuntimeError(args.proof_level.upper() + " private UI failure at " + stage +
                                   " type " + reason + " lifecycle " + transcript)
            missing_true = [key for key in expected_true if report.get(key) is not True]
            missing_false = [key for key in expected_false if report.get(key) is not False]
            if missing_true or missing_false:
                raise RuntimeError("E24/E25 signed private View/lifecycle assertions failed: true=" +
                    ",".join(missing_true) + "; false=" + ",".join(missing_false))
            if report.get("proof_level") != args.proof_level:
                raise RuntimeError("E24/E25 private UI proof mode differs from requested mode")
            if args.proof_level in ("e77", "e78", "e79", "e80") and report.get("private_activity_constructor_preflight_verified") is not True:
                raise RuntimeError("E77/E78 public no-argument Activity constructor preflight was not proven")
            if args.proof_level == "e25" and not (
                    report.get("private_component_manifest_verified") is True and
                    report.get("real_guest_activity_registered") is False):
                raise RuntimeError("E25 signed private component manifest preflight did not pass")
            if args.proof_level == "e29" and report.get("dispatch_ticket_private_apk_sha256") != args.expected_payload_sha256:
                raise RuntimeError("E29 ticket did not bind the exact signed private payload SHA-256")
            if args.proof_level in ("e74", "e77", "e78", "e79", "e80") and report.get("private_activity_class_preflight_verified") is not True:
                raise RuntimeError("E74 signed private Activity class was not proven by the private loader")
            if args.proof_level == "e78":
                validate_e78_receipt(report, args.source_commit,
                                     args.expected_probe_sha256, args.expected_payload_sha256)
            if args.proof_level == "e79":
                validate_e79_receipts(e79_write_report, report, args.source_commit,
                                      args.expected_probe_sha256, args.expected_payload_sha256)
            if args.proof_level == "e80":
                if args.e81_scoped_activity:
                    validate_e81_receipts(e79_write_report, report, args.source_commit,
                                          args.expected_probe_sha256, args.expected_payload_sha256)
                else:
                    validate_e80_receipts(e79_write_report, report, args.source_commit,
                                          args.expected_probe_sha256, args.expected_payload_sha256)
            if (report.get("lifecycle_transcript") != "CREATE,START,RESUME,PAUSE,STOP,DESTROY" or
                    report.get("process_name") != PROBE_PACKAGE + ":fonesi_guest_ui" or
                    report.get("api") != 34 or type(report.get("pid")) is not int or
                    report["pid"] <= 0 or type(report.get("uid")) is not int or
                    report["uid"] < 0):
                raise RuntimeError("E24 actual Android host lifecycle or process proof mismatch")
            evidence["android_signed_private_hosted_view_lifecycle_verified"] = True
            if args.proof_level == "e25":
                evidence["android_signed_private_guest_component_manifest_verified"] = True
                evidence["result"] = "PASS_E25_SIGNED_PRIVATE_SYNTHETIC_COMPONENT_PREFLIGHT_ONLY"
                print("PASS: Android 34 read signed PRIVATE synthetic Application/Activity manifest "
                      "metadata and attached the hosted View, without registering a guest Activity "
                      "or launching third-party components.", flush=True)
            elif args.proof_level == "e29":
                evidence["android_signed_private_guest_component_manifest_verified"] = True
                evidence["android_synthetic_component_dispatch_verified"] = True
                evidence["result"] = "PASS_E29_SIGNED_PRIVATE_SYNTHETIC_COMPONENT_TICKET_DISPATCH_ONLY"
                print("PASS: E25 private catalog and payload SHA were bound to a UUID ticket, "
                      "validated in an explicit internal host-Activity dispatch, then the existing "
                      "signed synthetic View lifecycle ran. No guest Activity was registered or "
                      "third-party component executed.", flush=True)
            elif args.proof_level == "e74":
                evidence["android_private_activity_class_preflight_verified"] = True
                evidence["result"] = "PASS_E74_SIGNED_PRIVATE_ACTIVITY_CLASS_PREFLIGHT_ONLY"
                print("PASS: Android synthetic host verified signed PRIVATE PayloadActivity "
                      "class/type and exact PRIVATE DexClassLoader, then relayed the existing "
                      "hosted View lifecycle. No guest Activity was attached/launched, "
                      "and no separate OS UID or account was accepted.", flush=True)
            elif args.proof_level == "e77":
                evidence["android_private_activity_class_preflight_verified"] = True
                evidence["android_private_activity_constructor_preflight_verified"] = True
                evidence["result"] = "PASS_E77_SIGNED_PRIVATE_ACTIVITY_CONSTRUCTOR_PREFLIGHT_ONLY"
                print("PASS: Android 34 verified signed PRIVATE Activity classloader and public "
                      "zero-argument constructor shape, then relayed the existing synthetic host "
                      "View lifecycle. No guest Activity was instantiated/attached/launched; "
                      "no separate OS UID or account was accepted.", flush=True)
            elif args.proof_level == "e78":
                evidence["android_private_activity_class_preflight_verified"] = True
                evidence["android_private_activity_constructor_preflight_verified"] = True
                evidence["android_private_application_class_preflight_verified"] = True
                evidence["android_synthetic_application_attached"] = True
                evidence["android_synthetic_application_on_create_verified"] = True
                evidence["android_synthetic_scoped_storage_ab_verified"] = True
                evidence["android_synthetic_scope_persistence_verified"] = True
                evidence["android_synthetic_scope_cross_readback_verified"] = True
                evidence["result"] = "PASS_E78_SIGNED_SYNTHETIC_APPLICATION_ATTACH_AND_SCOPED_DATA_ONLY"
                print("PASS: Android 34 manually attached two instances of the signed PRIVATE "
                      "synthetic Application through public Instrumentation, ran onCreate on the "
                      "main thread, and verified independent persistent SharedPreferences/files/SQLite "
                      "scopes. No ActivityThread registration, guest Activity, third-party app, "
                      "separate UID/account or production clone was accepted.", flush=True)
            elif args.proof_level in ("e79", "e80"):
                evidence["android_private_activity_class_preflight_verified"] = True
                evidence["android_private_activity_constructor_preflight_verified"] = True
                evidence["android_private_application_class_preflight_verified"] = True
                evidence["android_synthetic_application_attached"] = True
                evidence["android_synthetic_application_on_create_verified"] = True
                evidence["android_synthetic_scope_process_restart_verified"] = True
                evidence["android_synthetic_scope_data_after_process_restart_verified"] = True
                evidence["android_synthetic_scope_cross_readback_verified"] = True
                if args.proof_level == "e80":
                    evidence["android_e80_private_synthetic_activity_constructor_verified"] = True
                    if args.e81_scoped_activity:
                        evidence["android_e81_private_activity_scoped_method_verified"] = True
                        evidence["result"] = "PASS_E81_SIGNED_PRIVATE_ACTIVITY_SCOPED_METHOD_ONLY"
                    else:
                        evidence["result"] = "PASS_E80_SIGNED_SYNTHETIC_ACTIVITY_CONSTRUCTOR_ONLY"
                    print("PASS: E80/E81 constructed the exact signed PRIVATE synthetic Activity Java object once per "
                          "process on Android's main Looper without attach/onCreate, retained E79 force-stop "
                          "and scoped-data restart proofs. No real guest Activity, third-party app, "
                          "separate UID/account or production clone accepted.", flush=True)
                else:
                    evidence["result"] = "PASS_E79_SYNTHETIC_SCOPED_DATA_PROCESS_RESTART_ONLY"
                    print("PASS: E79 force-stopped the original Android host process, relaunched a new process, "
                          "reattached signed synthetic Applications, and verified persisted SharedPreferences/files/SQLite "
                          "markers and per-process onCreate counts. Same UID only; no guest Activity, third-party app, "
                          "account isolation or production clone was accepted.", flush=True)
            else:
                evidence["result"] = "PASS_E24_SIGNED_SYNTHETIC_PRIVATE_VIEW_LIFECYCLE_RELAY_ONLY"
                print("PASS: Android 34 hosted an attached View created by signed private "
                      "synthetic DEX and relayed CREATE→DESTROY lifecycle. "
                      "No arbitrary guest Activity, account or UID isolation proved.", flush=True)
        elif args.proof_level in ("e21", "e22", "e23"):
            (reports / "isolated-split-receipt.json").write_text(json.dumps(report, indent=2) + "\n")
            required_true = ("complete", "passed", "private_split_graph_verified",
                             "private_isolated_base_code_executed", "private_isolated_feature_code_executed",
                             "private_isolated_resources_verified", "signed_private_cluster_rechecked",
                             "same_android_uid")
            required_false = ("kernel_uid_isolation", "guest_activity_executed",
                              "third_party_guest_components_tested", "social_accounts_tested")
            if not all(report.get(key) is True for key in required_true) or not all(
                    report.get(key) is False for key in required_false):
                raise RuntimeError("E21 isolated split signed APK loader/resource assertions failed")
            if report.get("proof_level") != args.proof_level:
                raise RuntimeError("Signed split receipt proof level mismatch")
            if args.proof_level in ("e22", "e23") and not all(report.get(key) is True for key in (
                    "private_dependency_graph_verified",
                    "private_dependent_feature_code_executed",
                    "private_dependent_resources_verified")):
                raise RuntimeError("E22 dependent parent DEX/resources proof missing")
            if args.proof_level == "e23" and not all(report.get(key) is True for key in (
                    "private_config_graph_verified", "private_feature_config_locale_verified")):
                raise RuntimeError("E23 signed private feature config locale/resource assertions failed")
            if (report.get("process_name") != PROBE_PACKAGE + ":fonesi_guest_split" or
                    report.get("api") != 34 or type(report.get("pid")) is not int or
                    report["pid"] <= 0 or type(report.get("uid")) is not int or report["uid"] < 0):
                raise RuntimeError("E21 split process/API/UID proof is invalid")
            evidence["android_private_isolated_split_code_verified"] = True
            evidence["android_private_isolated_split_resources_verified"] = True
            if args.proof_level == "e23":
                evidence["android_dependent_feature_code_verified"] = True
                evidence["android_dependent_feature_resources_verified"] = True
                evidence["android_feature_configuration_locale_verified"] = True
                evidence["result"] = "PASS_E23_SIGNED_SYNTHETIC_ALPHA_CONFIG_RESOURCES_ONLY"
                print("PASS: Android 34 verified signed private alpha-targeted French config resources "
                      "and base/alpha/beta DEX parent calls. No third-party accounts tested.", flush=True)
            elif args.proof_level == "e22":
                evidence["android_dependent_feature_code_verified"] = True
                evidence["android_dependent_feature_resources_verified"] = True
                evidence["result"] = "PASS_E22_SIGNED_SYNTHETIC_DEPENDENT_FEATURE_ONLY"
                print("PASS: Android 34 executed signed private base/alpha/beta classloader parent DEX "
                      "and verified staged beta resources. No third-party guest components/accounts.", flush=True)
            else:
                evidence["result"] = "PASS_E21_SIGNED_SYNTHETIC_ISOLATED_BASE_FEATURE_ONLY"
                print("PASS: Android 34 executed independently loaded private signed base/feature DEX "
                      "and verified feature resources. No third-party APK components or accounts tested.", flush=True)
        elif args.proof_level in ("e15", "e16", "e17"):
            (reports / "internal-binder-receipt.json").write_text(json.dumps(report, indent=2) + "\n")
            host_pid, remote_pid = report.get("host_pid"), report.get("remote_pid")
            host_uid, remote_uid = report.get("host_uid"), report.get("remote_uid")
            expected_true = ("complete", "passed", "remote_binder_verified",
                             "challenge_response_verified", "remote_process_distinct", "same_android_uid")
            expected_false = ("guest_application_attached", "third_party_binder_virtualized",
                              "social_accounts_tested", "kernel_uid_isolation")
            if not all(report.get(k) is True for k in expected_true) or not all(
                    report.get(k) is False for k in expected_false):
                raise RuntimeError("Synthetic remote Binder receipt failed required assertions")
            if not (type(host_pid) is int and host_pid > 0 and
                    type(remote_pid) is int and remote_pid > 0 and remote_pid != host_pid and
                    type(host_uid) is int and host_uid >= 0 and
                    type(remote_uid) is int and remote_uid == host_uid):
                raise RuntimeError("Binder receipt lacks distinct PIDs and exact shared UID")
            evidence["android_internal_binder_verified"] = True
            if args.proof_level in ("e16", "e17"):
                if not (report.get("private_guest_bytecode_via_binder_verified") is True and
                        report.get("private_guest_resources_via_binder_verified") is True):
                    raise RuntimeError("E16/E17 private signed guest bytecode Binder proof missing or failed")
                evidence["android_binder_private_bytecode_verified"] = True
                if args.proof_level == "e17":
                    if not (report.get("synthetic_guest_application_attached") is True and
                            report.get("synthetic_guest_application_on_create_verified") is True and
                            report.get("full_guest_application_lifecycle_verified") is False and
                            report.get("guest_activity_executed") is False):
                        raise RuntimeError("E17 synthetic Application attach/onCreate proof missing")
                    evidence["android_synthetic_application_on_create_verified"] = True
                    evidence["result"] = "PASS_E17_SYNTHETIC_APPLICATION_ONCREATE_ONLY"
                    print("PASS: Android 34 AVD attached a signed private synthetic Application "
                          "with public Instrumentation and executed onCreate in a scoped Context. "
                          "No ActivityThread registration, third-party guest Activity or accounts verified.", flush=True)
                else:
                    evidence["result"] = "PASS_E16_BINDER_DISPATCHED_PRIVATE_SYNTHETIC_CODE_ONLY"
                    print("PASS: Android 34 AVD dispatched signed private synthetic guest method "
                          "through remote Binder and verified resources. No third-party Application, "
                          "Activity, Binder virtualization or accounts were executed.", flush=True)
            else:
                evidence["result"] = "PASS_E15_SYNTHETIC_INTERNAL_BINDER_ONLY"
                print("PASS: Android 34 AVD proved versioned in-app Binder IPC between host and "
                      "distinct synthetic guest-named process. Android UID is shared; third-party "
                      "Binder virtualization and real account isolation remain unverified.", flush=True)
        elif args.proof_level == "e13":
            (reports / "native-ab-receipt.json").write_text(json.dumps(report, indent=2) + "\n")
            a, b = report.get("A"), report.get("B")
            host_pid, host_uid = report.get("host_pid"), report.get("host_uid")
            if not (report.get("passed") is True and report.get("original_preserved") is True and
                    report.get("processes_have_distinct_pids") is True and
                    report.get("same_android_uid") is True and
                    report.get("full_clone_engine_tested") is False and
                    report.get("social_accounts_tested") is False and
                    type(host_pid) is int and host_pid > 0 and
                    type(host_uid) is int and host_uid >= 0 and
                    isinstance(a, dict) and isinstance(b, dict)):
                raise RuntimeError("Synthetic native A/B receipt failed top-level assertions")
            if not all(
                child.get("passed") is True and child.get("marker") == label and
                child.get("native_state") == 2 and type(child.get("pid")) is int and
                child["pid"] > 0 and child["pid"] != host_pid and
                type(child.get("uid")) is int and child["uid"] == host_uid
                for child, label in ((a, "A"), (b, "B"))
            ) or a["pid"] == b["pid"]:
                raise RuntimeError("Synthetic native A/B child identity, routing or SQLite checks failed")
            evidence["android_native_ab_sqlite_verified"] = True
            evidence["result"] = "PASS_E13_SYNTHETIC_NATIVE_AB_ONLY"
            print("PASS: Android 34 AVD verified native file/SQLite routing in distinct A/B processes, "
                  "original data preserved. Processes share Android UID; account isolation unverified.", flush=True)
        else:
            (reports / "guest-code-receipt.json").write_text(json.dumps(report, indent=2) + "\n")
            expected_true = ("complete", "passed", "signed_private_cluster", "dex_class_loader",
                             "private_resources", "activation_pending")
            expected_false = ("guest_context_executed", "guest_activity_executed",
                              "binder_provider_tested", "social_accounts_tested")
            if args.proof_level in ("e12", "e14"):
                expected_true += ("guest_bytecode_executed", "guest_process_verified",
                                  "guest_uid_same_as_host")
                reported_pid = report.get("guest_process_pid")
                if (type(reported_pid) is not int or reported_pid <= 0 or
                        report.get("pid") != reported_pid or
                        report.get("guest_process_name") != PROBE_PACKAGE + ":fonesi_guest_loader"):
                    raise RuntimeError("E12/E14 receipt process identity or PID is invalid")
            if args.proof_level == "e14":
                expected_true += ("synthetic_scoped_context_verified", "private_file_state_verified",
                                  "shared_preferences_state_verified", "sqlite_state_verified", "same_android_uid")
                expected_false += ("guest_application_attached", "kernel_uid_isolation")
            if not all(report.get(key) is True for key in expected_true) or not all(
                report.get(key) is False for key in expected_false
            ):
                raise RuntimeError("Android guest-code proof did not satisfy required assertions")
            evidence["android_code_image_verified"] = True
            if args.proof_level == "e14":
                evidence["android_guest_bytecode_verified"] = True
                evidence["android_scoped_storage_verified"] = True
                evidence["result"] = "PASS_E14_SYNTHETIC_SCOPED_CONTEXT_ONLY"
                print("PASS: Android 34 AVD executed synthetic private guest bytecode with "
                      "distinct file, preferences and SQLite scopes. Guest Application, Activity, "
                      "Binder and real account isolation remain unverified.", flush=True)
            elif args.proof_level == "e12":
                evidence["android_guest_bytecode_verified"] = True
                evidence["result"] = "PASS_E12_SYNTHETIC_BYTECODE_ONLY"
                print("PASS: Android 34 AVD executed synthetic signed private guest bytecode. "
                      "Guest Activity/Binder/account isolation remain unverified.", flush=True)
            else:
                evidence["result"] = "PASS_E11_SYNTHETIC_ONLY"
                print("PASS: Android 34 AVD loaded private signed payload DEX and resources. "
                      "Guest Activity, Binder and account isolation remain unverified.", flush=True)
        save()
    except BaseException as failure:
        evidence["failure_stage"] = stage
        evidence["failure_type"] = type(failure).__name__
        evidence["failure_reason"] = str(failure)[:500]
        if evidence.get("result") != "BLOCKED":
            evidence["result"] = "FAIL"
        save()
        raise
    finally:
        # This fresh AVD contains only the project-controlled synthetic APKs.
        # Capture bounded crash tags, not a full system/user logcat.
        if serial is not None and evidence.get("android_boot_verified"):
            try:
                filtered = run(
                    [adb, "-s", serial, "logcat", "-d", "-s",
                     "AndroidRuntime:E", "ActivityTaskManager:W", "ActivityManager:W",
                     "WindowManager:W", "FonesiNativeProbe:E", "libc:F", "*:S"],
                    timeout=15, check=False,
                ).stdout
                (reports / "synthetic-runtime-errors.log").write_text(filtered[-65536:])
            except (OSError, subprocess.SubprocessError):
                pass
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
