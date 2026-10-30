import hashlib
import json
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from storage_contract import TargetUncertain, _row_json, Rejected, inspect_legacy, load_schema, populate_quarantined_catalog


def fixture(path: Path, version=6, transform=lambda sql: sql):
    schema = load_schema(version)
    db = sqlite3.connect(path)
    for entity in schema["entities"]:
        db.execute(transform(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"])))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for statement in schema["setupQueries"]:
        db.execute(statement)
    values = {
        "clone_metadata": dict(engineKey="legacy-spacecore", cloneId="clone-a", packageName="example.app", label="Bản thử Á",
                               groupName=None, createdAt=1, changedAt=2, launchToken="old-route-a", accentKey="teal", badgeText="★", clusterId="cluster-a"),
        "update_tasks": dict(sequence=1, taskId="task-a", packageName="example.app", state="APPLYING", revision=7,
                             activeKey="active-a", payload='{"sourceKind":"PRIVATE","marker":"synthetic-only"}'),
        "queue_control": dict(id=1, paused=1),
        "engine_instances": dict(sequence=1, cloneId="clone-a", engineKey="legacy-spacecore", packageName="example.app", state="RESERVED", launchPending=1),
        "cluster_references": dict(clusterId="cluster-a", ownerType="REVIEW_REQUIRED", ownerId="task-a", engineKey="legacy-spacecore", createdAtEpochMs=9),
    }
    for entity in schema["entities"]:
        columns = [field["columnName"] for field in entity["fields"]]
        db.execute('INSERT INTO "' + entity["tableName"] + '" (' + ','.join('"'+c+'"' for c in columns) + ') VALUES (' + ','.join('?' for _ in columns) + ')',
                   [values[entity["tableName"]][column] for column in columns])
    db.execute(f"PRAGMA user_version={version}")
    db.commit()
    db.close()


class StorageContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="fonesi-synthetic-sqlite-")
        self.root = Path(self.temp.name)
        self.source = self.root / "legacy-fixture.db"
        fixture(self.source)
        self.target = sqlite3.connect(":memory:")

    def tearDown(self):
        self.target.close()
        self.temp.cleanup()

    def mutate_fixture(self, sql, parameters=()):
        with sqlite3.connect(self.source) as source:
            source.execute(sql, parameters)

    def assert_target_empty(self):
        self.assertEqual([], self.target.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall())

    def test_readonly_source_bytes_and_permissions(self):
        before = hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.source.chmod(0o444)
        snapshot = populate_quarantined_catalog(self.source, self.target)
        self.assertEqual(before, hashlib.sha256(self.source.read_bytes()).hexdigest())
        self.assertEqual(snapshot.fingerprint, self.target.execute("SELECT fingerprint FROM import_receipt").fetchone()[0])
        self.assertEqual("UNVERIFIED", self.target.execute("SELECT provider_status FROM import_receipt").fetchone()[0])

    def test_unknown_provider_is_not_activated(self):
        self.mutate_fixture("UPDATE clone_metadata SET engineKey='unknown-provider'")
        populate_quarantined_catalog(self.source, self.target)
        self.assertEqual(("unknown-provider", "UNVERIFIED", 0), self.target.execute("SELECT engine_key,readiness,launch_allowed FROM manager_catalog").fetchone())

    def test_unknown_owner_kind_preserved_and_quarantined(self):
        self.mutate_fixture("UPDATE cluster_references SET ownerType='FUTURE_OWNER'")
        populate_quarantined_catalog(self.source, self.target)
        owner = self.target.execute("SELECT owner_json,reason,release_allowed FROM quarantined_owners").fetchone()
        self.assertEqual("FUTURE_OWNER", json.loads(owner[0])["ownerType"]["value"])
        self.assertEqual(("UNKNOWN_OWNER_KIND", 0), owner[1:])

    def test_uncertain_task_state_and_raw_payload_preserved(self):
        before = inspect_legacy(self.source)
        populate_quarantined_catalog(self.source, self.target)
        task = self.target.execute("SELECT legacy_state,source_revision,payload_json,dispatch_allowed FROM quarantined_tasks").fetchone()
        self.assertEqual(("APPLYING", 7), task[:2]); self.assertEqual(0, task[3])
        self.assertEqual(before.tables["update_tasks"][0]["payload"], json.loads(task[2])["payload"]["value"])

    def test_unknown_future_task_state_stays_inert(self):
        self.mutate_fixture("UPDATE update_tasks SET state='FUTURE_NATIVE_OUTCOME'")
        populate_quarantined_catalog(self.source, self.target)
        self.assertEqual(("FUTURE_NATIVE_OUTCOME", 0), self.target.execute("SELECT legacy_state,dispatch_allowed FROM quarantined_tasks").fetchone())

    def test_blob_null_unicode_and_old_route_preserved(self):
        self.mutate_fixture("UPDATE update_tasks SET payload=?", (b"\x00\xffsynthetic",))
        populate_quarantined_catalog(self.source, self.target)
        row = self.target.execute("SELECT label,group_name,legacy_launch_token,badge_text FROM manager_catalog").fetchone()
        self.assertEqual(("Bản thử Á", None, "old-route-a", "★"), row)
        payload = json.loads(self.target.execute("SELECT payload_json FROM quarantined_tasks").fetchone()[0])["payload"]
        self.assertEqual("blob", payload["type"])
        self.assertEqual("AP9zeW50aGV0aWM=", payload["value"])

    def test_foreign_namespace_same_clone_id_remains_distinct(self):
        self.mutate_fixture("INSERT INTO clone_metadata SELECT 'foreign-provider',cloneId,packageName,label,groupName,createdAt,changedAt,'foreign-route',accentKey,badgeText,clusterId FROM clone_metadata")
        populate_quarantined_catalog(self.source, self.target)
        self.assertEqual(2, self.target.execute("SELECT count(*) FROM manager_catalog").fetchone()[0])
        self.assertEqual(0, self.target.execute("SELECT sum(launch_allowed) FROM manager_catalog").fetchone()[0])

    def test_existing_target_refused_unchanged(self):
        self.target.execute("CREATE TABLE existing(value TEXT)"); self.target.execute("INSERT INTO existing VALUES('keep')"); self.target.commit()
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assertEqual([("keep",)], self.target.execute("SELECT * FROM existing").fetchall())

    def test_temp_shadow_target_refused_unchanged(self):
        self.target.execute("CREATE TEMP TABLE manager_catalog(value TEXT)")
        self.target.execute("INSERT INTO temp.manager_catalog VALUES('keep')"); self.target.commit()
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assertEqual([("keep",)], self.target.execute("SELECT * FROM temp.manager_catalog").fetchall())
        self.assert_target_empty()

    def test_attached_target_database_refused(self):
        attached = self.root / "attached-fixture.db"
        self.target.execute("ATTACH DATABASE ? AS other", (str(attached),))
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_active_target_transaction_refused_without_rollback(self):
        self.target.execute("CREATE TABLE existing(value TEXT)"); self.target.execute("INSERT INTO existing VALUES('pending')")
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assertTrue(self.target.in_transaction)
        self.assertEqual(("pending",), self.target.execute("SELECT * FROM existing").fetchone())

    def test_unsafe_target_journal_mode_rejected(self):
        self.target.execute("PRAGMA journal_mode=OFF")
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_unsafe_target_synchronization_rejected(self):
        self.target.execute("PRAGMA synchronous=OFF")
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_explicit_sql_transactions_work_in_autocommit_connection(self):
        if hasattr(sqlite3.Connection, "autocommit"):
            target = sqlite3.connect(":memory:", autocommit=True)
        else:
            target = sqlite3.connect(":memory:", isolation_level=None)
        try:
            populate_quarantined_catalog(self.source, target)
            self.assertFalse(target.in_transaction)
            self.assertEqual(1, target.execute("SELECT count(*) FROM import_receipt").fetchone()[0])
        finally: target.close()

    def test_commit_failure_rolls_back_every_target_table(self):
        def authorizer(action, argument, _second, _database, _trigger):
            return sqlite3.SQLITE_DENY if action == sqlite3.SQLITE_TRANSACTION and argument == "COMMIT" else sqlite3.SQLITE_OK
        self.target.set_authorizer(authorizer)
        before = self.source.read_bytes()
        try:
            with self.assertRaises(sqlite3.DatabaseError): populate_quarantined_catalog(self.source, self.target)
        finally: self.target.set_authorizer(None)
        self.assertFalse(self.target.in_transaction); self.assert_target_empty()
        self.assertEqual(before, self.source.read_bytes())

    def test_disabled_check_constraints_rejected(self):
        self.target.execute("PRAGMA ignore_check_constraints=ON")
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_post_commit_error_is_uncertain_not_rolled_back(self):
        def fault(point):
            if point == "after_commit": raise RuntimeError("synthetic post-commit interruption")
        with self.assertRaises(TargetUncertain) as caught: populate_quarantined_catalog(self.source, self.target, fault)
        self.assertIsInstance(caught.exception.__cause__, RuntimeError)
        self.assertEqual(("UNVERIFIED", 1), self.target.execute("SELECT provider_status,paused FROM import_receipt").fetchone())
        self.assertEqual(0, self.target.execute("SELECT sum(dispatch_allowed) FROM quarantined_tasks").fetchone()[0])

    def test_rollback_error_preserves_original_failure(self):
        def authorizer(action, argument, _second, _database, _trigger):
            return sqlite3.SQLITE_DENY if action == sqlite3.SQLITE_TRANSACTION and argument == "ROLLBACK" else sqlite3.SQLITE_OK
        def fault(point):
            if point == "before_commit": raise RuntimeError("original synthetic failure")
        self.target.set_authorizer(authorizer)
        try:
            with self.assertRaises(TargetUncertain) as caught: populate_quarantined_catalog(self.source, self.target, fault)
            self.assertIsInstance(caught.exception.__cause__, RuntimeError)
            self.assertEqual("original synthetic failure", str(caught.exception.__cause__))
        finally: self.target.set_authorizer(None); self.target.execute("ROLLBACK")
        self.assert_target_empty()

    def test_preserved_rows_match_every_source_value_after_reopen(self):
        path = self.root / "row-verification.db"
        with sqlite3.connect(path) as target: snapshot = populate_quarantined_catalog(self.source, target)
        with sqlite3.connect(path) as reopened:
            for table, rows in snapshot.tables.items():
                actual = reopened.execute("SELECT row_json FROM preserved_rows WHERE table_name=? ORDER BY ordinal", (table,)).fetchall()
                self.assertEqual([(_row_json(row),) for row in rows], actual)

    def test_unexpected_index_and_auxiliary_column_rejected(self):
        self.mutate_fixture("CREATE INDEX extra_index ON clone_metadata(label)")
        with self.assertRaises(Rejected): inspect_legacy(self.source)
        self.mutate_fixture("DROP INDEX extra_index")
        self.mutate_fixture("ALTER TABLE room_master_table ADD COLUMN extra TEXT")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_all_owner_kinds_and_orphans_remain_retained(self):
        for number, kind in enumerate(("CLONE", "UPDATE_TASK", "ROLLBACK", "FUTURE_OWNER")):
            self.mutate_fixture("INSERT INTO cluster_references VALUES(?,?,?,?,?)", (f"missing-cluster-{number}", kind, f"missing-owner-{number}", "foreign-engine", number))
        populate_quarantined_catalog(self.source, self.target)
        self.assertEqual(5, self.target.execute("SELECT count(*) FROM quarantined_owners").fetchone()[0])
        self.assertEqual(0, self.target.execute("SELECT sum(release_allowed) FROM quarantined_owners").fetchone()[0])
        self.assertEqual(1, self.target.execute("SELECT count(*) FROM quarantined_owners WHERE reason='UNKNOWN_OWNER_KIND'").fetchone()[0])

    def test_legacy_unpaused_state_does_not_enable_new_dispatch(self):
        self.mutate_fixture("UPDATE queue_control SET paused=0")
        populate_quarantined_catalog(self.source, self.target)
        self.assertEqual(1, self.target.execute("SELECT paused FROM import_receipt").fetchone()[0])
        original = json.loads(self.target.execute("SELECT row_json FROM preserved_rows WHERE table_name='queue_control'").fetchone()[0])
        self.assertEqual(0, original["paused"]["value"])

    def test_nonfinite_legacy_numeric_value_rejected(self):
        self.mutate_fixture("UPDATE clone_metadata SET createdAt=?", (float("inf"),))
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_unexpected_foreign_key_rejected(self):
        other = self.root / "foreign-key-fixture.db"
        def transform(sql):
            return sql[:-1] + ", FOREIGN KEY(engineKey) REFERENCES queue_control(id))" if "CREATE TABLE IF NOT EXISTS `clone_metadata`" in sql else sql
        fixture(other, transform=transform)
        with self.assertRaises(Rejected): inspect_legacy(other)

    def test_unknown_schema_version_rejected(self):
        self.mutate_fixture("PRAGMA user_version=7")
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_wrong_room_identity_rejected(self):
        self.mutate_fixture("UPDATE room_master_table SET identity_hash='not-verified'")
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty()

    def test_missing_table_rejected(self):
        self.mutate_fixture("DROP TABLE cluster_references")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_unknown_table_rejected(self):
        self.mutate_fixture("CREATE TABLE hidden_data(value TEXT)")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_unknown_column_rejected(self):
        self.mutate_fixture("ALTER TABLE clone_metadata ADD COLUMN surprise TEXT")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_generated_column_rejected(self):
        self.mutate_fixture("ALTER TABLE clone_metadata ADD COLUMN generated_label TEXT GENERATED ALWAYS AS (label) VIRTUAL")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_changed_default_rejected(self):
        other = self.root / "default-fixture.db"
        fixture(other, transform=lambda sql: sql.replace("DEFAULT 'teal'", "DEFAULT 'red'"))
        with self.assertRaises(Rejected): inspect_legacy(other)

    def test_index_collation_and_order_rejected(self):
        for suffix in ("COLLATE NOCASE", "DESC"):
            with self.subTest(suffix=suffix):
                self.mutate_fixture("DROP INDEX index_clone_metadata_launchToken")
                self.mutate_fixture(f"CREATE UNIQUE INDEX index_clone_metadata_launchToken ON clone_metadata(launchToken {suffix})")
                with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_missing_required_index_rejected(self):
        self.mutate_fixture("DROP INDEX index_clone_metadata_launchToken")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_partial_index_cannot_replace_required_index(self):
        self.mutate_fixture("DROP INDEX index_clone_metadata_launchToken")
        self.mutate_fixture("CREATE UNIQUE INDEX index_clone_metadata_launchToken ON clone_metadata(launchToken) WHERE engineKey='only-one'")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_invalid_metadata_projection_rolls_back_without_source_repair(self):
        self.mutate_fixture("UPDATE clone_metadata SET label=?", (b"not-text",))
        before = self.source.read_bytes()
        with self.assertRaises(Rejected): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty(); self.assertEqual(before, self.source.read_bytes())

    def test_snapshot_budget_fails_closed(self):
        self.mutate_fixture("UPDATE update_tasks SET payload=?", ("x" * 20_000,))
        before = self.source.read_bytes()
        with patch("storage_contract.MAX_SNAPSHOT_BYTES", 10_000):
            with self.assertRaises((Rejected, sqlite3.DataError)): populate_quarantined_catalog(self.source, self.target)
        self.assert_target_empty(); self.assertEqual(before, self.source.read_bytes())

    def test_executable_schema_trigger_rejected(self):
        self.mutate_fixture("CREATE TRIGGER surprise AFTER UPDATE ON clone_metadata BEGIN SELECT 1; END")
        with self.assertRaises(Rejected): inspect_legacy(self.source)

    def test_symlink_source_rejected(self):
        link = self.root / "linked.db"; link.symlink_to(self.source)
        with self.assertRaises(Rejected): inspect_legacy(link)

    def test_missing_source_not_created(self):
        missing = self.root / "absent.db"
        with self.assertRaises(Rejected): inspect_legacy(missing)
        self.assertFalse(missing.exists())

    def test_fault_rolls_back_schema_rows_and_receipt(self):
        before = self.source.read_bytes()
        for stop in ("after_schema", "after_preserved_row", "after_catalog", "before_commit"):
            with self.subTest(stop=stop):
                def fault(point):
                    if point == stop: raise RuntimeError("synthetic injected failure")
                with self.assertRaises(RuntimeError): populate_quarantined_catalog(self.source, self.target, fault)
                self.assert_target_empty(); self.assertEqual(before, self.source.read_bytes())

    def test_target_lock_failure_preserves_source(self):
        path = self.root / "target.db"; locker = sqlite3.connect(path); blocked = sqlite3.connect(path, timeout=0.01)
        before = self.source.read_bytes()
        try:
            locker.execute("BEGIN EXCLUSIVE")
            with self.assertRaises(sqlite3.OperationalError): populate_quarantined_catalog(self.source, blocked)
            self.assertEqual(before, self.source.read_bytes())
        finally: locker.rollback(); locker.close(); blocked.close()

    def test_reopen_preserves_quarantine_receipt(self):
        path = self.root / "catalog.db"
        with sqlite3.connect(path) as target: snapshot = populate_quarantined_catalog(self.source, target)
        with sqlite3.connect(path) as reopened:
            self.assertEqual((snapshot.fingerprint, 1), reopened.execute("SELECT fingerprint,paused FROM import_receipt").fetchone())
            self.assertEqual(0, reopened.execute("SELECT sum(dispatch_allowed) FROM quarantined_tasks").fetchone()[0])

    def test_constraints_prevent_activation_by_data_flip(self):
        populate_quarantined_catalog(self.source, self.target)
        for statement in ("UPDATE manager_catalog SET launch_allowed=1", "UPDATE manager_catalog SET readiness='READY'",
                          "UPDATE quarantined_tasks SET dispatch_allowed=1", "UPDATE quarantined_owners SET release_allowed=1",
                          "UPDATE import_receipt SET paused=0"):
            with self.subTest(statement=statement):
                with self.assertRaises(sqlite3.IntegrityError): self.target.execute(statement)
                self.target.rollback()

    def test_snapshot_immutable_and_stable(self):
        first = inspect_legacy(self.source); second = inspect_legacy(self.source)
        self.assertEqual(first.fingerprint, second.fingerprint)
        with self.assertRaises(TypeError): first.tables["clone_metadata"][0]["label"] = "changed"
        with self.assertRaises(TypeError): first.tables["new"] = ()

    def test_source_changes_after_snapshot_do_not_rewrite_preserved_rows(self):
        def fault(point):
            if point == "after_schema": self.mutate_fixture("UPDATE clone_metadata SET label='later fixture change'")
        populate_quarantined_catalog(self.source, self.target, fault)
        self.assertEqual("Bản thử Á", self.target.execute("SELECT label FROM manager_catalog").fetchone()[0])
        self.assertEqual("later fixture change", inspect_legacy(self.source).tables["clone_metadata"][0]["label"])


def version_case(version):
    def test(self):
        source = self.root / f"version-{version}.db"; fixture(source, version)
        before = source.read_bytes(); snapshot = populate_quarantined_catalog(source, self.target)
        self.assertEqual(version, snapshot.version); self.assertEqual(before, source.read_bytes())
        self.assertEqual(sum(len(rows) for rows in snapshot.tables.values()), self.target.execute("SELECT count(*) FROM preserved_rows").fetchone()[0])
        self.assertEqual(("UNVERIFIED", 0), self.target.execute("SELECT readiness,launch_allowed FROM manager_catalog").fetchone())
        if version == 1: self.assertIsNone(self.target.execute("SELECT legacy_launch_token FROM manager_catalog").fetchone()[0])
    return test


for _version in range(1, 7):
    setattr(StorageContractTest, f"test_pinned_schema_{_version}_preservation", version_case(_version))

if __name__ == "__main__": unittest.main(verbosity=2)
