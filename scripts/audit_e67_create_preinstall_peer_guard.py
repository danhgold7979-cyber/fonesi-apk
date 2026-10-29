#!/usr/bin/env python3
"""E67: native user creation must not authorize unsafe shared APK import."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda name: (root / name).read_text()
engine = read("core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/ManagedGuestEngineTest.kt")
body = engine.split("    override fun create(source: InstalledSource", 1)[1].split("    override fun launch(cloneId", 1)[0]
for token in (
    "checkCreatePeers(source)",
    "val peerSnapshot = active().filter { it.packageName == source.packageName }",
    "var row = registry.reserve(source.packageName)",
    "enteredSdk = true",
    "backend.createUser(row.userId)",
    "check(row.userId in backend.users())",
    "check(snapshots.verify(snapshot))",
    "check(instance(row.id) == row)",
    "check(backend.inspect(row.userId, row.packageName) == null)",
    "val peersBeforeInstall = active().filter { it.packageName == source.packageName && it.id != row.id }",
    "peersBeforeInstall.toSet() == peerSnapshot.toSet()",
    "peer.state == InstanceState.READY",
    "record(peer)?.let { it.running == false && it.versionCode == source.versionCode",
    "it.signerDigests == source.signerDigests",
    "backend.install(row.userId, snapshot.source)",
    "if (!enteredSdk)",
):
    assert token in body, "E67 missing safe guest pre-import validation: " + token
assert body.index("checkCreatePeers(source)") < body.index("val peerSnapshot =") < body.index("var row = registry.reserve(")
assert body.index("enteredSdk = true") < body.index("backend.createUser(") < body.index("check(snapshots.verify(snapshot))", body.index("backend.createUser("))
assert body.index("check(instance(row.id) == row)") < body.index("val peersBeforeInstall =") < body.index("backend.install(")
assert body.index("record(peer)?.let") < body.index("backend.install(")
for name in (
    "peerStartingAfterNativeUserCreationBlocksNewSharedGuestInstall",
    "peerSignatureChangingAfterNativeUserCreationBlocksNewSharedInstall",
    "removingExistingPeerAfterNativeUserCreationCannotShrinkSharedScope",
    "newReservedPeerAfterNativeUserCreationBlocksSharedImport",
    "frozenSourceInvalidatedAfterNativeUserCreationRetainsReservation",
    "unexpectedNativePackageAtReservedUserMustNotBeOverwritten",
    "createUsesFrozenSourceAndReadsBackBeforePublishingReady",
    "authorizationLostAfterFirstSdkMutationStillCompletesCreateReadback",
):
    assert name in tests, "E67 missing lifecycle test: " + name
assert "check(row.state == InstanceState.READY)" in engine.split("    private fun sharedRecords(", 1)[1]
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "check(source.apkPaths.size == 1)" in read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
print("PASS: E67 validates immutable source, reservation and exact stopped READY peers after native user creation.")
print("PASS: uncertain native create retains reservation and snapshot with no unsafe extra SDK install.")
