#!/usr/bin/env python3
"""Static safety audit for the isolated E82 one-shot host-factory authorization gate."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


auth = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/E82FactoryAuthorization.java")
tests = read("native-runtime-probe/src/test/java/vn/fonesi/nativeprobe/E82FactoryAuthorizationTest.java")
runner = read("scripts/run_guest_code_emulator.py")
python_tests = read("scripts/test_run_guest_code_emulator.py")
gradle = read("native-runtime-probe/build.gradle.kts")
manifest = read("native-runtime-probe/src/main/AndroidManifest.xml")
manifest_audit = read("scripts/run_native_probe.py")
factory = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/E82AppComponentFactory.java")
stub = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/E82HostActivityStub.java")
host = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
payload = read("probe-payload/src/main/java/vn/fonesi/payload/PayloadActivity.kt")

for marker in (
    "public static synchronized void arm(Ticket ticket)",
    "public static synchronized Ticket consume(Dispatch dispatch)",
    "pending = null;",
    "state = State.CONSUMED;",
    "ticket.matches(dispatch)",
    "ticket.privateActivityClass.getClassLoader() != ticket.privateClassLoader",
    "ticket.loadedGuestCode == null",
    "PRIVATE_ACTIVITY_CLASS = \"vn.fonesi.payload.PayloadActivity\"",
    "HOST_PROCESS = HOST_PACKAGE + \":fonesi_guest_ui\"",
):
    assert marker in auth, "E82 authorization invariant missing: " + marker

consume = auth.split("public static synchronized Ticket consume(Dispatch dispatch)", 1)[1]
assert consume.index("pending = null;") < consume.index("state = State.CONSUMED;") < consume.index("ticket.matches(dispatch)"), (
    "E82 must consume the in-memory ticket before evaluating untrusted dispatch identity"
)
assert "public static synchronized void resetForTest" not in auth, "Production code must not expose ticket reset"
assert "android.content.Intent" not in auth and "android.app.Instrumentation" not in auth, (
    "Authorization state must not be reconstructed from Intent or Instrumentation callbacks"
)
for marker in (
    "exactSignedPrivateIdentityConsumesOneShotTicket",
    "duplicateArmIsRejected",
    "wrongNonceConsumesTicketAndCannotBeRetried",
    "componentProcessUidAndApkIdentityAreAllBound",
    "invalidPrivateLoaderOrClusterCannotBeArmed",
):
    assert marker in tests, "E82 authorization regression missing: " + marker
assert 'testImplementation("junit:junit:4.13.2")' in gradle, "JUnit dependency missing"
assert 'android:appComponentFactory=".E82AppComponentFactory"' in manifest
assert 'android:name=".E82HostActivityStub" android:exported="false"' in manifest
assert 'android:process=":fonesi_guest_ui"' in manifest
for marker in (
    "@TargetApi(28)",
    "extends AppComponentFactory",
    "return super.instantiateActivity(classLoader, className, intent)",
    "E82FactoryAuthorization.consume(dispatch)",
    "Activity.class.isAssignableFrom(candidate)",
    "candidate.getClassLoader() != ticket.getPrivateClassLoader()",
    "Application.getProcessName()",
    "Process.myUid()",
    "candidate.getConstructor()",
    "constructor.newInstance()",
):
    assert marker in factory, "E82 factory invariant missing: " + marker
assert "throw new SecurityException" in stub and "super.onCreate(savedInstanceState)" in stub
assert 'PACKAGE + ".E82HostActivityStub"' in manifest_audit
assert 'stub.get(A + "exported") == "false"' in manifest_audit
assert "E82AppComponentFactory" in manifest_audit
for marker in (
    "prepareE82FactoryDispatch(",
    "E82FactoryAuthorization.arm(ticket)",
    "sha256File(hostApk).equals(probeApkSha256)",
    "sha256File(payloadApk).equals(payloadApkSha256)",
    "startActivity(dispatch)",
):
    assert marker in host, "E82 host controller gate missing: " + marker
for marker in (
    "import android.annotation.TargetApi",
    "@TargetApi(28)",
    "beginE82HostStubLifecycle()",
    "framework_on_create_called",
    "framework_on_resume_called",
    "base_context_non_null",
    "window_non_null",
    "guest_component_registered_with_host_package_manager",
    "payload_source_fixture_package_installed",
    "host_stub_activity_info_exported",
):
    assert marker in payload, "E82 signed PRIVATE Activity receipt missing: " + marker

for marker in (
    "def validate_e82_receipt(",
    "files/e82-factory-receipts/",
    "PASS_E82_HOST_STUB_FRAMEWORK_ACTIVITY_LIFECYCLE_ONLY",
    '"e80", "e82"',
):
    assert marker in runner, "E82 runner/receipt gate missing: " + marker
for marker in (
    "class E82HostStubFrameworkReceiptTests",
    "test_real_host_stub_context_and_lifecycle_receipt_passes",
    "test_factory_consumption_and_both_framework_callbacks_are_required",
    "test_guest_uid_third_party_or_production_overclaim_is_denied",
    "test_installed_payload_source_fixture_is_not_misreported_as_guest_registration",
):
    assert marker in python_tests, "E82 Python negative-claim test missing: " + marker

print("PASS E82 subgate audit: one-shot process-local authorization is identity-bound and fail-closed.")
print("NOT VERIFIED: Android AppComponentFactory dispatch/Activity attachment; this audit is not E82 acceptance.")
