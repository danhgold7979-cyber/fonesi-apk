#!/usr/bin/env python3
"""Fail-closed audit for provider capability claims and manager status wording."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
core_engine = (ROOT / "core/src/main/kotlin/vn/fonesi/core/CloneEngine.kt").read_text()
managed_engine = (ROOT / "core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt").read_text()
runtime = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()
backend = (ROOT / "app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt").read_text()
view_model = (ROOT / "app/src/main/java/vn/fonesi/app/AppsViewModel.kt").read_text()
manager = (ROOT / "app/src/main/java/vn/fonesi/app/MainActivity.kt").read_text()

for required in (
    "data class EngineCapabilities(",
    "splitClusterImportSupported: Boolean = false",
    "guestActivityLifecycleVerified: Boolean = false",
    "guestServiceLifecycleVerified: Boolean = false",
    "guestReceiverLifecycleVerified: Boolean = false",
    "guestProviderLifecycleVerified: Boolean = false",
    "frameworkMediationVerified: Boolean = false",
    "isolationLevel: EngineIsolationLevel = EngineIsolationLevel.UNKNOWN",
    "val capabilities: EngineCapabilities get() = EngineCapabilities()",
):
    if required not in core_engine:
        raise SystemExit(f"Core capability contract missing fail-closed field: {required}")

for forbidden in (
    "splitClusterImportSupported = true",
    "guestActivityLifecycleVerified = true",
    "guestServiceLifecycleVerified = true",
    "guestReceiverLifecycleVerified = true",
    "guestProviderLifecycleVerified = true",
    "frameworkMediationVerified = true",
    "isolationLevel = EngineIsolationLevel.SEPARATE_OS_UID",
    "isolationLevel = EngineIsolationLevel.SEPARATE_GUEST_OS",
):
    if forbidden in managed_engine:
        raise SystemExit(f"Managed engine claims an unaccepted production capability: {forbidden}")

if "const val supportsSplits = false" not in runtime:
    raise SystemExit("SpaceCore candidate split support must remain disabled")
if "check(source.apkPaths.size == 1)" not in backend:
    raise SystemExit("SpaceCore backend must continue to reject split clusters")
if "capabilities = engine.capabilities" not in view_model:
    raise SystemExit("RuntimeState must expose the engine capability snapshot")
for label in (
    "Vòng đời guest Activity:",
    "Cách ly tài khoản:",
    "chưa được chứng minh",
    "chưa xác minh",
):
    if label not in manager:
        raise SystemExit(f"Manager UI is missing a truthful capability status: {label}")

print("PASS: capability defaults fail closed; EngineAvailability.Ready does not imply guest acceptance.")
print("PASS: SpaceCore split import remains disabled and the single-APK guard remains present.")
print("PASS: manager surfaces guest lifecycle and account-isolation status without claiming acceptance.")
