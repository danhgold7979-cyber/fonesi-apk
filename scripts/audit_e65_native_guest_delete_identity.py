#!/usr/bin/env python3
"""E65: native guest deletion must revalidate image identity before DELETING/uninstall."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
engine = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
body = engine.split("    override fun delete(cloneId: String) {", 1)[1].split("    override fun update(target: UpdateTarget)", 1)[0]
for token in (
    "val row = instance(cloneId)",
    "val live = record(row)",
    "check(live == null || live.running == false)",
    "mutationAllowed()",
    "val beforeDelete = backend.inspect(row.userId, row.packageName)",
    "if (live == null)",
    "check(beforeDelete == null)",
    "beforeDelete.packageName == live.packageName",
    "beforeDelete.version == live.versionCode",
    "beforeDelete.signers == live.signerDigests",
    "!beforeDelete.running",
    "registry.state(row.id, InstanceState.DELETING)",
    "if (live != null) backend.uninstall(row.userId, row.packageName)",
    "check(backend.inspect(row.userId, row.packageName) == null)",
    "registry.state(row.id, InstanceState.DELETED)",
):
    assert token in body, "E65 missing delete guard: " + token
assert body.index("val live = record(row)") < body.index("mutationAllowed()") < body.index("val beforeDelete = backend.inspect(")
assert body.index("val beforeDelete = backend.inspect(") < body.index("registry.state(row.id, InstanceState.DELETING)") < body.index("backend.uninstall(")
assert body.index("backend.uninstall(") < body.index("check(backend.inspect(row.userId, row.packageName) == null)") < body.index("registry.state(row.id, InstanceState.DELETED)")
for name in (
    "changedNativeVersionBeforeDeleteDispatchDoesNotRemoveGuest",
    "changedNativeSigningIdentityBeforeDeleteDispatchDoesNotRemoveGuest",
    "guestBecomingRunningBeforeDeleteDispatchCannotBeUninstalled",
    "nativeGuestAppearingAfterInitiallyMissingReadbackRequiresReconciliation",
    "consistentlyAbsentNativeGuestCanRetireWithoutSdkUninstall",
    "deleteOnlyAddressesSelectedUserAndDoesNotReuseItsReservation",
    "asynchronousLaunchRemainsUnknownAcrossEngineRecreationUntilConfirmedStop",
):
    assert name in tests, "E65 JVM regression missing: " + name
assert "check(observed.running != null)" in engine.split("    override fun launch(cloneId",1)[1].split("    override fun stop(cloneId",1)[0]
assert "check(row.state == InstanceState.READY)" in engine.split("    private fun sharedRecords(",1)[1]
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
print("PASS: E65 requires identity-continuous, stopped native guest before deletion dispatch.")
print("PASS: originally absent guest must remain absent; post-uninstall readback and ID reservation remain.")
