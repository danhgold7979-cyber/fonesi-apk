#!/usr/bin/env python3
"""E71: signed private explicit Activity selection rejects unmediated process declarations."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
resolver = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolver.kt")
tests = read("apk-runtime/src/test/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolverTest.kt")
assert "component.processName == null && target.processName == null" in resolver
selection = resolver.split("fun select(", 1)[1]
assert selection.index("component.processName == null") < selection.index("return VerifiedPrivateActivitySelection(")
for name in ("explicitActivityProcessOverrideIsRejectedForEveryCaller",
             "signedAliasProcessOverrideCannotBypassMissingProcessMediator",
             "signedAliasCannotSelectActivityTargetWithUnmediatedProcess",
             "exportedSignedAliasSelectsExactlyItsPrivateActivityTarget"):
    assert name in tests, name
for forbidden in ("startActivity(", "getLaunchIntentForPackage(", "Intent(", "installPackage("):
    assert forbidden not in resolver
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E71 refuses unmediated Activity or alias process overrides without dispatch.")
