#!/usr/bin/env python3
"""E57: offline presentation mutations must serialize with stop/create/delete via OS lock."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
source = read("app/src/main/java/vn/fonesi/app/storage/CloneMetadataRepository.kt")
tests = read("app/src/test/java/vn/fonesi/app/storage/StorageIntegrationTest.kt")
vm = read("app/src/main/java/vn/fonesi/app/AppsViewModel.kt")
start = source.index("private fun mutatePresentation(action: () -> Unit)")
body = source[start:source.index("fun delete(", start)]
assert "operationLock.acquire()" in body and "lease.use { action() }" in body
for name in ("fun rename(", "fun restyle("):
    section = source.split(name, 1)[1].split("\n    }", 1)[0]
    assert "mutatePresentation {" in section, f"{name} must be locked"
assert source.index("private fun mutatePresentation") < source.index("fun delete(")
assert "require(engine.availability is EngineAvailability.Ready)" not in body
assert "mutateEngine(" not in body
for name in ("presentationWritesFailClosedWhileSharedOperationLockIsHeld", "presentationFailureReleasesOperationLockWithoutChangingOtherEngine"):
    assert name in tests, "missing E57 lock regression: " + name
assert "stopClone(" in vm
assert "version = 6, exportSchema = true" in read("app/src/main/java/vn/fonesi/app/storage/FonesiDatabase.kt")
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E57 rename/restyle cooperate with OS lock and stay offline-safe.")
print("PASS: native SQLite lock contention/namespace/failure regressions declared; no split activation.")
