"""Schema1–6 opaque identity projection tests; synthetic temporary databases only."""
from contextlib import contextmanager
from pathlib import Path
import sqlite3
import tempfile
import unittest

from storage_contract import Rejected, _row_json, inspect_legacy, load_schema, populate_quarantined_catalog
from test_storage_contract import fixture


@contextmanager
def databases(version):
    with tempfile.TemporaryDirectory(prefix="fonesi-opaque-catalog-") as temporary:
        source = Path(temporary) / "synthetic-source.db"
        fixture(source, version)
        target = sqlite3.connect(":memory:")
        try:
            yield source, target
        finally:
            target.close()


class CatalogIdentityProjectionTests(unittest.TestCase):
    def assert_empty_target(self, target):
        self.assertFalse(target.in_transaction)
        self.assertEqual([], target.execute("SELECT name FROM main.sqlite_master").fetchall())

    def assert_preserved(self, source, target, original_bytes, original):
        self.assertEqual(original_bytes, source.read_bytes())
        self.assertEqual(original.fingerprint, inspect_legacy(source).fingerprint)
        expected_rows = sorted((table, ordinal, _row_json(row))
                               for table, rows in original.tables.items()
                               for ordinal, row in enumerate(rows))
        self.assertEqual(expected_rows, target.execute(
            "SELECT table_name,ordinal,row_json FROM preserved_rows ORDER BY table_name,ordinal").fetchall())
        self.assertEqual((original.version, original.room_identity, original.fingerprint, "UNVERIFIED", 1),
                         target.execute("SELECT source_version,room_identity,fingerprint,provider_status,paused FROM import_receipt").fetchone())
        catalog = sorted((row["engineKey"], row["cloneId"], row["packageName"], row["label"], "UNVERIFIED", 0)
                         for row in original.tables["clone_metadata"])
        self.assertEqual(catalog, sorted(target.execute(
            "SELECT engine_key,clone_id,package_name,label,readiness,launch_allowed FROM manager_catalog").fetchall()))
        tasks = sorted((row["taskId"], row["state"], row["revision"], _row_json(row), 0)
                       for row in original.tables["update_tasks"])
        self.assertEqual(tasks, sorted(target.execute(
            "SELECT task_id,legacy_state,source_revision,payload_json,dispatch_allowed FROM quarantined_tasks").fetchall()))
        owners = [(ordinal, _row_json(row), 0)
                  for ordinal, row in enumerate(original.tables.get("cluster_references", ()))]
        self.assertEqual(owners, target.execute(
            "SELECT ordinal,owner_json,release_allowed FROM quarantined_owners ORDER BY ordinal").fetchall())
        self.assertFalse(target.in_transaction)

    def test_opaque_identity_pairs_across_six_schemas(self):
        pairs = [("", "clone"), ("engine", ""), ("", ""), (" ", " "),
                 (" engine ", " clone "), ("legacy", "00000000-0000-0000-0000-000000000001"),
                 ("legacy:not-uuid", "legacy:not-uuid"), ("a:b", "1:p"),
                 ("é", "é"), ("e\u0301", "e\u0301")]
        for version in range(1, 7):
            for engine, clone in pairs:
                with self.subTest(version=version, engine=engine, clone=clone), databases(version) as (source, target):
                    with sqlite3.connect(source) as db:
                        db.execute("UPDATE clone_metadata SET engineKey=?,cloneId=?", (engine, clone))
                    before = source.read_bytes()
                    snapshot = inspect_legacy(source)
                    projected = populate_quarantined_catalog(source, target)
                    self.assertEqual(snapshot.fingerprint, projected.fingerprint)
                    self.assert_preserved(source, target, before, snapshot)

    def test_same_clone_in_distinct_namespaces_across_six_schemas(self):
        for version in range(1, 7):
            for clone in ("", "same-clone", " "):
                with self.subTest(version=version, clone=clone), databases(version) as (source, target):
                    schema = load_schema(version)
                    fields = next(entity["fields"] for entity in schema["entities"] if entity["tableName"] == "clone_metadata")
                    columns = [field["columnName"] for field in fields]
                    with sqlite3.connect(source) as db:
                        db.row_factory = sqlite3.Row
                        db.execute("UPDATE clone_metadata SET engineKey='',cloneId=?", (clone,))
                        row = dict(db.execute("SELECT * FROM clone_metadata").fetchone())
                        row["engineKey"] = "other-engine"
                        if "launchToken" in row:
                            row["launchToken"] = "other-synthetic-route"
                        db.execute('INSERT INTO clone_metadata (' + ','.join('"'+column+'"' for column in columns)
                                   + ') VALUES (' + ','.join('?' for _ in columns) + ')', [row[column] for column in columns])
                    before = source.read_bytes()
                    snapshot = inspect_legacy(source)
                    populate_quarantined_catalog(source, target)
                    self.assertEqual({("", clone), ("other-engine", clone)}, set(target.execute(
                        "SELECT engine_key,clone_id FROM manager_catalog").fetchall()))
                    self.assert_preserved(source, target, before, snapshot)

    def test_blob_identity_rejected_with_atomic_rollback(self):
        for version in range(1, 7):
            for field in ("engineKey", "cloneId"):
                with self.subTest(version=version, field=field), databases(version) as (source, target):
                    with sqlite3.connect(source) as db:
                        db.execute('UPDATE clone_metadata SET "'+field+'"=?', (sqlite3.Binary(b"synthetic-opaque"),))
                    before = source.read_bytes()
                    snapshot = inspect_legacy(source)
                    self.assertIsInstance(snapshot.tables["clone_metadata"][0][field], bytes)
                    with self.assertRaises(Rejected):
                        populate_quarantined_catalog(source, target)
                    self.assert_empty_target(target)
                    self.assertEqual(before, source.read_bytes())
                    self.assertEqual(snapshot.fingerprint, inspect_legacy(source).fingerprint)

    def test_empty_display_fields_remain_rejected(self):
        for version in range(1, 7):
            for field in ("packageName", "label"):
                with self.subTest(version=version, field=field), databases(version) as (source, target):
                    with sqlite3.connect(source) as db:
                        db.execute('UPDATE clone_metadata SET engineKey=\'\',cloneId=\'\',"'+field+'"=\'\'')
                    before = source.read_bytes()
                    snapshot = inspect_legacy(source)
                    with self.assertRaises(Rejected):
                        populate_quarantined_catalog(source, target)
                    self.assert_empty_target(target)
                    self.assertEqual(before, source.read_bytes())
                    self.assertEqual(snapshot.fingerprint, inspect_legacy(source).fingerprint)

    def test_fault_after_empty_identity_catalog_rolls_back(self):
        for version in range(1, 7):
            with self.subTest(version=version), databases(version) as (source, target):
                with sqlite3.connect(source) as db:
                    db.execute("UPDATE clone_metadata SET engineKey='',cloneId=''")
                before = source.read_bytes()
                snapshot = inspect_legacy(source)
                reached = []

                def fault(point):
                    if point == "after_catalog":
                        reached.append(point)
                        self.assertEqual(("", "", "UNVERIFIED", 0), target.execute(
                            "SELECT engine_key,clone_id,readiness,launch_allowed FROM manager_catalog").fetchone())
                        raise RuntimeError("synthetic after-catalog failure")

                with self.assertRaisesRegex(RuntimeError, "synthetic after-catalog failure"):
                    populate_quarantined_catalog(source, target, fault)
                self.assertEqual(["after_catalog"], reached)
                self.assert_empty_target(target)
                self.assertEqual(before, source.read_bytes())
                self.assertEqual(snapshot.fingerprint, inspect_legacy(source).fingerprint)
                # A fresh attempt is permitted here only because the synthetic target transaction rolled back.
                populate_quarantined_catalog(source, target)
                self.assert_preserved(source, target, before, snapshot)


if __name__ == "__main__":
    unittest.main()
