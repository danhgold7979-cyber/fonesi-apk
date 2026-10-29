#!/usr/bin/env python3
"""E55: durable queue cancel/retry must never mutate another engine namespace."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
queue = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateQueue.kt")
model = read("app/src/main/java/vn/fonesi/app/AppsViewModel.kt")
screen = read("app/src/main/java/vn/fonesi/app/MainActivity.kt")
cards = read("app/src/main/java/vn/fonesi/app/ManagementCards.kt")
tests = read("core/src/test/kotlin/vn/fonesi/core/update/UpdateQueueNamespaceTest.kt")
room_tests = read("app/src/test/java/vn/fonesi/app/storage/EngineScopedQueueControlTest.kt")
database = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")

for declaration, successor in (
    ("fun retryDeferred(engineKey: String, id: String): Boolean", "fun cancel("),
    ("fun cancel(engineKey: String, id: String): Boolean", "\n}"),
):
    assert declaration in queue, "E55 missing required namespaced API: " + declaration
    body = queue.split(declaration, 1)[1].split(successor, 1)[0]
    assert 'require(engineKey.isNotBlank())' in body, "E55 cannot accept an empty namespace"
    assert 'val current = store.list().firstOrNull { it.id == id } ?: return false' in body
    assert 'current.engineKey != engineKey' in body, "E55 must check durable task owner"
    assert 'store.replace(current,' in body, "E55 must use journal CAS after owner guard"
    assert body.index('current.engineKey != engineKey') < body.index('store.replace(current,')
assert "fun retryDeferred(id: String)" not in queue and "fun cancel(id: String)" not in queue
assert "services.queue.cancel(services.engineKey, id)" in model
assert "services.queue.retryDeferred(services.engineKey, id)" in model
assert "task.engineKey == model.engineKey" in screen
assert "foreignEngine = task.engineKey != model.engineKey" in screen
assert "if (foreignEngine)" in cards and "Tác vụ thuộc engine khác" in cards

for case in (
    "foreignQueuedCancellationIsRejectedWhileLocalCancellationSucceeds",
    "foreignDeferredRetryCannotChangeOwnerTaskButOwningEngineCanRetry",
    "activeAndUnknownOutcomeCannotBeControlledEvenByOwner",
    "blankEngineIdentityIsNotAnAuthorizedQueueController",
    "missingTaskDoesNotMutateAnotherEngineJournal",
):
    assert case in tests, "E55 missing JVM regression: " + case
assert "foreignRoomQueueControlsCannotErasePrivateOwnersOrRequeueOldEngineHistory" in room_tests
assert "ClusterOwnerType.UPDATE_TASK.name" in room_tests
assert "context.deleteDatabase(name)" in room_tests
assert "version = 6, exportSchema = true" in database
assert "const val supportsSplits = false" in runtime
print("PASS: E55 queue controls require an exact active engine namespace before any journal CAS.")
print("PASS: foreign-engine UI history is read-only; JVM/native Room ownership regressions present.")
print("PASS: Room v6 and candidate split-disabled boundaries are preserved.")
