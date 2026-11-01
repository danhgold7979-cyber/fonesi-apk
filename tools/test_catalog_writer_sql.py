"""Real owned-file SQLite SQL tests, NOT a production Android writer/auth broker."""
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
SQL = {name: (ROOT / 'storage/sql' / ('catalog_' + name + '.sql')).read_text()
       for name in ('v1_schema', 'claim_revision', 'update_existing_style', 'read_style')}
MAX_REVISION = 9223372036854775807
APPLICATION_ID = 1178815793
UUID_PATTERN = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')


def connect(path):
    return sqlite3.connect(path, isolation_level=None, timeout=0)


def bootstrap_new_owned_fixture(path, encoding='UTF-8'):
    # Exclusive creation: test harness never adopts, resets or repairs an existing file.
    with open(path, 'xb'):
        pass
    connection = connect(path)
    try:
        if encoding not in ('UTF-8', 'UTF-16le'):
            raise ValueError('unsupported fixture setup')
        connection.execute("PRAGMA encoding='" + encoding + "'")
        connection.execute('BEGIN IMMEDIATE')
        for statement in SQL['v1_schema'].split(';'):
            if statement.strip(): connection.execute(statement)
        connection.execute('INSERT INTO catalog_meta VALUES(1,?,0)', (str(uuid.uuid4()),))
        connection.execute(f'PRAGMA application_id={APPLICATION_ID}')
        connection.execute('PRAGMA user_version=1')
        connection.execute('COMMIT')
        return connection
    except BaseException:
        connection.close()
        raise  # retain uncertain file, no destructive cleanup/retry


def objects(db):
    return db.execute('SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name').fetchall()


def raw_catalog(db):
    columns = ['engine_key','clone_id','package_name','label','group_name','legacy_launch_token',
               'accent_key','badge_text','cluster_id','readiness','launch_allowed']
    projection = ','.join(f'typeof({c}),hex(CAST({c} AS BLOB))' for c in columns)
    return db.execute(f'SELECT {projection} FROM manager_catalog ORDER BY rowid').fetchall()


def metadata(db):
    return db.execute('SELECT id,typeof(catalog_id),catalog_id,typeof(revision),revision FROM catalog_meta ORDER BY id').fetchall()


def guard(db, expected_objects):
    databases = db.execute('PRAGMA database_list').fetchall()
    names = [row[1] for row in databases]
    if names.count('main') != 1 or len(names) != len(set(names)) or set(names) - {'main', 'temp'}:
        raise ValueError('attached or missing database')
    if db.execute('SELECT name FROM sqlite_temp_master').fetchall():
        raise ValueError('temporary user schema')
    if objects(db) != expected_objects: raise ValueError('unknown schema')
    if db.execute('PRAGMA encoding').fetchone() != ('UTF-8',): raise ValueError('unsupported encoding')
    if db.execute('PRAGMA user_version').fetchone() != (1,): raise ValueError('unknown version')
    if db.execute('PRAGMA application_id').fetchone() != (APPLICATION_ID,): raise ValueError('wrong owner format')
    rows = metadata(db)
    if len(rows) != 1: raise ValueError('not singleton')
    ident, kind, generation, rev_kind, revision = rows[0]
    if ident != 1 or kind != 'text' or not isinstance(generation, str) or not UUID_PATTERN.fullmatch(generation):
        raise ValueError('invalid generation')
    if rev_kind != 'integer' or not 0 <= revision <= MAX_REVISION: raise ValueError('invalid revision')
    return generation, revision


def apply_owned_fixture(db, expected_objects, generation, revision, key, hook=lambda stage, db: None):
    """Executes real source SQL on fixture only. Fixed valid style; NOT an authorization API."""
    entered = False
    started = False
    try:
        db.execute('BEGIN IMMEDIATE')
        started = True
        guard(db, expected_objects)
        # Snapshot tuple is data only, never treated as authentication or lifecycle proof.
        claimed = db.execute(SQL['claim_revision'], (generation.encode('utf-8'), revision)).rowcount
        if claimed != 1: raise ValueError('stale generation/revision or overflow')
        hook('after_claim', db)
        changed = db.execute(SQL['update_existing_style'], ('blue', ' A ', *key)).rowcount
        if changed != 1: raise ValueError('missing/wrong namespace; never upsert')
        hook('after_update', db)
        expected_row = ('text', key[0], 'text', key[1], 'text', b'blue', 'text', b' A ')
        if db.execute(SQL['read_style'], key).fetchall() != [expected_row]:
            raise ValueError('readback mismatch')
        if guard(db, expected_objects) != (generation, revision + 1):
            raise ValueError('generation/revision readback mismatch')
        hook('before_commit_entry', db)
        # A real coordinator must authorize this transition. This fixture has NO app caller.
        entered = True
        hook('commit_entered', db)
        db.execute('COMMIT')
        hook('after_commit', db)
        return 'COMMITTED'
    except (sqlite3.Error, ValueError, InterruptedError):
        if entered:
            # This fixture owns the connection. Release it without claiming rollback;
            # a fresh read is required to reconcile the actual durable outcome.
            try: db.close()
            except sqlite3.Error: pass
            return 'INDETERMINATE'
        if started:
            try: db.execute('ROLLBACK')
            except sqlite3.Error: return 'INDETERMINATE'
            return 'ROLLED_BACK'
        raise  # BEGIN failure is exposed, not confused with a rollback receipt


class CatalogWriterSqlTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'owned.sqlite'
        self.db = bootstrap_new_owned_fixture(self.path)
        self.expected = objects(self.db)
        self.generation, self.revision = guard(self.db, self.expected)
        # Fixture setup occurs before any editor snapshot; no production writer implied.
        for engine, clone, token in [(' e ', 'id\x00x', 'opaque-a'), ('other', 'id\x00x', 'opaque-b')]:
            self.db.execute('INSERT INTO manager_catalog VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                            (engine, clone, 'owned.fixture', 'label', None, token, 'teal', None,
                             'cluster', 'UNVERIFIED', 0))
        self.db.execute("UPDATE manager_catalog SET accent_key=CAST(X'80' AS TEXT),badge_text=X'00FF' WHERE engine_key=' e '")
        self.db.execute("UPDATE manager_catalog SET label=CAST(X'80FF' AS TEXT),group_name=X'00FF' WHERE engine_key='other'")
        self.key = (b' e ', b'id\x00x')
        self.before = raw_catalog(self.db)

    def tearDown(self):
        self.db.close()
        self.temp.cleanup()

    def apply(self, generation=None, revision=None, key=None, hook=lambda stage,db:None):
        return apply_owned_fixture(self.db, self.expected,
                                   self.generation if generation is None else generation,
                                   self.revision if revision is None else revision,
                                   self.key if key is None else key, hook)

    def test_success_exact_row_sibling_fields_generation_and_reopen(self):
        self.assertEqual(self.apply(), 'COMMITTED')
        after = raw_catalog(self.db)
        self.assertEqual(after[1], self.before[1])
        self.assertEqual(after[0][:12], self.before[0][:12])
        self.assertEqual(after[0][16:], self.before[0][16:])
        self.assertEqual(guard(self.db,self.expected),(self.generation,1))
        self.db.close(); self.db = connect(self.path)
        self.assertEqual(raw_catalog(self.db),after)
        self.assertEqual(guard(self.db,self.expected),(self.generation,1))

    def test_stale_wrong_generation_missing_wrong_namespace_no_upsert(self):
        for kwargs in [{'revision':1}, {'generation':str(uuid.uuid4())},
                       {'key':(b'missing',b'id\x00x')}, {'key':(b'e',b'id\x00x')}]:
            with self.subTest(kwargs=kwargs):
                self.assertEqual(self.apply(**kwargs),'ROLLED_BACK')
                self.assertEqual(raw_catalog(self.db),self.before)
                self.assertEqual(guard(self.db,self.expected),(self.generation,0))

    def test_delete_reinsert_aba_advances_global_revision(self):
        self.db.execute("CREATE TEMP TABLE aba_copy AS SELECT * FROM manager_catalog WHERE engine_key=' e '")
        for statement, params in [('DELETE FROM manager_catalog WHERE engine_key=?',(' e ',)),
                                  ('INSERT INTO manager_catalog SELECT * FROM aba_copy',())]:
            self.db.execute('BEGIN IMMEDIATE')
            self.db.execute(statement,params)
            self.db.execute('UPDATE catalog_meta SET revision=revision+1 WHERE id=1')
            self.db.execute('COMMIT')
        self.db.execute('DROP TABLE aba_copy')
        self.assertCountEqual(raw_catalog(self.db),self.before)
        self.assertEqual(self.apply(),'ROLLED_BACK')
        self.assertEqual(guard(self.db,self.expected),(self.generation,2))
        self.assertEqual(self.apply(revision=2),'COMMITTED')

    def test_overflow_refused_without_wrap(self):
        self.db.execute('UPDATE catalog_meta SET revision=?',(MAX_REVISION,))
        self.assertEqual(self.apply(revision=MAX_REVISION),'ROLLED_BACK')
        self.assertEqual(guard(self.db,self.expected),(self.generation,MAX_REVISION))
        self.assertEqual(raw_catalog(self.db),self.before)

    def test_wrong_metadata_types_or_singleton_fail_retained(self):
        for statement, params in [('UPDATE catalog_meta SET revision=?',('not-integer',)),
                                  ('UPDATE catalog_meta SET revision=?',(1.5,)),
                                  ('UPDATE catalog_meta SET catalog_id=?',(self.generation.encode(),)),
                                  ('UPDATE catalog_meta SET catalog_id=?',('not-uuid',))]:
            with self.subTest(statement=statement,params=params):
                self.db.execute(statement,params)
                before=metadata(self.db)
                self.assertEqual(self.apply(),'ROLLED_BACK')
                self.assertEqual(metadata(self.db),before)
                self.db.execute('UPDATE catalog_meta SET catalog_id=?,revision=0',(self.generation,))
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        self.db.execute('INSERT INTO catalog_meta VALUES(2,?,0)',(str(uuid.uuid4()),))
        self.db.execute('PRAGMA ignore_check_constraints=OFF')
        self.assertEqual(self.apply(),'ROLLED_BACK')
        self.assertEqual(len(metadata(self.db)),2)
        self.assertEqual(raw_catalog(self.db),self.before)

    def test_unknown_schema_and_version_retained(self):
        self.db.execute('CREATE TABLE unexpected(x)')
        before=objects(self.db)
        self.assertEqual(self.apply(),'ROLLED_BACK')
        self.assertEqual(objects(self.db),before)
        self.db.execute('DROP TABLE unexpected')
        self.db.execute('PRAGMA user_version=99')
        self.assertEqual(self.apply(),'ROLLED_BACK')
        self.assertEqual(self.db.execute('PRAGMA user_version').fetchone(),(99,))


    def test_temporary_and_attached_databases_refused_unchanged(self):
        self.db.execute('CREATE TEMP TABLE manager_catalog(x)')
        self.db.execute('INSERT INTO temp.manager_catalog VALUES(17)')
        self.assertEqual(self.apply(),'ROLLED_BACK')
        self.assertEqual(self.db.execute('SELECT x FROM temp.manager_catalog').fetchall(),[(17,)])
        self.db.execute('DROP TABLE temp.manager_catalog')
        self.assertEqual(raw_catalog(self.db),self.before)
        path=Path(self.temp.name)/'other-owned.sqlite'
        other=bootstrap_new_owned_fixture(path)
        other_before=objects(other),metadata(other)
        try:
            self.db.execute('ATTACH DATABASE ? AS other_owned',(str(path),))
            self.assertEqual(self.apply(),'ROLLED_BACK')
            self.assertEqual((objects(other),metadata(other)),other_before)
            self.assertEqual(raw_catalog(self.db),self.before)
            self.db.execute('DETACH DATABASE other_owned')
        finally: other.close()

    def test_utf16_owned_fixture_rejected_not_transcoded(self):
        path=Path(self.temp.name)/'utf16.sqlite'
        db=bootstrap_new_owned_fixture(path,'UTF-16le')
        try:
            before=objects(db),metadata(db)
            self.assertEqual(apply_owned_fixture(db,objects(db),self.generation,0,self.key),'ROLLED_BACK')
            self.assertEqual((objects(db),metadata(db)),before)
            self.assertEqual(db.execute('PRAGMA encoding').fetchone(),('UTF-16le',))
        finally: db.close()

    def test_busy_exposes_exact_sqlite_busy_without_mutation(self):
        other=connect(self.path)
        try:
            other.execute('BEGIN IMMEDIATE')
            with self.assertRaises(sqlite3.OperationalError) as error: self.apply()
            self.assertEqual(error.exception.sqlite_errorcode,sqlite3.SQLITE_BUSY)
            other.execute('ROLLBACK')
            self.assertEqual(raw_catalog(self.db),self.before)
            self.assertEqual(guard(self.db,self.expected),(self.generation,0))
        finally: other.close()

    def test_preentry_cancel_rolls_back_revision_and_row(self):
        for fault in ['after_claim','after_update','before_commit_entry']:
            def interrupt(stage,db):
                if stage==fault: raise InterruptedError('owned cancellation injection')
            self.assertEqual(self.apply(hook=interrupt),'ROLLED_BACK')
            self.assertEqual(raw_catalog(self.db),self.before)
            self.assertEqual(guard(self.db,self.expected),(self.generation,0))

    def test_readback_mismatch_rolls_back(self):
        def corrupt(stage,db):
            if stage=='after_update': db.execute("UPDATE manager_catalog SET badge_text='wrong' WHERE engine_key=' e '")
        self.assertEqual(self.apply(hook=corrupt),'ROLLED_BACK')
        self.assertEqual(raw_catalog(self.db),self.before)
        self.assertEqual(guard(self.db,self.expected),(self.generation,0))

    def test_postentry_failure_never_claims_rollback_or_replays(self):
        def interrupted_receipt(stage,db):
            if stage=='after_commit': raise InterruptedError('receipt interruption after commit')
        self.assertEqual(self.apply(hook=interrupted_receipt),'INDETERMINATE')
        self.db=connect(self.path)
        self.assertEqual(guard(self.db,self.expected),(self.generation,1))
        self.assertNotEqual(raw_catalog(self.db),self.before)
        self.assertEqual(self.apply(),'ROLLED_BACK')  # old snapshot cannot replay
        self.assertEqual(guard(self.db,self.expected),(self.generation,1))


    def test_entry_interruption_closes_connection_without_false_rollback_receipt(self):
        def interrupted_entry(stage,db):
            if stage=='commit_entered': raise InterruptedError('commit entry interrupted')
        self.assertEqual(self.apply(hook=interrupted_entry),'INDETERMINATE')
        self.db=connect(self.path)
        self.assertEqual(raw_catalog(self.db),self.before)
        self.assertEqual(guard(self.db,self.expected),(self.generation,0))

    def test_failed_rollback_is_indeterminate(self):
        def close_before_rollback(stage,db):
            if stage=='after_update':
                db.close()
                raise InterruptedError('lost connection')
        self.assertEqual(self.apply(hook=close_before_rollback),'INDETERMINATE')
        self.db=connect(self.path)
        self.assertEqual(raw_catalog(self.db),self.before)  # later readback, not original claimed rollback

    def test_bootstrap_refuses_existing_file_unchanged(self):
        before=self.path.read_bytes()
        with self.assertRaises(FileExistsError): bootstrap_new_owned_fixture(self.path)
        self.assertEqual(self.path.read_bytes(),before)
        self.assertEqual(raw_catalog(self.db),self.before)


if __name__ == '__main__':
    print(f'HOST owned writer SQL fixtures: native SQLite {sqlite3.sqlite_version}; Android/auth/all-writer coverage NOT_RUN',flush=True)
    unittest.main(verbosity=2)
