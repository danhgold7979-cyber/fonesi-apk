#!/usr/bin/env python3
"""E75: validate Activity constructor shape without executing guest code."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
preflight = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestActivityClassPreflight.kt")
tests = read("engine-source/src/test/java/vn/fonesi/engine/PrivateGuestActivityClassPreflightTest.kt")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
assert "candidate.getConstructor()" in preflight
assert "catch (error: NoSuchMethodException)" in preflight
assert "constructor.parameterCount == 0" in preflight
assert preflight.index("candidate.getConstructor()") < preflight.index("return candidate")
for forbidden in (".newInstance(", "constructor.newInstance(", "startActivity(", "Instrumentation.newApplication("):
    assert forbidden not in preflight, "E75 must inspect, not execute/attach: " + forbidden
for name in (
    "publicZeroArgumentConstructorIsInspectedButNeverInvoked",
    "parameterizedOnlyPrivateAndProtectedConstructorsAreRejected",
    "abstractOrNonStaticInnerActivityCannotBeUsedAsGuestEntrypoint",
    "parentDelegatedHostClassCannotImpersonatePrivateClassloaderDefinition",
    "realSignedPrivateManifestWithoutDexIsNotAnExecutableGuestActivity",
):
    assert name in tests, "Missing E75 constructor/class safety test: " + name
assert "PrivateGuestActivityClassPreflight.resolve(" in loader
assert "privateLoader" in preflight and "candidate.classLoader === privateLoader" in preflight
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E75 inspects a public zero-argument constructor without invoking or attaching guest code.")
print("PASS: existing signed PRIVATE class ownership and no split/provider activation gates remain.")
