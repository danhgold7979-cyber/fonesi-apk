#!/usr/bin/env python3
"""Validate the pinned E78 parent receipt before it is bundled with E82 evidence."""
import argparse
import json
import re
from pathlib import Path

EXPECTED_SOURCE_SHA = "57d60cb80d6cece692b2898afd15d8668fd14883"
EXPECTED_RESULT = "PASS_E78_SIGNED_SYNTHETIC_APPLICATION_ATTACH_AND_SCOPED_DATA_ONLY"
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def require_flags(document, keys, expected, label):
    for key in keys:
        assert document.get(key) is expected, f"{label}.{key} must be {expected}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    evidence_path = args.directory / "e11-evidence.json"
    receipt_path = args.directory / "synthetic-ui-receipt.json"
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))

    assert evidence.get("source_commit") == EXPECTED_SOURCE_SHA
    assert evidence.get("result") == EXPECTED_RESULT
    require_flags(evidence, (
        "android_boot_verified",
        "android_apks_installed",
        "android_synthetic_application_attached",
        "android_synthetic_application_on_create_verified",
        "android_synthetic_scope_cross_readback_verified",
        "android_synthetic_scope_persistence_verified",
        "android_synthetic_scoped_storage_ab_verified",
        "android_ui_configuration_stable",
        "emulator_stopped",
    ), True, "evidence")
    require_flags(evidence, (
        "android_guest_bytecode_verified",
        "android_synthetic_component_dispatch_verified",
        "android_production_clone_accepted",
        "android_third_party_component_executed",
        "samsung_or_physical_device_tested",
        "social_apps_tested",
    ), False, "evidence")

    assert receipt.get("complete") is True and receipt.get("passed") is True
    assert receipt.get("proof_level") == "e78"
    assert receipt.get("api") == 34
    assert receipt.get("source_commit") == EXPECTED_SOURCE_SHA
    assert receipt.get("probe_apk_sha256") == evidence.get("probe_sha256")
    assert receipt.get("payload_apk_sha256") == evidence.get("payload_sha256")
    for key in ("probe_apk_sha256", "payload_apk_sha256"):
        assert SHA256.fullmatch(str(receipt.get(key, ""))), f"receipt.{key} is not SHA-256"
    require_flags(receipt, (
        "real_host_activity_lifecycle_verified",
        "signed_private_guest_view_rendered",
        "private_guest_view_attached_to_window",
        "private_guest_lifecycle_relay_verified",
        "signed_private_resources_verified",
        "synthetic_guest_application_attached",
        "synthetic_guest_application_on_create_verified",
        "synthetic_guest_application_on_main_thread",
        "synthetic_guest_application_instances_distinct",
        "synthetic_application_on_create_once_per_scope",
        "synthetic_scope_a_preferences_file_sqlite_verified",
        "synthetic_scope_b_preferences_file_sqlite_verified",
        "synthetic_scope_persistence_after_context_reconstruction_verified",
        "synthetic_scope_cross_readback_isolated",
        "same_android_uid",
    ), True, "receipt")
    require_flags(receipt, (
        "private_component_manifest_verified",
        "guest_application_registered_with_activity_thread",
        "guest_application_registered_with_package_manager",
        "full_guest_application_lifecycle_verified",
        "synthetic_component_dispatch_verified",
        "production_clone_accepted",
        "third_party_component_executed",
        "real_guest_activity_registered",
        "kernel_uid_isolation",
        "social_accounts_tested",
        "actual_guest_activity_attached",
        "third_party_guest_components_tested",
    ), False, "receipt")
    assert receipt.get("uid") == 10142
    assert evidence.get("emulator_acceleration") == "software"
    print("PASS: pinned E78 parent receipt verified against exact source SHA and APK hashes.")
    print("PASS: synthetic same-UID scope proof retained; guest registration, UID isolation, third-party execution and physical-device claims remain false.")


if __name__ == "__main__":
    main()
