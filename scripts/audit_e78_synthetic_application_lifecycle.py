#!/usr/bin/env python3
"""E78: enforce signed synthetic Application attach and scoped-data proof without product overclaims."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text(encoding="utf-8")
preflight = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestApplicationClassPreflight.kt")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
tests = read("engine-source/src/test/java/vn/fonesi/engine/PrivateGuestApplicationClassPreflightTest.kt")
probe = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
payload_app = read("probe-payload/src/main/java/vn/fonesi/payload/SyntheticGuestApplication.java")
scoped_context = read("engine-source/src/main/java/vn/fonesi/engine/SyntheticGuestScopedContext.kt")
scoped_entry = read("probe-payload/src/main/java/vn/fonesi/payload/ScopedProbeEntrypoint.java")
runner = read("scripts/run_guest_code_emulator.py")
runner_tests = read("scripts/test_run_guest_code_emulator.py")
workflow = read(".github/workflows/e78-synthetic-application-lifecycle.yml")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")

# Preflight is deliberately non-initializing and never invokes the constructor.
assert "Class.forName(signedName, false, privateLoader)" in preflight
assert "candidate.classLoader === privateLoader" in preflight
assert "applicationBase.isAssignableFrom(candidate)" in preflight
assert "candidate.getConstructor()" in preflight
assert ".newInstance(" not in preflight and "callApplicationOnCreate" not in preflight
assert "preflightApplicationClass()" in loader
assert "componentInventory.applicationClass" in loader
assert "Application::class.java" in loader

# E78 runtime deliberately attaches only a project-owned synthetic Application with public APIs.
assert "Instrumentation instrumentationA = new Instrumentation()" in probe
assert "Instrumentation instrumentationB = new Instrumentation()" in probe
assert "newApplication(" in probe and "callApplicationOnCreate(applications[0])" in probe
assert "callApplicationOnCreate(applications[1])" in probe
assert "runOnUiThread(() ->" in probe and "attached.await(45, TimeUnit.SECONDS)" in probe
assert "applications[0].getBaseContext() == contextA" in probe
assert "applications[1].getBaseContext() == contextB" in probe
assert "syntheticApplicationInstancesDistinct = true" in probe
assert "syntheticScopePersistenceVerified = true" in probe
assert "syntheticScopeCrossReadbackVerified = true" in probe
assert "guest_application_registered_with_activity_thread" in probe
assert "guest_application_registered_with_package_manager" in probe
assert "full_guest_application_lifecycle_verified" in probe
assert "source_commit" in probe and "probe_apk_sha256" in probe and "payload_apk_sha256" in probe
assert 'Class.forName("android.app.ActivityThread"' not in probe
assert "applications[0].onCreate()" not in probe and "applications[1].onCreate()" not in probe

# Payload checks the signed code/resources and records exactly one onCreate per fresh scope.
assert "getBaseContext()" in payload_app
assert "getClass().getClassLoader() != base.getClassLoader()" in payload_app
assert "SyntheticApplicationProcessMarker.advance(" in payload_app
assert "private static final String PROCESS_TOKEN = UUID.randomUUID().toString()" in payload_app
assert 'putInt("starts", next.getStarts())' in payload_app
assert 'putInt("last_pid", next.getPid())' in payload_app
assert 'putString("last_process_token", next.getProcessToken())' in payload_app
assert "SYNTHETIC TEST PAYLOAD ONLY" in scoped_context
assert "Same UID means this is" in scoped_context
assert "ScopedProbeEntrypoint.class.getClassLoader() != guest.getClassLoader()" in scoped_entry
assert "SQLiteDatabase.openOrCreateDatabase" in scoped_entry
assert "SharedPreferences/file/SQLite" in probe or "SharedPreferences/file/SQLite" in read("docs/phases/E78_SYNTHETIC_APPLICATION_LIFECYCLE_PLAN.md")

for name in (
    "exactPrivateConcreteApplicationPassesWithoutRunningConstructor",
    "delegatedParentClassAndMissingSignedNameFailClosed",
    "wrongBaseAbstractNonPublicAndInnerTypesAreRejected",
    "parameterizedPrivateAndProtectedConstructorsAreRejected",
    "staticInitializerIsNotRunOnSuccessfulOrFailedPreflight",
):
    assert name in tests, "Missing E78 JVM regression: " + name
for name in (
    "test_valid_exact_source_application_and_two_scope_receipt_passes",
    "test_source_or_apk_hash_mismatch_fails_closed",
    "test_missing_application_or_scope_evidence_fails_closed",
    "test_guest_registration_or_uid_isolation_overclaim_fails_closed",
    "test_scope_ids_must_be_distinct_canonical_uuids",
    "test_software_emulation_is_limited_to_e77_e78_and_e79",
):
    assert name in runner_tests, "Missing E78 runner regression: " + name
assert "def validate_e78_receipt(" in runner
assert "PASS_E78_SIGNED_SYNTHETIC_APPLICATION_ATTACH_AND_SCOPED_DATA_ONLY" in runner
assert "def software_emulation_proof_allowed(" in runner
assert "const val supportsSplits = false" in backend
assert "--proof-level e78" in workflow
assert "--allow-software-emulation" in workflow
assert "python3 scripts/prepare_native_emulator.py --install --image aosp34" in workflow
assert "python3 scripts/audit_e78_synthetic_application_lifecycle.py" in workflow
assert "python3 scripts/test_run_guest_code_emulator.py" in workflow
assert "synthetic_guest_application_on_create_verified" in workflow
print("PASS E78 static audit: signed Application preflight is non-initializing; runtime uses public Instrumentation only.")
print("PASS E78 static audit: two UUID scopes, persistence/cross-readback and source/APK-bound receipt are required.")
print("E78 is synthetic same-process/same-UID evidence only; no ActivityThread registration, third-party app or product clone acceptance.")
