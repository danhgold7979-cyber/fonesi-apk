#!/usr/bin/env python3
"""E73: bind signed PRIVATE Activity identity to actual private class/type, never launch it."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda path: (root / path).read_text()
guard = read("engine-source/src/main/java/vn/fonesi/engine/PrivateGuestActivityClassPreflight.kt")
loader = read("engine-source/src/main/java/vn/fonesi/engine/GuestCodeLoader.kt")
tests = read("engine-source/src/test/java/vn/fonesi/engine/PrivateGuestActivityClassPreflightTest.kt")
resolver = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/SignedPrivateExplicitActivityResolver.kt")
catalog = read("apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkComponentCatalog.kt")

for token in (
    "Class.forName(selection.targetActivityName, false, privateLoader)",
    "candidate.name == selection.targetActivityName",
    "candidate.classLoader === privateLoader",
    "activityBase.isAssignableFrom(candidate)",
    "Modifier.isPublic(candidate.modifiers)",
    "!Modifier.isAbstract(candidate.modifiers)",
    "!candidate.isMemberClass || Modifier.isStatic(candidate.modifiers)",
    "return candidate",
):
    assert token in guard, "E73 private bytecode guard missing: " + token
check = guard.split("fun requireMatches(", 1)[1]
assert check.index("candidate.name == selection.targetActivityName") < check.index("candidate.classLoader === privateLoader")
assert check.index("candidate.classLoader === privateLoader") < check.index("return candidate")
for token in (
    "fun preflightExplicitActivityClass(",
    "val signed = selectExplicitActivity(className, caller)",
    "PrivateGuestActivityClassPreflight.resolve(",
    "signed, classLoader, Activity::class.java",
    "componentInventory.versionCode == version",
    "data class PrivateGuestActivityClassHandle(",
):
    assert token in loader, "E73 loaded guest class binding missing: " + token
for test in (
    "concreteActivityClassWithExactDefiningLoaderPassesPreflightWithoutInstantiation",
    "signedAliasPreflightsItsConcreteTargetInsteadOfAnAliasClass",
    "candidateWithDifferentNameCannotImpersonateSignedTarget",
    "parentDelegatedHostClassCannotImpersonatePrivateClassloaderDefinition",
    "unrelatedClassAndActivityBaseItselfAreNotConcreteGuestActivities",
    "abstractOrNonStaticInnerActivityCannotBeUsedAsGuestEntrypoint",
    "missingPrivateActivityClassNeverFallsBackToInstalledOriginalOrHost",
    "realSignedPrivateManifestWithoutDexIsNotAnExecutableGuestActivity",
):
    assert test in tests, "E73 JVM regression missing: " + test
assert "PrivateApkComponentCatalog.inspect(store, cluster.id, 34)" in tests
for forbidden in ("startActivity(", "Instrumentation.newActivity(", "getLaunchIntentForPackage(", "createPackageContext(", "newInstance(", "onCreate("):
    assert forbidden not in guard, "E73 must not execute guests: " + forbidden
assert "inventory.applicationProcessName == null" in resolver
assert "component.processName == null && target.processName == null" in resolver
assert "require(store.open(clusterId, platformApi) == signed)" in catalog
assert "const val supportsSplits = false" in read("app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt")
print("PASS: E73 selected signed Activity must be a concrete type defined by PRIVATE loader, not host.")
print("PASS: JVM protocol only; no Android Activity launch, split provider or account-isolation claim.")
