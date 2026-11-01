"""Owned native-SQLite conformance fixtures; NOT an Android/Room migration adapter."""
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ORACLES = ROOT / 'reference/history/e82/app/schemas/vn.fonesi.app.storage.FonesiDatabase'
PINS = {3: '05479b88b5964b6ee0839521152d01fe3560c0d3a2451355bf9f6fc0fb661bc6',
        4: 'e5fcc73cc34819ea675833dab029adfc0a15d6566afa5aeaa96bd160a3fd53c8'}
SQL = (ROOT / 'storage/sql/style_columns_3_to_4.sql').read_text()
STATEMENTS = [x.strip() for x in SQL.split(';') if x.strip()]


def schema(version):
    raw = (ORACLES / f'{version}.json').read_bytes()
    assert hashlib.sha256(raw).hexdigest() == PINS[version]
    return json.loads(raw)['database']


def connect(path):
    return sqlite3.connect(path, isolation_level=None, timeout=0)


def create_owned_fixture(connection, version=3):
    data = schema(version)
    for entity in data['entities']:
        connection.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices') or []:
            connection.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for statement in data['setupQueries']:
        connection.execute(statement)
    connection.execute(f'PRAGMA user_version={version}')  # owned fixture initialization only


def schema_objects(connection):
    return connection.execute('SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name').fetchall()


def raw_rows(connection, table, columns):
    # Names come only from pinned schema, never external input. Preserve type AND bytes.
    fields = ','.join(f'typeof("{c}"),hex(CAST("{c}" AS BLOB))' for c in columns)
    return connection.execute(f'SELECT {fields} FROM "{table}" ORDER BY rowid').fetchall()


def retained_values(connection):
    result = {}
    for entity in schema(3)['entities']:
        result[entity['tableName']] = raw_rows(connection, entity['tableName'],
                                              [x['columnName'] for x in entity['fields']])
    result['sqlite_sequence'] = raw_rows(connection, 'sqlite_sequence', ['name', 'seq'])
    result['room_master_table'] = raw_rows(connection, 'room_master_table', ['id', 'identity_hash'])
    result['user_version'] = connection.execute('PRAGMA user_version').fetchone()
    return result


def structure(connection):
    result = {}
    for entity in schema(4)['entities']:
        table = entity['tableName']
        result[table] = {
            'columns': connection.execute(f'PRAGMA table_info("{table}")').fetchall(),
            'indexes': sorted(connection.execute(f'PRAGMA index_list("{table}")').fetchall(), key=lambda x:x[1]),
            'foreign_keys': connection.execute(f'PRAGMA foreign_key_list("{table}")').fetchall(),
        }
    return result


def apply_fragment_owned_fixture(connection, expected_schema, interrupt_after_first=False):
    """Test harness owns reservation/guard/rollback. Deliberately NOT exported production code."""
    connection.execute('BEGIN IMMEDIATE')
    try:
        if connection.execute('PRAGMA user_version').fetchone() != (3,):
            raise ValueError('unrecognized fixture source version')
        if schema_objects(connection) != expected_schema:
            raise ValueError('unrecognized fixture source schema')
        if connection.execute('SELECT id,identity_hash FROM room_master_table').fetchall() != [
                (42, schema(3)['identityHash'])]:
            raise ValueError('unrecognized fixture Room metadata')
        for i, statement in enumerate(STATEMENTS):
            connection.execute(statement)
            if interrupt_after_first and i == 0:
                raise InterruptedError('owned interruption injection')
        connection.execute('COMMIT')
    except BaseException:
        connection.execute('ROLLBACK')
        raise


class StyleMigrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'owned-fixture.db'
        self.db = connect(self.path)
        create_owned_fixture(self.db)
        self.expected = schema_objects(self.db)
        keys = [('', ''), (' engine ', ' id '), ('engine', 'Case'), ('engine', 'case'),
                ('é', 'x'), ('e\u0301', 'x'), ('A|B', 'C'), ('A', 'B|C'), ('N\x00S', 'I\x00D'), ('same-engine', 'Case'), ('same-engine', 'case')]
        for i, (engine, clone) in enumerate(keys):
            self.db.execute('INSERT INTO clone_metadata VALUES(?,?,?,?,?,?,?,?)',
                            (engine, clone, 'owned.fixture', 'name', None, i, i, f'owned-token-{i}'))
        self.db.execute("UPDATE clone_metadata SET label=CAST(X'80FF' AS TEXT),groupName=X'00FF' WHERE launchToken='owned-token-0'")
        self.db.execute("UPDATE clone_metadata SET engineKey=CAST(X'80FF' AS TEXT) WHERE launchToken='owned-token-2'")
        self.db.execute("UPDATE clone_metadata SET cloneId=X'00FF' WHERE launchToken='owned-token-3'")
        self.db.execute("INSERT INTO update_tasks VALUES(20,'task','owned.fixture','WAITING',7,NULL,'opaque')")
        self.db.execute('INSERT INTO queue_control VALUES(1,1)')
        self.db.execute("INSERT INTO engine_instances VALUES(30,'reservation','engine','owned.fixture','RESERVED',1)")
        self.before = retained_values(self.db)

    def tearDown(self):
        self.db.close()
        self.temp.cleanup()

    def test_exact_fragment_has_no_transaction_version_or_room_mutation(self):
        self.assertEqual(STATEMENTS, [
            "ALTER TABLE clone_metadata ADD COLUMN accentKey TEXT NOT NULL DEFAULT 'teal'",
            'ALTER TABLE clone_metadata ADD COLUMN badgeText TEXT'])

    def test_columns_structure_raw_values_and_reopen_preserved(self):
        apply_fragment_owned_fixture(self.db, self.expected)
        self.assertEqual(retained_values(self.db), self.before)
        self.assertEqual(self.db.execute('SELECT DISTINCT accentKey,badgeText FROM clone_metadata').fetchall(), [('teal',None)])
        target = connect(':memory:')
        try:
            create_owned_fixture(target, 4)
            self.assertEqual(structure(self.db), structure(target))
        finally:
            target.close()
        self.db.close()
        self.db = connect(self.path)
        self.assertEqual(retained_values(self.db), self.before)
        self.assertEqual(self.db.execute('PRAGMA user_version').fetchone(), (3,))

    def test_interruption_rolls_back_both_schema_and_data(self):
        with self.assertRaises(InterruptedError):
            apply_fragment_owned_fixture(self.db, self.expected, True)
        self.assertEqual(schema_objects(self.db), self.expected)
        self.assertEqual(retained_values(self.db), self.before)
        apply_fragment_owned_fixture(self.db, self.expected)
        self.assertEqual(retained_values(self.db), self.before)

    def test_unknown_partial_already_v4_and_extra_trigger_fail_unchanged(self):
        mutations = ['PRAGMA user_version=99', STATEMENTS[0],
                     "CREATE TRIGGER extra AFTER UPDATE ON clone_metadata BEGIN SELECT 1; END",
                     'DROP INDEX index_clone_metadata_launchToken']
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                db = connect(':memory:')
                try:
                    create_owned_fixture(db)
                    db.execute(mutation)
                    before = schema_objects(db), retained_values(db)
                    with self.assertRaises(ValueError): apply_fragment_owned_fixture(db, self.expected)
                    self.assertEqual((schema_objects(db), retained_values(db)), before)
                finally: db.close()
        apply_fragment_owned_fixture(self.db, self.expected)
        before = schema_objects(self.db), retained_values(self.db)
        with self.assertRaises(ValueError): apply_fragment_owned_fixture(self.db, self.expected)
        self.assertEqual((schema_objects(self.db), retained_values(self.db)), before)
        db = connect(':memory:')
        try:
            create_owned_fixture(db, 4)
            before = schema_objects(db), retained_values(db)
            with self.assertRaises(ValueError): apply_fragment_owned_fixture(db, self.expected)
            self.assertEqual((schema_objects(db), retained_values(db)), before)
        finally: db.close()

    def test_competing_writer_busy_and_changed_source_rechecked_under_lock(self):
        other = connect(self.path)
        try:
            other.execute('BEGIN IMMEDIATE')
            other.execute('CREATE TABLE unexpected(x)')
            with self.assertRaises(sqlite3.OperationalError) as raised:
                apply_fragment_owned_fixture(self.db, self.expected)
            self.assertEqual(raised.exception.sqlite_errorcode, sqlite3.SQLITE_BUSY)
            self.assertEqual(retained_values(self.db), self.before)
            other.execute('COMMIT')
            changed = schema_objects(self.db)
            with self.assertRaises(ValueError): apply_fragment_owned_fixture(self.db, self.expected)
            self.assertEqual(schema_objects(self.db), changed)
            self.assertEqual(retained_values(self.db), self.before)
        finally: other.close()

    def test_indexes_and_autoincrement_semantics_survive(self):
        apply_fragment_owned_fixture(self.db, self.expected)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE clone_metadata SET launchToken='owned-token-0' WHERE launchToken='owned-token-1'")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO update_tasks(taskId,packageName,state,revision,payload) VALUES('task','owned.fixture','WAITING',1,'x')")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO engine_instances(cloneId,engineKey,packageName,state,launchPending) VALUES('reservation','other','owned.fixture','RESERVED',0)")
        self.assertEqual(retained_values(self.db), self.before)
        self.db.execute("INSERT INTO update_tasks(taskId,packageName,state,revision,payload) VALUES('task2','owned.fixture','WAITING',1,'x')")
        self.assertEqual(self.db.execute("SELECT sequence FROM update_tasks WHERE taskId='task2'").fetchone(), (21,))
        self.db.execute("INSERT INTO engine_instances(cloneId,engineKey,packageName,state,launchPending) VALUES('reservation2','other','owned.fixture','RESERVED',0)")
        self.assertEqual(self.db.execute("SELECT sequence FROM engine_instances WHERE cloneId='reservation2'").fetchone(), (31,))


if __name__ == '__main__':
    print(f'HOST owned fixtures: Python native SQLite {sqlite3.sqlite_version}; Android/Room migration NOT_RUN', flush=True)
    unittest.main(verbosity=2)
