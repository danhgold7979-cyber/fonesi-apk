#!/usr/bin/env python3
"""No fabricated device run. Only pure E75 receipt-contract regression tests."""
import unittest
from uuid import uuid4
from e75_receipt_policy import (
    require_e75_constructor_receipt, TRUE, FALSE, TRANSCRIPT, HOST_PROCESS,
)


class SyntheticE75ReceiptPolicyTest(unittest.TestCase):
    def setUp(self):
        self.run_id = str(uuid4())
        self.receipt = {name: True for name in TRUE}
        self.receipt.update({name: False for name in FALSE})
        self.receipt.update({
            "run_id": self.run_id,
            "proof_level": "e75",
            "api": 34,
            "pid": 1234,
            "uid": 10123,
            "process_name": HOST_PROCESS,
            "lifecycle_transcript": TRANSCRIPT,
        })

    def checkRejected(self, **change):
        altered = self.receipt.copy()
        altered.update(change)
        with self.assertRaises(ValueError):
            require_e75_constructor_receipt(altered, self.run_id)

    def test_exact_synthetic_host_receipt_contract_is_valid(self):
        self.assertTrue(require_e75_constructor_receipt(self.receipt, self.run_id))

    def test_missing_class_or_constructor_or_unattached_proof_is_denied(self):
        for key in ("private_activity_class_preflight_verified",
                    "private_activity_instance_constructed",
                    "private_activity_instance_unattached"):
            with self.subTest(key=key):
                self.checkRejected(**{key: False})
                altered = self.receipt.copy()
                altered.pop(key)
                with self.assertRaises(ValueError):
                    require_e75_constructor_receipt(altered, self.run_id)

    def test_guest_activity_registration_or_attachment_or_uid_claim_is_denied(self):
        for key in FALSE:
            with self.subTest(key=key):
                self.checkRejected(**{key: True})
                altered = self.receipt.copy()
                altered.pop(key)
                with self.assertRaises(ValueError):
                    require_e75_constructor_receipt(altered, self.run_id)

    def test_synthetic_host_identity_is_not_a_guest_process(self):
        self.checkRejected(process_name="vn.fonesi.payload:guest")
        self.checkRejected(process_name="")
        with self.assertRaises(ValueError):
            require_e75_constructor_receipt(self.receipt, self.run_id, "vn.fonesi.payload:guest")

    def test_stale_or_wrong_uuid_and_wrong_proof_level_rejected(self):
        self.checkRejected(run_id=str(uuid4()))
        self.checkRejected(proof_level="e74")
        self.checkRejected(proof_level="e75-other")
        with self.assertRaises(ValueError):
            require_e75_constructor_receipt(self.receipt, self.run_id.upper())

    def test_signed_view_host_lifecycle_must_complete(self):
        self.checkRejected(lifecycle_transcript="CREATE,RESUME,DESTROY")
        self.checkRejected(real_host_activity_lifecycle_verified=False)
        self.checkRejected(signed_private_image_rechecked=False)

    def test_no_type_coercion_for_identity_fields(self):
        for key, value in (("api", "34"), ("pid", True), ("uid", "10123"),
                           ("uid", 0), ("pid", -1), ("api", 33)):
            with self.subTest(key=key, value=value):
                self.checkRejected(**{key: value})

    def test_failure_receipt_can_never_pass(self):
        self.checkRejected(passed=False)
        self.checkRejected(complete=False)
        self.checkRejected(private_activity_instance_constructed=1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
