#!/usr/bin/env python3
"""E74: project-owned synthetic Android probe can preflight PRIVATE Activity class only."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
probe = read("native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/SyntheticGuestUiProbeActivity.java")
runner = read("scripts/run_guest_code_emulator.py")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
fixture_manifest = read("probe-payload/src/main/AndroidManifest.xml")
audit73 = read("scripts/audit_e73_private_activity_bytecode_preflight.py")

for required in (
    'private static final String SIGNED_ACTIVITY = GUEST_PACKAGE + ".PayloadActivity";',
    '!"e29".equals(proofLevel) && !"e74".equals(proofLevel) &&',
    '!"e77".equals(proofLevel) && !"e78".equals(proofLevel) && !"e79".equals(proofLevel)',
    'if ("e74".equals(proofLevel) || "e77".equals(proofLevel) || "e78".equals(proofLevel) || "e79".equals(proofLevel)) {',
    "PrivateGuestActivityClassHandle activityProof = image.preflightExplicitActivityClass(",
    "SIGNED_ACTIVITY, ExplicitActivityCaller.TRUSTED_INTERNAL",
    "activityProof.getSelection().getClusterId().equals(localCluster.getId())",
    "activityProof.getSelection().getTargetActivityName().equals(SIGNED_ACTIVITY)",
    "activityProof.getActivityClass().getClassLoader() == image.getClassLoader()",
    "getClassLoader().loadClass(SIGNED_ACTIVITY)",
    "localStore.open(localCluster.getId(), Build.VERSION.SDK_INT)",
    "privateActivityClassVerified = true",
    'if ("e74".equals(proofLevel) || "e77".equals(proofLevel) || "e78".equals(proofLevel) || "e79".equals(proofLevel))',
    'ProbeData.check(privateActivityClassVerified,',
    '.put("private_activity_class_preflight_verified", privateActivityClassVerified)',
    '.put("real_guest_activity_registered", false)',
    '.put("actual_guest_activity_attached", false)',
):
    assert required in probe, "E74 signed private Activity host probe missing: " + required
segment = probe.split('if ("e74".equals(proofLevel) || "e77".equals(proofLevel) || "e78".equals(proofLevel) || "e79".equals(proofLevel)) {', 1)[1].split("int marker =", 1)[0]
assert segment.index("image.preflightExplicitActivityClass(") < segment.index("privateActivityClassVerified = true")
for forbidden in ("startActivity(", "getLaunchIntentForPackage(", "Instrumentation.newActivity(",
                  "onCreate(", "new PayloadActivity("):
    assert forbidden not in segment, "E74 must not launch guest: " + forbidden

assert '<activity android:name=".PayloadActivity" android:exported="true">' in fixture_manifest
assert "fun preflightExplicitActivityClass(" in loader
assert "Class.forName(selection.targetActivityName, false, privateLoader)" in read(
    "engine-source/src/main/java/vn/fonesi/engine/PrivateGuestActivityClassPreflight.kt")
assert "PrivateGuestActivityClassPreflightTest" in audit73

for required in (
    '"e24", "e25", "e29", "e74", "e77", "e78", "e79"',
    '"android_private_activity_class_preflight_verified": False',
    'report.get("private_activity_class_preflight_verified") is not True',
    '"private_activity_class_preflight_verified",',
    '"real_guest_activity_registered", "production_clone_accepted"',
    '"PASS_E74_SIGNED_PRIVATE_ACTIVITY_CLASS_PREFLIGHT_ONLY"',
    '"android_private_activity_class_preflight_verified"] = True',
    'has_kvm = Path("/dev/kvm").exists() and os.access("/dev/kvm", os.R_OK | os.W_OK)',
    'if not has_kvm and not (software_emulation_proof_allowed(args.proof_level) and args.allow_software_emulation)',
    'args.allow_software_emulation and not software_emulation_proof_allowed(args.proof_level)',
):
    assert required in runner, "E74 runner source/receipt safety missing: " + required
assert "args.expected_payload_sha256" in runner and "args.expected_probe_sha256" in runner
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
print("PASS: E74 opt-in Android synthetic probe binds real signed PRIVATE Activity class without guest attach.")
print("PASS: Android E74 receipt requires exact UUID/process/flags, and KVM/phone runtime proof remains separate.")
