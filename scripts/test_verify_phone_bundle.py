#!/usr/bin/env python3
"""Tests for ZIP input integrity, not proof of Android guest execution."""
import hashlib
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

from verify_phone_bundle import FILES, validate_bundle


class TestDebugPhoneBundleAudit(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.originals = {}
        for number, name in enumerate(FILES):
            file = self.root / name
            file.write_bytes(("synthetic-test-" + name + "-" + str(number)).encode())
            self.originals[name] = file
        self.zip = self.root / "bundle.zip"

    def build(self, names=None, payload=None, append=None, manifest=None):
        sums = {name: hashlib.sha256(self.originals[name].read_bytes()).hexdigest() for name in FILES}
        if manifest is None:
            manifest = json.dumps(sums).encode()
        if names is None:
            names = list(FILES)
        with zipfile.ZipFile(self.zip, "w", zipfile.ZIP_DEFLATED) as archive:
            for name in names:
                archive.writestr(name, payload.get(name, self.originals[name].read_bytes())
                                 if payload else self.originals[name].read_bytes())
            if append:
                for name, content in append:
                    archive.writestr(name, content)
            archive.writestr("checksums.json", manifest)

    def test_valid_exact_archive_is_verified(self):
        self.build()
        report = validate_bundle(self.zip, self.originals)
        self.assertEqual(set(report["files"]), set(FILES))
        self.assertEqual(len(report["archive_sha256"]), 64)

    def test_missing_file_and_extra_entry_are_rejected(self):
        self.build(names=list(FILES)[:-1])
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)
        self.build(append=[("unexpected.apk", b"unexpected")])
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_duplicate_entry_is_rejected_even_when_set_matches(self):
        self.build(append=[(FILES[0], b"duplicate")])
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_path_traversal_entry_is_rejected(self):
        self.build(names=list(FILES)[1:], append=[("../" + FILES[0], b"escape")])
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_tampered_archive_is_not_verified(self):
        self.build(payload={FILES[0]: b"tampered apk"})
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_modified_original_is_not_verified(self):
        self.build()
        self.originals[FILES[0]].write_bytes(b"changed after bundle was created")
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_bad_checksum_and_duplicate_json_keys_are_rejected(self):
        self.build(manifest=b'{"bad": "entry"}')
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)
        item = FILES[0]
        duplicate = ('{"' + item + '":"a","' + item + '":"b"}').encode()
        self.build(manifest=duplicate)
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_symlink_members_are_rejected(self):
        self.build()
        with zipfile.ZipFile(self.zip, "w") as archive:
            info = zipfile.ZipInfo(FILES[0])
            info.create_system = 3
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(info, b"target")
            for name in FILES[1:]:
                archive.writestr(name, self.originals[name].read_bytes())
            hashes = {n: hashlib.sha256(self.originals[n].read_bytes()).hexdigest() for n in FILES}
            archive.writestr("checksums.json", json.dumps(hashes))
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)

    def test_symlink_original_is_rejected(self):
        self.build()
        alternate = self.root / "other"
        alternate.write_bytes(b"another")
        self.originals[FILES[0]].unlink()
        self.originals[FILES[0]].symlink_to(alternate)
        with self.assertRaises(ValueError):
            validate_bundle(self.zip, self.originals)


if __name__ == "__main__":
    unittest.main()
