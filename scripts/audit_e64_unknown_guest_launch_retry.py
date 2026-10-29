#!/usr/bin/env python3
"""E64: unknown asynchronous launch may never be re-dispatched without positive reconciliation."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
engine = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
body = engine.split("    override fun launch(cloneId: String) {", 1)[1].split("    override fun stop(cloneId: String)", 1)[0]
for required in (
    "val row = instance(cloneId)",
    "check(row.state == InstanceState.READY)",
    "val observed = checkNotNull(record(row))",
    "check(observed.running != null)",
    "mutationAllowed()",
    "registry.pending(row.id, true)",
    "backend.launch(row.userId, row.packageName)",
):
    assert required in body, "E64 native launch safety missing: " + required
assert body.index("check(row.state == InstanceState.READY)") < body.index("val observed = checkNotNull(record(row))")
assert body.index("val observed = checkNotNull(record(row))") < body.index("check(observed.running != null)")
assert body.index("check(observed.running != null)") < body.index("registry.pending(row.id, true)") < body.index("backend.launch(")
record = engine.split("    private fun record(row: GuestInstance)", 1)[1].split("    override fun listClones()", 1)[0]
assert "if (live.running && row.launchPending) registry.pending(row.id, false)" in record
assert "if (row.launchPending && !live.running) null else live.running" in record
for regression in (
    "pendingUnknownLaunchCannotDispatchTwiceBeforeVerifiedStop",
    "failedLaunchCannotBeBlindlyRetriedAcrossEngineRecreation",
    "positiveRunningReadbackResolvesUnknownAndAllowsReopenOfSameNativeGuest",
    "launchExceptionPreservesPendingMarkerAndIsNeverRetriedAutomatically",
    "reservedNativePackageReadbackCannotAuthorizeLaunch",
    "nonReadyLifecycleStatesCannotDispatchOrMarkPending",
    "positiveExactNativeStoppedReadbackAloneClearsDurablePending",
):
    assert regression in tests, "Missing E64 protocol regression: " + regression
assert "check(row.state == InstanceState.READY)" in engine.split("    private fun sharedRecords(", 1)[1]
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
print("PASS: E64 prevents blind native guest relaunch while durable pending outcome remains unknown.")
print("PASS: positive running/explicit stopped reconciliation preserve READY-only native launch protocol.")
