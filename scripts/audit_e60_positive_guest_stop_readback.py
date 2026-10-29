#!/usr/bin/env python3
"""E60: a guest stop requires positive exact native readback before clearing launchPending."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text()
source = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
provider = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
body = source.split("    override fun stop(cloneId: String) {", 1)[1].split("    override fun delete(cloneId: String)", 1)[0]
required = (
    "ready()", "val row = instance(cloneId)", "mutationAllowed()",
    "backend.stop(row.userId, row.packageName)",
    "val observed = checkNotNull(backend.inspect(row.userId, row.packageName))",
    "observed.packageName == row.packageName",
    "observed.version >= 0",
    "observed.signers.isNotEmpty()",
    "!observed.running",
    "registry.pending(row.id, false)",
)
for token in required:
    assert token in body, f"E60 missing positive guest-stop invariant: {token}"
assert body.index("backend.stop(") < body.index("checkNotNull(backend.inspect(")
assert body.index("checkNotNull(backend.inspect(") < body.index("observed.packageName ==")
assert body.index("observed.packageName ==") < body.index("registry.pending(row.id, false)")
assert "?.running != true" not in body
for name in (
    "missingNativeReadbackAfterStopPreservesLaunchPendingForReview",
    "wrongPackageGuestReadbackCannotConfirmStopOrClearPending",
    "invalidVersionGuestReadbackCannotConfirmStop",
    "emptySignerGuestReadbackCannotConfirmStop",
    "stillRunningNativeReadbackCannotConfirmStopEvenIfBackendReturns",
    "positiveExactNativeStoppedReadbackAloneClearsDurablePending",
    "observedRunningConfirmsDispatchAndFailedStopDoesNotClearItsState",
    "asynchronousLaunchRemainsUnknownAcrossEngineRecreationUntilConfirmedStop",
):
    assert name in tests, f"E60 missing core/JVM regression: {name}"
assert "val row = instance(cloneId)" in body
assert "version = 6, exportSchema = true" in db
assert "const val supportsSplits = false" in runtime
assert "check(source.apkPaths.size == 1)" in provider
for forbidden in ("fun collectGarbage(", "fun deleteCluster(", "Files.delete("):
    assert forbidden not in source
print("PASS: E60 native guest stop must positively confirm identity, version, signatures and not running.")
print("PASS: uncertain stop preserves durable launch-pending; production split/GC gates unchanged.")
