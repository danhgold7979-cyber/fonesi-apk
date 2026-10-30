#!/usr/bin/env python3
"""E77: require a real owned-emulator receipt for signed Activity constructor preflight."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
runner = read("scripts/run_guest_code_emulator.py")
probe = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
preflight = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestActivityClassPreflight.kt")
workflow = read(".github/workflows/e77-emulator-activity-preflight-proof.yml")

assert '"e77"' in runner.split('parser.add_argument("--proof-level"', 1)[1].split("default=", 1)[0]
assert "private_activity_constructor_preflight_verified" in runner
assert "PASS_E77_SIGNED_PRIVATE_ACTIVITY_CONSTRUCTOR_PREFLIGHT_ONLY" in runner
assert "args.source_commit" in runner and "sha256(file)" in runner
assert '("e24", "e25", "e29", "e74", "e77", "e78", "e79")' in runner
assert 'report.get("private_activity_constructor_preflight_verified") is not True' in runner
assert "privateActivityConstructorPreflightVerified = true" in probe
assert 'if ("e77".equals(proofLevel) || "e78".equals(proofLevel) || "e79".equals(proofLevel))\n                    privateActivityConstructorPreflightVerified = true;' in probe
assert 'ProbeData.check(privateActivityConstructorPreflightVerified,' in probe
assert 'if ("e74".equals(proofLevel) || "e77".equals(proofLevel) || "e78".equals(proofLevel) || "e79".equals(proofLevel))' in probe
assert 'Class.forName(selection.targetActivityName, false, privateLoader)' in preflight
assert "candidate.getConstructor()" in preflight
assert ".newInstance(" not in preflight and "constructor.newInstance(" not in preflight
assert "preflightExplicitActivityClass(" in loader
for required in (
    "python3 scripts/prepare_native_emulator.py --install --image aosp34",
    "--proof-level e77",
    "--allow-software-emulation",
    "--boot-timeout 600",
    "--source-commit",
    "--expected-probe-sha256",
    "--expected-payload-sha256",
    "PASS_E77_SIGNED_PRIVATE_ACTIVITY_CONSTRUCTOR_PREFLIGHT_ONLY",
    "android_private_activity_constructor_preflight_verified",
    "actual_guest_activity_attached",
    "production_clone_accepted",
):
    assert required in workflow, "E77 workflow is not strict enough: " + required
assert "run_native_probe.py --audit-only" in workflow, "Static audit retained, but emulator must also execute"
assert workflow.index("python3 scripts/prepare_native_emulator.py --install --image aosp34") < workflow.index("--proof-level e77")
assert "if not has_kvm and not (software_emulation_proof_allowed(args.proof_level) and args.allow_software_emulation)" in runner
assert "args.allow_software_emulation and not software_emulation_proof_allowed(args.proof_level)" in runner
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "def read_ui_configuration(device" in runner
assert "UI_CONFIGURATION_READ_TIMEOUT_SECONDS = 20" in runner
assert "UI_CONFIGURATION_STABILITY_TIMEOUT_SECONDS = 120" in runner
assert "UI_CONFIGURATION_SAMPLE_INTERVAL_SECONDS = 2" in runner
assert "UI_CONFIGURATION_REQUIRED_STABLE_TRANSITIONS = 3" in runner
assert 'device(\n        "shell", UI_CONFIGURATION_COMMAND,' in runner
assert '"shell", "sh", "-c", UI_CONFIGURATION_COMMAND' not in runner
assert 'snapshot[0] != "[wm-size]"' in runner
assert 'line == "[wm-density]"' in runner
assert '"font-scale=", "user-rotation=", "locale=", "language=", "country="' in runner
assert "timeout=timeout_seconds, check=False" in runner
assert "deadline = time.monotonic() + UI_CONFIGURATION_STABILITY_TIMEOUT_SECONDS" in runner
assert "stable_samples >= UI_CONFIGURATION_REQUIRED_STABLE_TRANSITIONS" in runner
assert 'evidence["android_ui_configuration_last_fingerprint"] = list(current)' in runner
assert '"last_fingerprint": list(previous or ())' in runner
test_source = read("scripts/test_run_guest_code_emulator.py")
for required in (
    "test_reads_all_seven_values_with_one_adb_shell_command",
    "test_nonzero_adb_exit_fails_closed",
    "test_empty_fingerprint_fails_closed",
    "test_shell_environment_contamination_is_rejected",
    "test_missing_or_duplicate_scalar_marker_is_rejected",
    "test_per_sample_timeout_can_be_reduced_to_remaining_window",
    "test_unbounded_timeout_is_rejected_before_adb_call",
):
    assert required in test_source, "Missing E77 helper regression: " + required
assert "scripts/test_run_guest_code_emulator.py" in workflow
assert "python3 scripts/test_run_guest_code_emulator.py" in workflow
pull_request_block = workflow.split("pull_request:", 1)[1].split("workflow_dispatch:", 1)[0]
assert "phase/e76-activity-preflight-no-class-init-20261030" in pull_request_block
assert "phase/e74-signed-private-activity-class-probe-20261029" not in pull_request_block
print("PASS: E77 workflow requires a booted AOSP API 34 emulator and an exact-source/hash-bound constructor-preflight receipt.")
print("PASS: UI fingerprint retains all seven values, four identical snapshots and bounded diagnostic timeout.")
print("PASS: deterministic fake-ADB tests are wired into the exact-source E77 workflow.")
