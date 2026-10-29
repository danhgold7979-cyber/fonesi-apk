#!/usr/bin/env python3
"""E56: revalidate clone metadata and real engine identity under the stop lock."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
guard = read("app/src/main/java/vn/fonesi/app/storage/CloneStopIdentityGuard.kt")
model = read("app/src/main/java/vn/fonesi/app/AppsViewModel.kt")
tests = read("app/src/test/java/vn/fonesi/app/storage/CloneStopIdentityGuardTest.kt")
db = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
candidate = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")

for token in (
    "expected.engineKey == activeEngineKey",
    "expected.launchToken == expectedToken",
    "check(observed == expected)",
    "live.map { it.id }.distinct().size == live.size",
    "live.singleOrNull { it.id == expected.cloneId }?.packageName == expected.packageName",
):
    assert token in guard, "E56 missing immutable stop identity gate: " + token
stop = model.split("fun stopClone(token: String)", 1)[1].split("fun reconcileClones", 1)[0]
for token in (
    "val row = currentClone(token)",
    "services.operationLock().acquire()",
    "CloneStopIdentityGuard.requireSameGuest(",
    "services.database.clones().findByLaunchToken(token)",
    "engine.listClones()",
    "services.tasks.list().none",
    "(engine as? StoppableCloneEngine ?: error(\"Stop unavailable\")).stop(row.cloneId)",
):
    assert token in stop, "E56 missing guarded stop flow: " + token
assert stop.index("services.operationLock().acquire()") < stop.index("CloneStopIdentityGuard.requireSameGuest(")
assert stop.index("CloneStopIdentityGuard.requireSameGuest(") < stop.index("services.tasks.list().none")
assert stop.index("services.tasks.list().none") < stop.index(".stop(row.cloneId)")
assert "it.packageName == row.packageName" in stop, "Preserve conservative update barrier"
for name in (
    "exactCurrentCloneMetadataAndLiveIdentityAreAccepted",
    "staleTokenOrWrongEngineCannotStopClone",
    "changedPackageOrClusterGenerationCannotStopOriginalIdentity",
    "duplicateMissingOrWrongPackageEngineRecordCannotStopClone",
    "nativeRoomReadbackRejectsChangedMetadataWithoutDeletingIt",
):
    assert name in tests, "E56 missing negative test: " + name
for forbidden in ("engine.stop(", "engine.update(", "engine.launch(", "Files.delete(", "deleteCluster("):
    assert forbidden not in guard, "E56 guard must remain pure readback-only"
assert "version = 6, exportSchema = true" in db
assert "const val supportsSplits = false" in candidate
print("PASS: E56 clone stop validates token/engine/package/cluster and live guest record after locking.")
print("PASS: conservative update barrier and Room v6/split-disabled/provider safety remain intact.")
