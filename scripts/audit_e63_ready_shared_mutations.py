#!/usr/bin/env python3
"""E63: an unconfirmed native-backed reservation cannot authorize shared APK writes."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
engine = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
body = engine.split("    private fun sharedRecords(packageName: String)", 1)[1].split("    private fun checkCreatePeers(", 1)[0]
for required in (
    "active().filter { it.packageName == packageName }",
    "check(row.state != InstanceState.DELETING)",
    "check(row.state == InstanceState.READY)",
    "checkNotNull(record(row))",
):
    assert required in body, "E63 missing shared peer guard: " + required
assert body.index("check(row.state == InstanceState.READY)") < body.index("checkNotNull(record(row))")
create = engine.split("    override fun create(source: InstalledSource", 1)[1].split("    override fun launch(cloneId", 1)[0]
update = engine.split("    override fun update(target: UpdateTarget)", 1)[1].split("    /** Missing native readback", 1)[0]
peers = engine.split("    private fun checkCreatePeers(source: InstalledSource)", 1)[1].split("    private fun checkSource(", 1)[0]
assert create.index("checkCreatePeers(source)") < create.index("snapshots.prepare(taskId, source)")
assert create.count("checkCreatePeers(source)") >= 2
assert "check(sharedRecords(source.packageName).all" in peers
assert "val current = sharedRecords(target.source.packageName)" in update
assert update.index("val current = sharedRecords(") < update.index("backend.install(")
for name in (
    "reservedNativePeerBlocksAnotherCreateBeforeSnapshotOrSdkMutation",
    "reservedNativePeerBlocksSharedUpdateBeforeSdkMutation",
    "mixedReadyAndReservedNativePeersCannotNarrowOrAdvanceSharedUpdate",
    "createUsesFrozenSourceAndReadsBackBeforePublishingReady",
    "sharedUpdateImportsOnceAndVerifiesAllAffectedInstances",
    "missingRegistryPeerBlocksSharedCreateAndUpdateWithoutShrinkingScope",
    "deletingRegistryPeerBlocksSharedImportUntilReconciled",
):
    assert name in tests, "E63 missing JVM regression: " + name
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
print("PASS: E63 shared APK create/update reject native-backed RESERVED peers before readback or install.")
print("PASS: E62 launch and prior missing/DELETING peer safety and split-disabled guards remain.")
