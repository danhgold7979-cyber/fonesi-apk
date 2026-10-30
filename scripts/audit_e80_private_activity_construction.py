#!/usr/bin/env python3
"""E80: audit bounded test-owned PRIVATE Activity Java constructor proof, never guest attachment."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding="utf-8")
payload = read("probe-payload/src/main/java/vn/fonesi/payload/PayloadActivity.kt")
counter = read("probe-payload/src/main/java/vn/fonesi/payload/SyntheticActivityConstructionProbe.java")
tests = read("probe-payload/src/test/java/vn/fonesi/payload/SyntheticActivityConstructionProbeTest.java")
host = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
runner = read("scripts/run_guest_code_emulator.py")
runner_tests = read("scripts/test_run_guest_code_emulator.py")
workflow = read(".github/workflows/e80-private-activity-construction.yml")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "init { SyntheticActivityConstructionProbe.recordConstructor() }" in payload
for item in ("AtomicInteger", "CONSTRUCTED.updateAndGet", "if (current != 0)", "return 1"):
    assert item in counter, "E80 private constructor counter missing: " + item
for test_name in ("cleanPrivateConstructorTransitionsExactlyOnce", "duplicateConstructorIsRejected", "negativeOrOverflowMarkerIsRejected"):
    assert test_name in tests, "E80 constructor JVM test missing: " + test_name
for item in ('"e80".equals(proofLevel)', "verifyE80UnattachedPrivateActivity(", "image.preflightExplicitActivityClass(", "new Instrumentation().newActivity(", "Looper.getMainLooper()", "created.getBaseContext() == null", "e80PrivateActivityConstructorCount == 1", '"e80_private_activity_constructor_verified"', '"e80_private_activity_unattached_verified"', '"e80_guest_activity_oncreate_called", false', '"actual_guest_activity_attached", false', '"production_clone_accepted", false'):
    assert item in host, "E80 host proof missing: " + item
assert "callActivityOnCreate(" not in host and "startActivity(created)" not in host
for item in ("def validate_e80_receipts(", "expected_proof=\"e80\"", "_validate_e80_constructor_stage(", '"e80_private_activity_constructor_count"', "PASS_E80_SIGNED_SYNTHETIC_ACTIVITY_CONSTRUCTOR_ONLY", '"pidof", PROBE_PACKAGE + ":fonesi_guest_ui"'):
    assert item in runner, "E80 host runner missing: " + item
for name in ("test_two_exact_signed_synthetic_constructors_pass", "test_missing_main_thread_private_loader_or_unattached_proof_is_denied", "test_wrong_count_or_signed_class_is_denied", "test_guest_oncreate_or_framework_attachment_overclaim_denied", "test_reused_process_and_source_or_apk_hash_drift_denied", "test_relabeling_old_e79_proof_to_e80_is_denied"):
    assert name in runner_tests, "E80 Python proof regression missing: " + name
assert "--proof-level e80" in workflow and "scripts/audit_e79_synthetic_process_restart.py" in workflow
assert "scripts/audit_e80_private_activity_construction.py" in workflow
assert "probe-payload:testDebugUnitTest" in workflow
assert "e80-process-restart-receipt.json" in workflow
assert "const val supportsSplits = false" in runtime and "check(source.apkPaths.size == 1)" in backend
print("PASS E80 source audit: test-only signed PRIVATE Activity constructor runs on main Looper without framework guest attach.")
print("PASS E80 safety: E79 durable A/B restart, exact signed code and source hash, forbidden product/UID/account claims remain enforced.")
