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
assert '("e24", "e25", "e29", "e74", "e77")' in runner
assert 'report.get("private_activity_constructor_preflight_verified") is not True' in runner
assert "privateActivityConstructorPreflightVerified = true" in probe
assert 'if ("e77".equals(proofLevel)) privateActivityConstructorPreflightVerified = true;' in probe
assert 'ProbeData.check(privateActivityConstructorPreflightVerified,' in probe
assert 'if ("e74".equals(proofLevel) || "e77".equals(proofLevel))' in probe
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
assert "if not has_kvm and not (args.proof_level == \"e77\" and args.allow_software_emulation)" in runner
assert "args.allow_software_emulation and args.proof_level != \"e77\"" in runner
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E77 workflow requires a booted AOSP API 34 emulator and an exact-source/hash-bound constructor-preflight receipt.")
print("PASS: receipt explicitly denies guest Activity attach/launch, third-party execution and production clone acceptance.")
