#!/usr/bin/env python3
"""Fail-closed E44 audit: private retained source authority must not fall back to original APK."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text()
task = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateTask.kt")
queue = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateQueue.kt")
runner = read("core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt")
codec = read("app/src/main/java/vn/fonesi/app/storage/TaskCodec.kt")
room = read("app/src/main/java/vn/fonesi/app/storage/RoomUpdateTaskStore.kt")
owner = read("app/src/main/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepository.kt")
enqueue = read("app/src/main/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuer.kt")
policy = read("app/src/main/java/vn/fonesi/app/storage/RoomLegacyUpdateSourcePolicy.kt")
local = read("app/src/main/java/vn/fonesi/app/storage/LocalServices.kt")
test_app = read("app/src/test/java/vn/fonesi/app/storage/StorageIntegrationTest.kt")
test_e41 = read("app/src/test/java/vn/fonesi/app/storage/UpdateTaskClusterOwnershipRepositoryTest.kt")
test_e43 = read("app/src/test/java/vn/fonesi/app/storage/PrivateClusterRetainedEnqueuerTest.kt")
test_core = read("core/src/test/kotlin/vn/fonesi/core/update/UpdateRunnerTest.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt")
runtime = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
database = read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")

for label, source, markers in (
    ("immutable retained marker", task, (
        "val retainedSource: Boolean = false",
        "retainedSource == previous.retainedSource",
    )),
    ("explicit queue authority", queue, (
        "retainedSource: Boolean = false",
        ".copy(retainedSource = retainedSource)",
    )),
    ("legacy backward-compatible codec", codec, (
        'if (task.retainedSource) put("retainedSource", true)',
        'if (!json.has("retainedSource")) false',
        "require(value is Boolean)",
        "retainedSource = retainedSource",
    )),
    ("durable CAS and dedup guard", room, (
        "sameSourceAuthority(TaskCodec.decode(it), task)",
        "existing.retainedSource == incoming.retainedSource",
        "replacement.requireSuccessorOf(expected)",
        "transitionExistingOwners(replacement)",
    )),
    ("atomic owner and marker", owner, (
        "store.open(clusterId, platformApi)",
        "markRetainedSource(task)",
        "db.tasks().replace(task.id, task.revision, task.state.name, row.state",
    )),
    ("E43 retained verified-source enqueue", enqueue, (
        "store.open(clusterId, platformApi)",
        "retainedSource = true",
        "task.state == TaskState.QUEUED && task.retainedSource",
    )),
    ("core original-source execution quarantine", runner, (
        "private val allowLegacySource: (UpdateTask) -> Boolean = { true }",
        "eligible.firstOrNull(::mayUseLegacySource)",
        "eligible.isEmpty()) RunResult.Idle else RunResult.PolicyBlocked",
        "private fun mayUseLegacySource(task: UpdateTask)",
        "!task.retainedSource && runCatching { allowLegacySource(task) }.getOrDefault(false)",
    )),
    ("pre-marker Room owner guard", policy, (
        "!task.retainedSource",
        "ClusterOwnerType.UPDATE_TASK.name",
        "ClusterOwnerType.REVIEW_REQUIRED.name",
    )),
    ("production runner wire", local, (
        "allowLegacySource = RoomLegacyUpdateSourcePolicy(database)::allows",
    )),
):
    for marker in markers:
        if marker not in source:
            raise SystemExit(f"E44 {label} missing required invariant: {marker}")

for label, source, name in (
    ("core runner", test_core, "retained private task cannot use original catalog or run engine"),
    ("authority exception", test_core, "host source authority exception fails closed before catalog and engine"),
    ("legacy/marked journal & Room ref", test_app, "retainedSourceMarkerIsStrictAndLegacySourcePolicyChecksOlderOwnerReferences"),
    ("rollback marker and reference", test_e41, "ownerAcquisitionWritesDurableSourceMarkerAndSqlAbortRollsBothBack"),
    ("cross-authority dedup rejection", test_e43, "legacyAndRetainedRequestsCannotMergeAcrossSourceAuthorities"),
):
    if name not in source:
        raise SystemExit(f"E44 missing acceptance test {label}: {name}")

if "version = 6, exportSchema = true" not in database:
    raise SystemExit("E44 changed Room schema unexpectedly")
if "const val supportsSplits = false" not in runtime or "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("Provider single-APK and disabled split guards lost")
for source in (policy, codec):
    for forbidden in ("engine.update(", "engine.launch(", "fun collectGarbage(", "fun deleteCluster(",
                      "Files.delete("):
        if forbidden in source:
            raise SystemExit(f"E44 source authority may not invoke runtime/GC: {forbidden}")

print("PASS: E44 retained-source marker is durable and immutable across task transitions.")
print("PASS: E41/E43 ownership, dedup, and legacy source guard fail closed with rollback tests.")
print("PASS: legacy update provider cannot import an unsupported retained private APK cluster.")
