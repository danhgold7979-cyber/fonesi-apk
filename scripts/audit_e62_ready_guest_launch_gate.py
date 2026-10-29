#!/usr/bin/env python3
"""E62: only a confirmed READY instance may dispatch the native guest launch."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
engine = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
launch = engine.split("override fun launch(cloneId: String) {", 1)[1].split("override fun stop(cloneId: String)", 1)[0]
for token in (
    "val row = instance(cloneId)",
    "check(row.state == InstanceState.READY)",
    "check(record(row) != null)",
    "mutationAllowed()",
    "registry.pending(row.id, true)",
    "backend.launch(row.userId, row.packageName)",
):
    assert token in launch, "E62 missing READY launch protection: " + token
assert launch.index("check(row.state == InstanceState.READY)") < launch.index("check(record(row) != null)")
assert launch.index("check(row.state == InstanceState.READY)") < launch.index("registry.pending(row.id, true)") < launch.index("backend.launch(")
assert "row.state != InstanceState.DELETING" not in launch
for regression in (
    "reservedNativePackageReadbackCannotAuthorizeLaunch",
    "nonReadyLifecycleStatesCannotDispatchOrMarkPending",
    "launchExceptionPreservesPendingMarkerAndIsNeverRetriedAutomatically",
    "independentlySelectedInstancesDispatchDifferentNativeUsers",
):
    assert regression in tests, "Missing E62 test: " + regression
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E62 launch requires READY before readback, pending write or native dispatch.")
print("PASS: unconfirmed reservations and other lifecycle states remain unavailable for launch.")
