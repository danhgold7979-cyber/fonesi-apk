#!/usr/bin/env python3
"""E79: require a true synthetic host-process restart and durable A/B scoped data proof."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text(encoding="utf-8")
app = read("probe-payload/src/main/java/vn/fonesi/payload/SyntheticGuestApplication.java")
marker = read("probe-payload/src/main/java/vn/fonesi/payload/SyntheticApplicationProcessMarker.java")
marker_tests = read("probe-payload/src/test/java/vn/fonesi/payload/SyntheticApplicationProcessMarkerTest.java")
probe = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
runner = read("scripts/run_guest_code_emulator.py")
runner_tests = read("scripts/test_run_guest_code_emulator.py")
workflow = read(".github/workflows/e79-synthetic-process-restart-durability.yml")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")

# Per-process token is generated once in the signed payload classloader; duplicate callbacks fail closed.
assert "private static final String PROCESS_TOKEN = UUID.randomUUID().toString()" in app
assert "SyntheticApplicationProcessMarker.advance(" in app
assert 'putInt("starts", next.getStarts())' in app
assert 'putInt("last_pid", next.getPid())' in app
assert 'putString("last_process_token", next.getProcessToken())' in app
assert 'currentProcessToken.equals(lastProcessToken)' in marker
assert 'return new State(starts + 1, currentPid, currentProcessToken)' in marker
for name in (
    "freshScopeStartsAtOne", "duplicateOnCreateInSameProcessIsRejected",
    "newProcessAdvancesPersistedCountOnce", "pidReuseWithNewProcessTokenIsNotMistakenForDuplicate",
    "partialPersistedStateIsRejected", "invalidCurrentIdentityIsRejected",
    "negativeAndOverflowCountsAreRejected",
):
    assert name in marker_tests, "Missing E79 lifecycle marker JVM test: " + name

# The host Activity reopens the signed private image in both stages and attaches only synthetic Applications.
assert '"e79".equals(proofLevel)' in probe
assert '"write".equals(e79Stage)' in probe and '"verify".equals(e79Stage)' in probe
assert "verifyE79SyntheticApplication(image, localStore, localCluster)" in probe
assert "Instrumentation instrumentationA = new Instrumentation()" in probe
assert "callApplicationOnCreate(applications[0])" in probe and "callApplicationOnCreate(applications[1])" in probe
assert "e79ScopedDataAfterRestartVerified = true" in probe
assert '"e79_process_restart_verified"' in probe
assert '"guest_application_registered_with_activity_thread", false' in probe
assert '"guest_application_registered_with_package_manager", false' in probe
assert '"production_clone_accepted", false' in probe

# Runner must prove the old process is gone before a second Activity launch, then bind both receipts.
assert "def validate_e79_receipts(" in runner
assert "def launch_e79_stage(" in runner
assert '"am", "force-stop", PROBE_PACKAGE' in runner
assert '"pidof", PROBE_PACKAGE + ":fonesi_guest_ui"' in runner
assert "E79 force-stop did not terminate the dedicated host process" in runner
assert "PASS_E79_SYNTHETIC_SCOPED_DATA_PROCESS_RESTART_ONLY" in runner
for name in (
    "test_valid_two_process_receipts_pass", "test_same_pid_or_process_token_fails_closed",
    "test_changed_scope_or_marker_after_restart_fails_closed",
    "test_nonincrementing_application_start_count_fails_closed",
    "test_wrong_prior_process_binding_fails_closed",
    "test_forbidden_guest_or_account_overclaim_fails_closed",
    "test_source_or_apk_hash_mismatch_fails_closed",
):
    assert name in runner_tests, "Missing E79 runner test: " + name
assert "const val supportsSplits = false" in runtime
assert "check(source.apkPaths.size == 1)" in backend
assert "--proof-level e79" in workflow and "--allow-software-emulation" in workflow
assert "python3 scripts/audit_e79_synthetic_process_restart.py" in workflow
assert "probe-payload:testDebugUnitTest" in workflow
print("PASS E79 audit: duplicate onCreate in one process is rejected; new process token advances durable start markers.")
print("PASS E79 audit: force-stop, process-death confirmation, second launch and two-stage receipt validation are required.")
print("E79 remains synthetic same-UID proof only; no ActivityThread/PackageManager registration, third-party app or product clone acceptance.")
