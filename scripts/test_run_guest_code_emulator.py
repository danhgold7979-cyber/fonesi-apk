#!/usr/bin/env python3
"""Deterministic tests for E77's bounded Android UI fingerprint reader."""
import unittest
from types import SimpleNamespace

from run_guest_code_emulator import (
    UI_CONFIGURATION_COMMAND,
    UI_CONFIGURATION_READ_TIMEOUT_SECONDS,
    PAYLOAD_PACKAGE,
    PROBE_PACKAGE,
    read_ui_configuration,
    software_emulation_proof_allowed,
    validate_e78_receipt,
    validate_e79_receipts,
)


VALID_OUTPUT = """[wm-size]
Physical size: 1080x1920
Override size: 480x800
[wm-density]
Physical density: 420
font-scale=1.0
user-rotation=0
locale=en-US
language=en
country=US
"""


class UiConfigurationReaderTests(unittest.TestCase):
    def test_reads_all_seven_values_with_one_adb_shell_command(self):
        calls = []

        def fake_device(*args, **kwargs):
            calls.append((args, kwargs))
            return SimpleNamespace(returncode=0, stdout=VALID_OUTPUT)

        snapshot = read_ui_configuration(fake_device)

        self.assertEqual(
            snapshot,
            (
                "[wm-size]",
                "Physical size: 1080x1920",
                "Override size: 480x800",
                "[wm-density]",
                "Physical density: 420",
                "font-scale=1.0",
                "user-rotation=0",
                "locale=en-US",
                "language=en",
                "country=US",
            ),
        )
        self.assertEqual(len(calls), 1)
        args, kwargs = calls[0]
        self.assertEqual(args, ("shell", UI_CONFIGURATION_COMMAND))
        self.assertEqual(kwargs, {"timeout": UI_CONFIGURATION_READ_TIMEOUT_SECONDS, "check": False})
        for required in (
            "wm size",
            "wm density",
            "settings get system font_scale",
            "settings get system user_rotation",
            "getprop persist.sys.locale",
            "getprop persist.sys.language",
            "getprop persist.sys.country",
        ):
            self.assertIn(required, UI_CONFIGURATION_COMMAND)

    def test_nonzero_adb_exit_fails_closed(self):
        def failed_device(*args, **kwargs):
            return SimpleNamespace(returncode=1, stdout="partial")

        with self.assertRaisesRegex(RuntimeError, "Cannot read Android UI configuration"):
            read_ui_configuration(failed_device)

    def test_empty_fingerprint_fails_closed(self):
        def empty_device(*args, **kwargs):
            return SimpleNamespace(returncode=0, stdout="\n  \n")

        with self.assertRaisesRegex(RuntimeError, "fingerprint is empty"):
            read_ui_configuration(empty_device)

    def test_shell_environment_contamination_is_rejected(self):
        contaminated = "BASHPID=123\nEPOCHREALTIME=1.2\n" + VALID_OUTPUT

        def fake_device(*args, **kwargs):
            return SimpleNamespace(returncode=0, stdout=contaminated)

        with self.assertRaisesRegex(RuntimeError, "shell environment"):
            read_ui_configuration(fake_device)

    def test_missing_or_duplicate_scalar_marker_is_rejected(self):
        missing = VALID_OUTPUT.replace("locale=en-US\n", "")
        duplicate = VALID_OUTPUT.replace("country=US\n", "country=US\ncountry=US\n")

        for output in (missing, duplicate):
            with self.subTest(output=output):
                def fake_device(*args, **kwargs):
                    return SimpleNamespace(returncode=0, stdout=output)

                with self.assertRaisesRegex(RuntimeError, "exactly one"):
                    read_ui_configuration(fake_device)

    def test_per_sample_timeout_can_be_reduced_to_remaining_window(self):
        calls = []

        def fake_device(*args, **kwargs):
            calls.append(kwargs)
            return SimpleNamespace(returncode=0, stdout=VALID_OUTPUT)

        read_ui_configuration(fake_device, timeout_seconds=3)
        self.assertEqual(calls, [{"timeout": 3, "check": False}])

    def test_unbounded_timeout_is_rejected_before_adb_call(self):
        calls = []

        def fake_device(*args, **kwargs):
            calls.append((args, kwargs))
            return SimpleNamespace(returncode=0, stdout=VALID_OUTPUT)

        with self.assertRaises(ValueError):
            read_ui_configuration(fake_device, timeout_seconds=21)
        self.assertEqual(calls, [])



class E78ReceiptValidationTests(unittest.TestCase):
    def valid_receipt(self):
        scope_a = "11111111-1111-4111-8111-111111111111"
        scope_b = "22222222-2222-4222-8222-222222222222"
        report = {
            "complete": True,
            "passed": True,
            "proof_level": "e78",
            "real_host_activity_lifecycle_verified": True,
            "signed_private_guest_view_rendered": True,
            "private_guest_view_attached_to_window": True,
            "private_guest_lifecycle_relay_verified": True,
            "signed_private_resources_verified": True,
            "signed_private_image_rechecked": True,
            "same_android_uid": True,
            "private_activity_class_preflight_verified": True,
            "private_activity_constructor_preflight_verified": True,
            "private_application_class_preflight_verified": True,
            "synthetic_guest_application_attached": True,
            "synthetic_guest_application_on_create_verified": True,
            "synthetic_guest_application_on_main_thread": True,
            "synthetic_guest_application_instances_distinct": True,
            "synthetic_application_on_create_once_per_scope": True,
            "synthetic_scope_a_preferences_file_sqlite_verified": True,
            "synthetic_scope_b_preferences_file_sqlite_verified": True,
            "synthetic_scope_persistence_after_context_reconstruction_verified": True,
            "synthetic_scope_cross_readback_isolated": True,
            "guest_application_registered_with_activity_thread": False,
            "guest_application_registered_with_package_manager": False,
            "full_guest_application_lifecycle_verified": False,
            "actual_guest_activity_attached": False,
            "real_guest_activity_registered": False,
            "third_party_component_executed": False,
            "third_party_guest_components_tested": False,
            "kernel_uid_isolation": False,
            "social_accounts_tested": False,
            "production_clone_accepted": False,
            "source_commit": "a" * 40,
            "probe_apk_sha256": "b" * 64,
            "payload_apk_sha256": "c" * 64,
            "synthetic_application_class_name": PAYLOAD_PACKAGE + ".SyntheticGuestApplication",
            "api": 34,
            "pid": 1234,
            "uid": 10123,
            "process_name": PROBE_PACKAGE + ":fonesi_guest_ui",
            "run_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            "synthetic_scope_a_id": scope_a,
            "synthetic_scope_b_id": scope_b,
            "lifecycle_transcript": "CREATE,START,RESUME,PAUSE,STOP,DESTROY",
        }
        return report

    def test_valid_exact_source_application_and_two_scope_receipt_passes(self):
        report = self.valid_receipt()
        self.assertEqual(
            validate_e78_receipt(report, "a" * 40, "b" * 64, "c" * 64),
            (report["synthetic_scope_a_id"], report["synthetic_scope_b_id"]),
        )

    def test_source_or_apk_hash_mismatch_fails_closed(self):
        report = self.valid_receipt()
        with self.assertRaisesRegex(RuntimeError, "source SHA"):
            validate_e78_receipt(report, "d" * 40, "b" * 64, "c" * 64)
        with self.assertRaisesRegex(RuntimeError, "host probe APK"):
            validate_e78_receipt(report, "a" * 40, "d" * 64, "c" * 64)
        with self.assertRaisesRegex(RuntimeError, "payload APK"):
            validate_e78_receipt(report, "a" * 40, "b" * 64, "d" * 64)

    def test_missing_application_or_scope_evidence_fails_closed(self):
        report = self.valid_receipt()
        report["synthetic_guest_application_on_create_verified"] = False
        with self.assertRaisesRegex(RuntimeError, "synthetic_guest_application_on_create_verified"):
            validate_e78_receipt(report, "a" * 40, "b" * 64, "c" * 64)

    def test_guest_registration_or_uid_isolation_overclaim_fails_closed(self):
        report = self.valid_receipt()
        report["guest_application_registered_with_activity_thread"] = True
        with self.assertRaisesRegex(RuntimeError, "guest_application_registered_with_activity_thread"):
            validate_e78_receipt(report, "a" * 40, "b" * 64, "c" * 64)

    def test_scope_ids_must_be_distinct_canonical_uuids(self):
        report = self.valid_receipt()
        report["synthetic_scope_b_id"] = report["synthetic_scope_a_id"]
        with self.assertRaisesRegex(RuntimeError, "distinct canonical UUIDs"):
            validate_e78_receipt(report, "a" * 40, "b" * 64, "c" * 64)

    def test_software_emulation_is_limited_to_e77_e78_and_e79(self):
        self.assertTrue(software_emulation_proof_allowed("e77"))
        self.assertTrue(software_emulation_proof_allowed("e78"))
        self.assertTrue(software_emulation_proof_allowed("e79"))
        for proof in ("e17", "e24", "e74", "e76"):
            self.assertFalse(software_emulation_proof_allowed(proof))



class E79ProcessRestartReceiptTests(unittest.TestCase):
    def stage(self, stage):
        write = stage == "write"
        report = {
            "complete": True, "passed": True, "proof_level": "e79", "e79_stage": stage,
            "real_host_activity_lifecycle_verified": True,
            "signed_private_guest_view_rendered": True, "private_guest_view_attached_to_window": True,
            "private_guest_lifecycle_relay_verified": True, "signed_private_resources_verified": True,
            "signed_private_image_rechecked": True, "same_android_uid": True,
            "private_activity_class_preflight_verified": True,
            "private_activity_constructor_preflight_verified": True,
            "private_application_class_preflight_verified": True,
            "synthetic_guest_application_attached": True,
            "synthetic_guest_application_on_create_verified": True,
            "synthetic_guest_application_on_main_thread": True,
            "synthetic_guest_application_instances_distinct": True,
            "synthetic_application_on_create_once_per_scope": True,
            "synthetic_scope_a_preferences_file_sqlite_verified": True,
            "synthetic_scope_b_preferences_file_sqlite_verified": True,
            "synthetic_scope_persistence_after_context_reconstruction_verified": True,
            "synthetic_scope_cross_readback_isolated": True,
            "guest_application_registered_with_activity_thread": False,
            "guest_application_registered_with_package_manager": False,
            "full_guest_application_lifecycle_verified": False,
            "actual_guest_activity_attached": False, "real_guest_activity_registered": False,
            "third_party_component_executed": False, "third_party_guest_components_tested": False,
            "kernel_uid_isolation": False, "social_accounts_tested": False,
            "production_clone_accepted": False,
            "source_commit": "a" * 40, "probe_apk_sha256": "b" * 64, "payload_apk_sha256": "c" * 64,
            "synthetic_application_class_name": PAYLOAD_PACKAGE + ".SyntheticGuestApplication",
            "api": 34, "pid": 1001 if write else 1002, "uid": 10101,
            "process_name": PROBE_PACKAGE + ":fonesi_guest_ui",
            "run_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee" if write else "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
            "synthetic_scope_a_id": "11111111-1111-4111-8111-111111111111",
            "synthetic_scope_b_id": "22222222-2222-4222-8222-222222222222",
            "lifecycle_transcript": "CREATE,START,RESUME,PAUSE,STOP,DESTROY",
            "e79_process_token": "33333333-3333-4333-8333-333333333333" if write else "44444444-4444-4444-8444-444444444444",
            "e79_marker_a": "a" * 32, "e79_marker_b": "b" * 32,
            "e79_start_count_a": 1 if write else 2,
            "e79_start_count_b": 1 if write else 2,
            "e79_process_restart_verified": not write,
            "e79_scoped_data_after_process_restart_verified": not write,
        }
        if not write:
            report.update({
                "e79_prior_pid": 1001,
                "e79_prior_process_token": "33333333-3333-4333-8333-333333333333",
                "e79_prior_run_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            })
        return report

    def test_valid_two_process_receipts_pass(self):
        write, verify = self.stage("write"), self.stage("verify")
        self.assertEqual(
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64),
            (write, verify),
        )

    def test_same_pid_or_process_token_fails_closed(self):
        write, verify = self.stage("write"), self.stage("verify")
        verify["pid"] = write["pid"]
        with self.assertRaisesRegex(RuntimeError, "reused the original process identity"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64)
        verify = self.stage("verify")
        verify["e79_process_token"] = write["e79_process_token"]
        with self.assertRaisesRegex(RuntimeError, "reused the original process identity"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64)

    def test_changed_scope_or_marker_after_restart_fails_closed(self):
        write, verify = self.stage("write"), self.stage("verify")
        verify["e79_marker_b"] = "c" * 32
        with self.assertRaisesRegex(RuntimeError, "changed persisted scope identity/marker"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64)

    def test_nonincrementing_application_start_count_fails_closed(self):
        write, verify = self.stage("write"), self.stage("verify")
        verify["e79_start_count_a"] = 1
        with self.assertRaisesRegex(RuntimeError, "onCreate count mismatch"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64)

    def test_wrong_prior_process_binding_fails_closed(self):
        write, verify = self.stage("write"), self.stage("verify")
        verify["e79_prior_run_id"] = "cccccccc-dddd-4eee-8fff-000000000000"
        with self.assertRaisesRegex(RuntimeError, "not bound to completed write-stage process"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64)

    def test_forbidden_guest_or_account_overclaim_fails_closed(self):
        write, verify = self.stage("write"), self.stage("verify")
        verify["guest_application_registered_with_activity_thread"] = True
        with self.assertRaisesRegex(RuntimeError, "E79 stage assertions failed"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "c" * 64)

    def test_source_or_apk_hash_mismatch_fails_closed(self):
        write, verify = self.stage("write"), self.stage("verify")
        with self.assertRaisesRegex(RuntimeError, "source SHA"):
            validate_e79_receipts(write, verify, "d" * 40, "b" * 64, "c" * 64)
        with self.assertRaisesRegex(RuntimeError, "host APK SHA"):
            validate_e79_receipts(write, verify, "a" * 40, "d" * 64, "c" * 64)
        with self.assertRaisesRegex(RuntimeError, "payload APK SHA"):
            validate_e79_receipts(write, verify, "a" * 40, "b" * 64, "d" * 64)



if __name__ == "__main__":
    unittest.main(verbosity=2)
