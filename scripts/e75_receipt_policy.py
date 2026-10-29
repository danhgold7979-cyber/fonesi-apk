"""Strict E75 synthetic Android receipt policy. This never runs a guest or emulator."""
from uuid import UUID

TRUE = (
    "complete",
    "passed",
    "real_host_activity_lifecycle_verified",
    "signed_private_guest_view_rendered",
    "private_guest_view_attached_to_window",
    "private_guest_lifecycle_relay_verified",
    "signed_private_resources_verified",
    "signed_private_image_rechecked",
    "private_activity_class_preflight_verified",
    "private_activity_instance_constructed",
    "private_activity_instance_unattached",
    "same_android_uid",
)
FALSE = (
    "kernel_uid_isolation",
    "actual_guest_activity_attached",
    "real_guest_activity_registered",
    "production_clone_accepted",
    "third_party_component_executed",
    "third_party_guest_components_tested",
    "social_accounts_tested",
)
TRANSCRIPT = "CREATE,START,RESUME,PAUSE,STOP,DESTROY"
HOST_PROCESS = "vn.fonesi.nativeprobe:fonesi_guest_ui"


def require_e75_constructor_receipt(receipt, expected_run_id, expected_process=HOST_PROCESS):
    """Validate an actual-device *host-only* E75 receipt; reject missing/spoofed claims.

    Passing this data-contract check alone is NOT proof an Android device ran.
    The caller must already have obtained a complete fresh receipt from its owned AVD.
    """
    if type(receipt) is not dict:
        raise ValueError("E75 receipt is not an object")
    if type(expected_run_id) is not str or str(UUID(expected_run_id)) != expected_run_id:
        raise ValueError("Noncanonical expected E75 run UUID")
    if receipt.get("run_id") != expected_run_id:
        raise ValueError("E75 receipt UUID differs from launched host run")
    if receipt.get("proof_level") != "e75":
        raise ValueError("E75 receipt proof level mismatch")
    if receipt.get("process_name") != expected_process or expected_process != HOST_PROCESS:
        raise ValueError("E75 requires the exact dedicated synthetic host process")
    if receipt.get("lifecycle_transcript") != TRANSCRIPT:
        raise ValueError("Synthetic host Activity lifecycle transcript incomplete")
    if type(receipt.get("api")) is not int or receipt["api"] != 34:
        raise ValueError("Unexpected Android API in E75 host receipt")
    for name in ("pid", "uid"):
        if type(receipt.get(name)) is not int or receipt[name] <= 0:
            raise ValueError("E75 host " + name + " is missing/invalid")
    for name in TRUE:
        if receipt.get(name) is not True:
            raise ValueError("E75 missing required positive proof: " + name)
    for name in FALSE:
        if receipt.get(name) is not False:
            raise ValueError("E75 missing explicit negative guest/UID/account claim: " + name)
    return True
