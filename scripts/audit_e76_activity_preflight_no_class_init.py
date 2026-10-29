#!/usr/bin/env python3
"""E76: signed Activity class metadata and constructor preflight must not initialize guest code."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
preflight = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestActivityClassPreflight.kt")
tests = read("engine-source/src/test/java/vn/fonesi/engine/PrivateGuestActivityClassPreflightTest.kt")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
assert "Class.forName(selection.targetActivityName, false, privateLoader)" in preflight
assert "candidate.getConstructor()" in preflight
assert ".newInstance(" not in preflight
assert "constructor.newInstance(" not in preflight
assert "Class.forName(selection.targetActivityName, true" not in preflight
for test in (
    "successfulPreflightDoesNotRunGuestStaticInitializer",
    "constructorShapeFailureDoesNotRunGuestStaticInitializer",
    "publicZeroArgumentConstructorIsInspectedButNeverInvoked",
    "parameterizedOnlyPrivateAndProtectedConstructorsAreRejected",
):
    assert test in tests, "E76 regression missing: " + test
assert "assertEquals(\"Preflight must not initialize the guest class\", 0, GuestStaticInitializationSentinel.count)" in tests
assert "assertEquals(\"Failed preflight must not initialize the guest class\", 0, GuestStaticInitializationSentinel.count)" in tests
assert "PrivateGuestActivityClassPreflight.resolve(" in loader
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E76 class loading and constructor reflection do not initialize or execute guest code.")
print("PASS: no Activity instantiation/attach, split provider or guest runtime activation.")
