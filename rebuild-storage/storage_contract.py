"""Synthetic SQLite preservation/quarantine reference contract. Not an Android migration adapter."""
from __future__ import annotations

import base64
import hashlib
import json
import math
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from types import MappingProxyType
from typing import Callable, Mapping

SPEC_ROOT = Path(__file__).parent / "spec" / "legacy-room"
MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024  # Test-contract memory budget, not a product clone limit.
KNOWN_OWNER_TYPES = frozenset({"CLONE", "UPDATE_TASK", "REVIEW_REQUIRED", "ROLLBACK"})


class Rejected(ValueError):
    """Input cannot be safely interpreted; source remains untouched."""


class TargetUncertain(RuntimeError):
    """Do not reuse target without reopening/reconciling its receipt; never activates a provider."""


def _canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _typed(value: object) -> dict:
    if value is None:
        return {"type": "null"}
    if type(value) is int:
        return {"type": "integer", "value": value}
    if type(value) is float and math.isfinite(value):
        return {"type": "real", "value": value}
    if type(value) is str:
        value.encode("utf-8", errors="strict")
        return {"type": "text", "value": value}
    if type(value) is bytes:
        return {"type": "blob", "value": base64.b64encode(value).decode("ascii")}
    raise Rejected("Unsupported SQLite value")


def _row_json(row: Mapping) -> str:
    return _canonical({column: _typed(value) for column, value in row.items()})


def load_schema(version: int) -> dict:
    if type(version) is not int or version not in range(1, 7):
        raise Rejected("Unsupported legacy schema version")
    manifest = json.loads((SPEC_ROOT / "MANIFEST.json").read_text())
    name = f"{version}.json"
    data = (SPEC_ROOT / name).read_bytes()
    if hashlib.sha256(data).hexdigest() != manifest["files"][name]["sha256"]:
        raise Rejected("Historical schema oracle checksum mismatch")
    schema = json.loads(data)["database"]
    if schema["version"] != version:
        raise Rejected("Historical schema version mismatch")
    return schema


def _quote(name: str) -> str:
    return '"' + name.replace('"', '""') + '"'


def _validate_schema(db: sqlite3.Connection, schema: dict) -> list[str]:
    if db.execute("PRAGMA quick_check").fetchall() != [("ok",)]:
        raise Rejected("Legacy integrity check failed")
    entities = {entity["tableName"]: entity for entity in schema["entities"]}
    objects = db.execute("SELECT type,name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'").fetchall()
    if any(kind in ("view", "trigger") for kind, _ in objects):
        raise Rejected("Unexpected executable schema object")
    tables = {name for kind, name in objects if kind == "table"}
    if not set(entities).issubset(tables) or tables - set(entities) - {"room_master_table", "android_metadata"}:
        raise Rejected("Unexpected or missing legacy table")
    try:
        hashes = db.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchall()
    except sqlite3.DatabaseError as error:
        raise Rejected("Missing Room identity") from error
    if hashes != [(schema["identityHash"],)]:
        raise Rejected("Room identity does not match version")
    auxiliary_columns = {"room_master_table": ["id", "identity_hash"], "android_metadata": ["locale"], "sqlite_sequence": ["name", "seq"]}
    existing_tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    for auxiliary in existing_tables - set(entities):
        columns = db.execute(f"PRAGMA table_xinfo({_quote(auxiliary)})").fetchall()
        if auxiliary not in auxiliary_columns or [row[1] for row in columns] != auxiliary_columns[auxiliary] or any(row[6] for row in columns):
            raise Rejected("Unsupported auxiliary table shape")
    table_options = {row[1]: (row[4], row[5]) for row in db.execute("PRAGMA table_list") if row[0] == "main"}
    for name in entities:
        if table_options.get(name) != (0, 0):
            raise Rejected("Unsupported legacy table options")
    for name, entity in entities.items():
        columns = db.execute(f"PRAGMA table_xinfo({_quote(name)})").fetchall()
        if any(row[6] != 0 for row in columns):
            raise Rejected("Hidden/generated legacy column")
        actual = [(row[1], row[2].upper(), bool(row[3])) for row in columns]
        expected = [(field["columnName"], field["affinity"], field.get("notNull", False)) for field in entity["fields"]]
        if actual != expected:
            raise Rejected("Legacy column contract mismatch")
        for column, field in zip(columns, entity["fields"]):
            actual_default = column[4]
            expected_default = field.get("defaultValue")
            if actual_default is not None and actual_default.upper() == "NULL":
                actual_default = None
            if expected_default is not None and expected_default.upper() == "NULL":
                expected_default = None
            if actual_default != expected_default:
                raise Rejected("Legacy column default mismatch")
        if db.execute(f"PRAGMA foreign_key_list({_quote(name)})").fetchall():
            raise Rejected("Unexpected legacy foreign key")
        primary = [row[1] for row in sorted(columns, key=lambda r: r[5]) if row[5]]
        if primary != entity["primaryKey"]["columnNames"]:
            raise Rejected("Legacy primary key mismatch")
        index_rows = db.execute(f"PRAGMA index_list({_quote(name)})").fetchall()
        index_map = {row[1]: (bool(row[2]), bool(row[4])) for row in index_rows}
        required_indexes = {index["name"] for index in entity.get("indices", [])}
        if any(name not in required_indexes and not name.startswith("sqlite_autoindex_") for name in index_map):
            raise Rejected("Unexpected legacy index")
        for index in entity.get("indices", []):
            if index_map.get(index["name"]) != (index["unique"], False):
                raise Rejected("Legacy index missing or changed")
            extended = db.execute(f"PRAGMA index_xinfo({_quote(index['name'])})").fetchall()
            key_fields = [field for field in extended if field[5]]
            orders = index.get("orders") or ["ASC"] * len(index["columnNames"])
            if [(field[2], bool(field[3]), field[4]) for field in key_fields] != [
                    (column, order == "DESC", "BINARY") for column, order in zip(index["columnNames"], orders)]:
                raise Rejected("Legacy index order/collation mismatch")
            fields = db.execute(f"PRAGMA index_info({_quote(index['name'])})").fetchall()
            if [field[2] for field in fields] != index["columnNames"]:
                raise Rejected("Legacy index column mismatch")
    all_tables = db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
    return [row[0] for row in all_tables]


@dataclass(frozen=True)
class Snapshot:
    version: int
    room_identity: str
    fingerprint: str
    tables: Mapping[str, tuple[Mapping, ...]]


def inspect_legacy(source_fixture: Path) -> Snapshot:
    """Read-only stable snapshot; caller must supply a synthetic fixture in current milestone."""
    source = source_fixture.absolute()
    if source.is_symlink() or not source.is_file() or source.resolve() != source:
        raise Rejected("Source must be a canonical regular fixture file")
    db = sqlite3.connect(source.as_uri() + "?mode=ro", uri=True, isolation_level=None)
    try:
        db.setlimit(sqlite3.SQLITE_LIMIT_LENGTH, MAX_SNAPSHOT_BYTES)
        db.execute("PRAGMA query_only=ON")
        db.execute("BEGIN")
        version = db.execute("PRAGMA user_version").fetchone()[0]
        schema = load_schema(version)
        tables = _validate_schema(db, schema)
        snapshot = {}
        encoded = {"version": version, "room_identity": schema["identityHash"], "tables": {}}
        size = 0
        for table in tables:
            cursor = db.execute(f"SELECT * FROM {_quote(table)}")
            names = [column[0] for column in cursor.description]
            rows = []
            for values in cursor:
                row = dict(zip(names, values))
                serialized = _row_json(row)
                size += len(serialized.encode("utf-8"))
                if size > MAX_SNAPSHOT_BYTES:
                    raise Rejected("Synthetic snapshot memory budget exceeded")
                rows.append((serialized, MappingProxyType(row)))
            rows.sort(key=lambda pair: pair[0])
            snapshot[table] = tuple(row for _, row in rows)
            encoded["tables"][table] = [text for text, _ in rows]
        fingerprint = hashlib.sha256(_canonical(encoded).encode("utf-8")).hexdigest()
        return Snapshot(version, schema["identityHash"], fingerprint, MappingProxyType(snapshot))
    finally:
        if db.in_transaction:
            db.rollback()
        db.close()


DDL = (
    "CREATE TABLE main.import_receipt (id INTEGER PRIMARY KEY CHECK(id=1), source_version INTEGER NOT NULL, room_identity TEXT NOT NULL, fingerprint TEXT NOT NULL, provider_status TEXT NOT NULL CHECK(provider_status='UNVERIFIED'), paused INTEGER NOT NULL CHECK(paused=1))",
    "CREATE TABLE main.preserved_rows (table_name TEXT NOT NULL, ordinal INTEGER NOT NULL, row_json TEXT NOT NULL, PRIMARY KEY(table_name,ordinal))",
    "CREATE TABLE main.manager_catalog (engine_key TEXT NOT NULL, clone_id TEXT NOT NULL, package_name TEXT NOT NULL, label TEXT NOT NULL, group_name TEXT, legacy_launch_token TEXT, accent_key TEXT, badge_text TEXT, cluster_id TEXT, readiness TEXT NOT NULL CHECK(readiness='UNVERIFIED'), launch_allowed INTEGER NOT NULL CHECK(launch_allowed=0), PRIMARY KEY(engine_key,clone_id))",
    "CREATE TABLE main.quarantined_tasks (task_id TEXT PRIMARY KEY NOT NULL, legacy_state TEXT NOT NULL, source_revision INTEGER NOT NULL, payload_json TEXT NOT NULL, dispatch_allowed INTEGER NOT NULL CHECK(dispatch_allowed=0))",
    "CREATE TABLE main.quarantined_owners (ordinal INTEGER PRIMARY KEY, owner_json TEXT NOT NULL, reason TEXT NOT NULL, release_allowed INTEGER NOT NULL CHECK(release_allowed=0))",
)


def populate_quarantined_catalog(source_fixture: Path, target_fixture: sqlite3.Connection,
                                  fault: Callable[[str], None] = lambda point: None) -> Snapshot:
    """Populate separate empty test DB atomically; never writes source or enables provider actions."""
    if target_fixture.in_transaction:
        raise Rejected("Target has an active caller transaction")
    journal_mode = target_fixture.execute("PRAGMA main.journal_mode").fetchone()[0].lower()
    if journal_mode not in {"delete", "truncate", "persist", "wal", "memory"}:
        raise Rejected("Target journaling cannot guarantee rollback")
    if target_fixture.execute("PRAGMA ignore_check_constraints").fetchone()[0] != 0:
        raise Rejected("Target CHECK constraints must be enforced")
    if target_fixture.execute("PRAGMA main.synchronous").fetchone()[0] < 2:
        raise Rejected("Target requires FULL/EXTRA synchronization")
    if any(row[1] not in ("main", "temp") for row in target_fixture.execute("PRAGMA database_list")):
        raise Rejected("Attached target database not allowed")
    if target_fixture.execute("SELECT name FROM sqlite_temp_master").fetchone():
        raise Rejected("Target TEMP schema must be empty")
    snapshot = inspect_legacy(source_fixture)
    transaction_started = False
    try:
        target_fixture.execute("BEGIN IMMEDIATE")
        transaction_started = True
        if target_fixture.execute("SELECT name FROM sqlite_master WHERE type IN ('table','view','trigger')").fetchone():
            raise Rejected("Target must be empty")
        for statement in DDL:
            target_fixture.execute(statement)
        fault("after_schema")
        for table, rows in snapshot.tables.items():
            for ordinal, row in enumerate(rows):
                target_fixture.execute("INSERT INTO main.preserved_rows VALUES(?,?,?)", (table, ordinal, _row_json(row)))
                fault("after_preserved_row")
        for row in snapshot.tables["clone_metadata"]:
            for field in ("engineKey", "cloneId", "packageName", "label"):
                if not isinstance(row[field], str) or not row[field]:
                    raise Rejected("Legacy metadata identity cannot be projected")
            target_fixture.execute("INSERT INTO main.manager_catalog VALUES(?,?,?,?,?,?,?,?,?,'UNVERIFIED',0)", (
                row["engineKey"], row["cloneId"], row["packageName"], row["label"], row["groupName"],
                row.get("launchToken"), row.get("accentKey"), row.get("badgeText"), row.get("clusterId")))
        fault("after_catalog")
        for row in snapshot.tables["update_tasks"]:
            target_fixture.execute("INSERT INTO main.quarantined_tasks VALUES(?,?,?,?,0)", (
                row["taskId"], row["state"], row["revision"], _row_json(row)))
        for ordinal, row in enumerate(snapshot.tables.get("cluster_references", ())):
            reason = "PROVIDER_UNVERIFIED" if row["ownerType"] in KNOWN_OWNER_TYPES else "UNKNOWN_OWNER_KIND"
            target_fixture.execute("INSERT INTO main.quarantined_owners VALUES(?,?,?,0)", (ordinal, _row_json(row), reason))
        target_fixture.execute("INSERT INTO main.import_receipt VALUES(1,?,?,?,'UNVERIFIED',1)", (
            snapshot.version, snapshot.room_identity, snapshot.fingerprint))
        fault("before_commit")
        target_fixture.execute("COMMIT")
        if target_fixture.in_transaction:
            raise Rejected("Target transaction did not complete")
        fault("after_commit")
        return snapshot
    except BaseException as original:
        if target_fixture.in_transaction:
            try:
                target_fixture.execute("ROLLBACK")
            except sqlite3.Error as rollback_error:
                uncertain = TargetUncertain("Target rollback uncertain; reopen and reconcile receipt before reuse")
                uncertain.add_note(f"Rollback failure type: {type(rollback_error).__name__}")
                raise uncertain from original
        elif transaction_started:
            raise TargetUncertain("Target commit outcome uncertain; reopen and reconcile receipt before reuse") from original
        raise
