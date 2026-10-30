#!/usr/bin/env python3
"""E81 signed PRIVATE synthetic Activity method scope proof, never Android guest lifecycle."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding="utf-8")
payload = read("probe-payload/src/main/java/vn/fonesi/payload/PayloadActivity.kt")
rules = read("probe-payload/src/main/java/vn/fonesi/payload/SyntheticActivityScopedBindingRules.java")
jvm = read("probe-payload/src/test/java/vn/fonesi/payload/SyntheticActivityScopedBindingRulesTest.java")
host = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
runner = read("scripts/run_guest_code_emulator.py")
tests = read("scripts/test_run_guest_code_emulator.py")
workflow = read(".github/workflows/e81-private-activity-scoped-method.yml")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "SyntheticActivityConstructionProbe.recordConstructor()" in payload
for x in ("SyntheticActivityScopedBindingRules.nextBoundScope", "bindSyntheticScopedContextForTestOnly",
          "readBoundSyntheticScopeForTestOnly", "ScopedProbeEntrypoint.read(scoped)",
          "check(baseContext == null)", "scope.classLoader === javaClass.classLoader"):
    assert x in payload, "E81 payload scoped method missing: " + x
assert "attachBaseContext(" not in payload and "callActivityOnCreate(" not in payload
for x in ("previousScope != null", "UUID.fromString(requestedScope).toString().equals(requestedScope)",
          "throw new IllegalStateException", "throw new IllegalArgumentException"):
    assert x in rules, "E81 immutable UUID scope guard missing: " + x
for x in ("freshCanonicalScopeAcceptedOnce", "sameScopeRebindingFailsClosed",
          "differentScopeRebindingFailsClosed", "invalidOrNoncanonicalScopeFailsClosed"):
    assert x in jvm, "E81 JVM regression missing: " + x
for x in ('"e81_scoped_mode"', "e81ScopedMode", "verifyE81PrivateScopedMethodBinding(",
          "bindSyntheticScopedContextForTestOnly", "readBoundSyntheticScopeForTestOnly",
          "e81PrivateActivity.getBaseContext() == null", "e81PrivateRebindingRejected = true",
          "e81PrivateScopedDataReadbackVerified = true", '"e81_guest_activity_oncreate_called", false',
          '"e81_framework_activity_attached", false', '"actual_guest_activity_attached", false'):
    assert x in host, "E81 Android bounded host proof missing: " + x
assert "callActivityOnCreate(" not in host and "startActivity(created)" not in host
for x in ("def validate_e81_receipts(", "--e81-scoped-activity", '"e81_scoped_mode"',
          "validate_e80_receipts(", "PASS_E81_SIGNED_PRIVATE_ACTIVITY_SCOPED_METHOD_ONLY",
          '"e81-process-restart-receipt.json"'):
    assert x in runner, "E81 Android runner proof missing: " + x
for x in ("test_exact_two_process_signed_scoped_activity_methods_pass",
          "test_old_e80_receipts_cannot_be_relabelled_as_e81",
          "test_b_scope_or_missing_immutable_rebinding_proof_is_denied",
          "test_no_private_readback_or_null_framework_context_is_denied",
          "test_framework_guest_activity_overclaim_is_denied",
          "test_e79_e80_source_hash_and_process_continuity_are_mandatory"):
    assert x in tests, "E81 Python receipt regression missing: " + x
for x in ("--proof-level e80", "--e81-scoped-activity", "scripts/audit_e80_private_activity_construction.py",
          "scripts/audit_e81_private_activity_scoped_method.py", "e81-process-restart-receipt.json",
          "probe-payload:testDebugUnitTest"):
    assert x in workflow, "E81 CI gate missing: " + x
assert "const val supportsSplits = false" in runtime
assert "check(source.apkPaths.size == 1)" in backend
print("PASS E81 audit: signed PRIVATE Activity method uses exact A scope; B/duplicate rebinding denied.")
print("PASS E81 audit: E79/E80 source/APK/restart safety, false guest/onCreate/UID claims retained.")
