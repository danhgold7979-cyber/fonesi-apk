#!/usr/bin/env python3
"""E78 interim safety audit: class preflight only; not runtime acceptance."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text(encoding="utf-8")
preflight = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestApplicationClassPreflight.kt")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
tests = read("engine-source/src/test/java/vn/fonesi/engine/PrivateGuestApplicationClassPreflightTest.kt")
backend = read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
assert "Class.forName(signedName, false, privateLoader)" in preflight
assert "candidate.classLoader === privateLoader" in preflight
assert "applicationBase.isAssignableFrom(candidate)" in preflight
assert "candidate.getConstructor()" in preflight
assert ".newInstance(" not in preflight and "callApplicationOnCreate" not in preflight
assert "preflightApplicationClass()" in loader
assert "componentInventory.applicationClass" in loader
assert "Application::class.java" in loader
assert "const val supportsSplits = false" in backend
for name in (
    "exactPrivateConcreteClassPassesWithoutRunningConstructor",
    "delegatedParentClassAndMissingSignedNameFailClosed",
    "invalidTypesAndConstructorsAreRefused",
    "staticInitializerIsNotRunOnSuccessfulOrFailedPreflight",
):
    assert name in tests, "Missing E78 JVM test " + name
print("PASS E78 interim static checks: signed PRIVATE Application class loading is fail-closed.")
print("Not E78 PASS: no Application attach/onCreate or Android scope receipt has been executed.")
