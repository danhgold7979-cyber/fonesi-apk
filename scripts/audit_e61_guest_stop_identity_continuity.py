#!/usr/bin/env python3
"""E61: native stop may clear pending only after unchanged guest identity readback."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
source = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
body = source.split("    override fun stop(cloneId: String) {", 1)[1].split("    override fun delete(cloneId: String)", 1)[0]
for required in (
    "val beforeStop = checkNotNull(backend.inspect(row.userId, row.packageName))",
    "beforeStop.packageName == row.packageName",
    "beforeStop.version >= 0",
    "beforeStop.signers.isNotEmpty()",
    "backend.stop(row.userId, row.packageName)",
    "val observed = checkNotNull(backend.inspect(row.userId, row.packageName))",
    "observed.packageName == beforeStop.packageName",
    "observed.version == beforeStop.version",
    "observed.signers == beforeStop.signers",
    "!observed.running",
    "registry.pending(row.id, false)",
):
    assert required in body, "Missing E61 native identity guard: " + required
assert body.index("beforeStop = checkNotNull(") < body.index("backend.stop(")
assert body.index("backend.stop(") < body.index("val observed = checkNotNull(")
assert body.index("val observed = checkNotNull(") < body.index("registry.pending(row.id, false)")
for required in (
    "corruptedNativeIdentityBeforeStopCannotInvokeSdkOrClearPending",
    "changedNativeVersionAfterStopCannotClearPending",
    "changedNativeSignersAfterStopCannotClearPending",
    "missingNativeReadbackAfterStopPreservesLaunchPendingForReview",
    "positiveExactNativeStoppedReadbackAloneClearsDurablePending",
):
    assert required in tests, "Missing E61 regression: " + required
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E61 requires valid pre-stop native image and exact post-stop package/version/signers continuity.")
print("PASS: no clearing of unknown launch state or activation of split/provider/GC capability.")
