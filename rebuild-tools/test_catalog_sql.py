"""Execute actual Java SQL literals on synthetic SQLite; not Android CursorWindow acceptance."""
import json
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'rebuild-android/app/src/main/java/vn/fonesi/rebuild/catalog/CatalogSql.java'
MIN_ROWID = -(2 ** 63)
MAX_ROWID = 2 ** 63 - 1
CATALOG_ID = '00000000-0000-4000-8000-000000000001'


def literals():
    source = SOURCE.read_text()
    pattern = r'public static final String (\w+)\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+);'
    result = {}
    for name, expression in re.findall(pattern, source):
        tokens = re.findall(r'"(?:[^"\\]|\\.)*"', expression)
        remainder = re.sub(r'"(?:[^"\\]|\\.)*"', '', expression)
        if remainder.replace('+', '').strip():
            raise AssertionError('SQL is not a fixed literal expression')
        if name in result:
            raise AssertionError('Duplicate SQL name')
        result[name] = ''.join(json.loads(token) for token in tokens)
    required = {'META_DDL', 'CATALOG_DDL', 'FIRST_PAGE', 'NEXT_PAGE', 'FINAL_META', 'META_CARDINALITY'}
    if set(result) != required:
        raise AssertionError('SQL literal surface changed')
    return result


class CatalogSqlTests(unittest.TestCase):
    def setUp(self):
        self.sql = literals()
        self.db = sqlite3.connect(':memory:')
        self.db.row_factory = sqlite3.Row
        self.db.execute(self.sql['META_DDL'])
        self.db.execute(self.sql['CATALOG_DDL'])
        self.db.execute('INSERT INTO catalog_meta VALUES(1,?,0)', (CATALOG_ID,))

    def tearDown(self):
        self.db.close()

    def row(self, position=1, engine='engine', clone='clone', **changes):
        values = dict(rowid=position, engine_key=engine, clone_id=clone, package_name='example.test',
                      label='Label', group_name=None, readiness='UNVERIFIED', launch_allowed=0)
        values.update(changes)
        columns = list(values)
        self.db.execute('INSERT INTO manager_catalog (' + ','.join(columns) + ') VALUES ('
                        + ','.join('?' for _ in columns) + ')', [values[key] for key in columns])

    def page(self, after=None):
        return self.db.execute(self.sql['FIRST_PAGE' if after is None else 'NEXT_PAGE'],
                               (str(MIN_ROWID if after is None else after),)).fetchall()

    def test_actual_literal_first_next_relation(self):
        self.assertEqual(self.sql['FIRST_PAGE'].replace('rowid >= CAST(? AS INTEGER)', 'rowid > CAST(? AS INTEGER)'), self.sql['NEXT_PAGE'])
        self.assertEqual(1, self.sql['FIRST_PAGE'].count('?'))
        self.assertIn('LIMIT 129', self.sql['FIRST_PAGE'])
        for forbidden in ('ROW_NUMBER', 'sqlite_schema', 'table_xinfo', 'table_list'):
            self.assertNotIn(forbidden.lower(), '\n'.join(self.sql.values()).lower())

    def test_exact_ddl_empty_catalog_and_sentinel(self):
        self.assertEqual([], self.db.execute('SELECT * FROM manager_catalog').fetchall())
        rows = self.page()
        self.assertEqual(1, len(rows))
        self.assertEqual(0, rows[0]['row_present'])
        self.assertIsNone(rows[0]['position'])
        self.assertEqual(CATALOG_ID.encode(), rows[0]['catalog_id_bytes'])
        self.assertEqual((1, 1), tuple(self.db.execute(self.sql['META_CARDINALITY']).fetchone()))
        self.assertEqual(1, len(self.db.execute(self.sql['FINAL_META']).fetchall()))

    def test_missing_meta_does_not_become_empty_success(self):
        self.db.execute('DELETE FROM catalog_meta')
        self.row()
        self.assertEqual([], self.page())
        self.assertEqual([], self.db.execute(self.sql['FINAL_META']).fetchall())
        self.assertEqual((0, None), tuple(self.db.execute(self.sql['META_CARDINALITY']).fetchone()))

    def test_extra_metadata_is_detected_by_unfiltered_cardinality(self):
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        self.db.execute('INSERT INTO catalog_meta VALUES(2,?,0)', (CATALOG_ID,))
        self.assertEqual((2, 1), tuple(self.db.execute(self.sql['META_CARDINALITY']).fetchone()))
        self.assertEqual(1, len(self.page()))  # Filtered page alone cannot validate singleton metadata.
        self.db.execute('DELETE FROM catalog_meta WHERE id=1')
        self.assertEqual((1, 0), tuple(self.db.execute(self.sql['META_CARDINALITY']).fetchone()))
        self.assertEqual([], self.page())

    def test_signed_rowid_full_range_without_arithmetic(self):
        positions = [MIN_ROWID, -5, -1, 0, 1, MAX_ROWID]
        for n, position in enumerate(positions):
            self.row(position, clone=str(n))
        self.assertEqual(positions, [row['position'] for row in self.page()])
        for n, position in enumerate(positions):
            rows = self.page(position)
            actual = [row['position'] for row in rows if row['row_present']]
            self.assertEqual(positions[n + 1:], actual)
        self.assertEqual(0, self.page(MAX_ROWID)[0]['row_present'])

    def test_limit_129_lookahead_not_skipped_on_continuation(self):
        for position in range(-130, 10):
            self.row(position, clone=str(position))
        first = self.page()
        self.assertEqual(129, len(first))
        accepted = first[:128]
        next_page = self.page(accepted[-1]['position'])
        self.assertEqual(first[128]['position'], next_page[0]['position'])
        self.assertEqual(list(range(-130, 10)), [row['position'] for row in accepted + next_page])

    def test_exact_empty_space_unicode_opaque_keys_remain_distinct(self):
        pairs = [('', ''), (' ', ''), ('engine', ''), ('engine', ' '),
                 ('a:b', 'c'), ('a', 'b:c'), ('é', 'same'), ('e\u0301', 'same'),
                 ('engine', 'legacy-not-uuid'), ('engine', CATALOG_ID)]
        for position, (engine, clone) in enumerate(pairs):
            self.row(position, engine, clone)
        rows = self.page()
        self.assertEqual(pairs, [(row['engine_key_bytes'].decode('utf-8', 'strict'),
                                 row['clone_id_bytes'].decode('utf-8', 'strict')) for row in rows])
        for row in rows:
            self.assertEqual((1, 1), (row['readiness_inert'], row['launch_inert']))
            self.assertEqual(1, row['group_name_is_null'])
            self.assertIsNone(row['group_name_bytes'])

    def test_field_byte_budget_before_transfer_all_five_fields(self):
        for field in ('engine_key', 'clone_id', 'package_name', 'label', 'group_name'):
            for text in ('x' * 16384, 'x' * 16385, 'é' * 8192, 'é' * 8193):
                with self.subTest(field=field, byte_length=len(text.encode())):
                    self.db.execute('DELETE FROM manager_catalog')
                    self.row(**{field: text})
                    projected = self.page()[0]
                    size = len(text.encode())
                    self.assertEqual(1, projected[field + '_is_text'])
                    self.assertEqual(size, projected[field + '_byte_length'])
                    self.assertEqual(int(size > 16384), projected[field + '_over_budget'])
                    self.assertEqual(None if size > 16384 else text.encode(), projected[field + '_bytes'])

    def test_blob_and_null_flags_never_look_like_empty_text(self):
        # Query-only corruption fixtures relax NOT NULL; real schema validation must reject this DDL.
        self.db.execute('DROP TABLE manager_catalog')
        self.db.execute(self.sql['CATALOG_DDL'].replace(' NOT NULL', ''))
        for field in ('engine_key', 'clone_id', 'package_name', 'label', 'group_name'):
            for value in (None, b'', b'opaque-blob', ''):
                with self.subTest(field=field, value=value):
                    self.db.execute('DELETE FROM manager_catalog')
                    self.row(**{field: value})
                    projected = self.page()[0]
                    self.assertEqual(int(isinstance(value, str)), projected[field + '_is_text'])
                    self.assertEqual(int(value is None), projected[field + '_is_null'])
                    self.assertEqual(0 if value == '' else -1, projected[field + '_byte_length'])
                    self.assertEqual(b'' if value == '' else None, projected[field + '_bytes'])

    def test_invalid_utf8_is_transferred_exactly_for_strict_rejection(self):
        for field in ('engine_key', 'clone_id', 'package_name', 'label', 'group_name'):
            with self.subTest(field=field):
                self.db.execute('DELETE FROM manager_catalog')
                self.row()
                self.db.execute('UPDATE manager_catalog SET ' + field + '=CAST(? AS TEXT)', (b'\xffbad',))
                projected = self.page()[0]
                self.assertEqual(1, projected[field + '_is_text'])
                self.assertEqual(b'\xffbad', projected[field + '_bytes'])
                with self.assertRaises(UnicodeDecodeError):
                    projected[field + '_bytes'].decode('utf-8', 'strict')
                self.assertEqual(b'\xffbad', self.db.execute('SELECT CAST(' + field + ' AS BLOB) FROM manager_catalog').fetchone()[0])

    def test_inert_flags_require_exact_storage_values(self):
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        for readiness, launch, expected in [('UNVERIFIED', 0, (1, 1)), ('unverified', 0, (0, 1)),
                                           ('UNVERIFIED ', 1, (0, 0)), (b'UNVERIFIED', b'0', (0, 0))]:
            with self.subTest(readiness=readiness, launch=launch):
                self.db.execute('DELETE FROM manager_catalog')
                self.row(readiness=readiness, launch_allowed=launch)
                row = self.page()[0]
                self.assertEqual(expected, (row['readiness_inert'], row['launch_inert']))

    def test_metadata_projection_bounds_types_and_revision(self):
        for value in (CATALOG_ID, 'x' * 37, b'uuid-blob', ''):
            with self.subTest(catalog_id=value):
                self.db.execute('UPDATE catalog_meta SET catalog_id=?', (value,))
                row = self.db.execute(self.sql['FINAL_META']).fetchone()
                is_text = isinstance(value, str)
                self.assertEqual(int(is_text), row['catalog_id_is_text'])
                self.assertEqual(int(is_text and len(value.encode()) > 36), row['catalog_id_over_budget'])
                self.assertEqual(value.encode() if is_text and len(value.encode()) <= 36 else None, row['catalog_id_bytes'])
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        for revision in (-1, 0, MAX_ROWID, 1.5, b'0'):
            with self.subTest(revision=revision):
                self.db.execute('UPDATE catalog_meta SET revision=?', (revision,))
                row = self.db.execute(self.sql['FINAL_META']).fetchone()
                self.assertEqual(int(type(revision) is int and revision >= 0), row['revision_valid'])

    def test_final_metadata_query_exposes_revision_change(self):
        self.row()
        old = self.page()[0]
        self.db.execute('UPDATE catalog_meta SET revision=revision+1')
        new = self.db.execute(self.sql['FINAL_META']).fetchone()
        self.assertEqual(0, old['catalog_revision'])
        self.assertEqual(1, new['catalog_revision'])
        self.assertNotEqual(old['catalog_revision'], new['catalog_revision'])

    def test_stored_master_ddl_is_exact_trimmed_literal(self):
        actual = [tuple(row) for row in self.db.execute(
            "SELECT type,name,tbl_name,sql FROM main.sqlite_master ORDER BY name")]
        expected = [('table', 'catalog_meta', 'catalog_meta', self.sql['META_DDL'].strip()),
                    ('table', 'manager_catalog', 'manager_catalog', self.sql['CATALOG_DDL'].strip()),
                    ('index', 'sqlite_autoindex_manager_catalog_1', 'manager_catalog', None)]
        self.assertEqual(expected, actual)

    def test_exact_table_and_index_pragma_shapes(self):
        table_specs = {
            'catalog_meta': [('id', 'INTEGER', 0, 1), ('catalog_id', 'TEXT', 1, 0), ('revision', 'INTEGER', 1, 0)],
            'manager_catalog': [('engine_key', 'TEXT', 1, 1), ('clone_id', 'TEXT', 1, 2),
                ('package_name', 'TEXT', 1, 0), ('label', 'TEXT', 1, 0), ('group_name', 'TEXT', 0, 0),
                ('legacy_launch_token', 'TEXT', 0, 0), ('accent_key', 'TEXT', 0, 0), ('badge_text', 'TEXT', 0, 0),
                ('cluster_id', 'TEXT', 0, 0), ('readiness', 'TEXT', 1, 0), ('launch_allowed', 'INTEGER', 1, 0)]}
        for table, columns in table_specs.items():
            with self.subTest(table=table):
                cursor = self.db.execute('PRAGMA main.table_info(' + table + ')')
                self.assertEqual(6, len(cursor.description))
                self.assertEqual([(cid, name, kind, not_null, None, pk)
                                  for cid, (name, kind, not_null, pk) in enumerate(columns)],
                                 [tuple(row) for row in cursor])
        meta_indexes = self.db.execute('PRAGMA main.index_list(catalog_meta)')
        self.assertEqual(5, len(meta_indexes.description))
        self.assertEqual([], meta_indexes.fetchall())
        indexes = self.db.execute('PRAGMA main.index_list(manager_catalog)')
        self.assertEqual(5, len(indexes.description))
        self.assertEqual([(0, 'sqlite_autoindex_manager_catalog_1', 1, 'pk', 0)], [tuple(row) for row in indexes])
        keys = self.db.execute('PRAGMA main.index_xinfo(sqlite_autoindex_manager_catalog_1)')
        self.assertEqual(6, len(keys.description))
        self.assertEqual([(0, 0, 'engine_key', 0, 'BINARY', 1), (1, 1, 'clone_id', 0, 'BINARY', 1),
                          (2, -1, None, 0, 'BINARY', 0)], [tuple(row) for row in keys])

    def test_bootstrap_recipe_transaction_has_only_meta_and_empty_catalog(self):
        # Executes the approved SQL transaction recipe, not Android file-ownership/bootstrap code.
        with tempfile.TemporaryDirectory(prefix='fonesi-catalog-recipe-') as temporary:
            path = Path(temporary) / 'synthetic.db'
            db = sqlite3.connect(path)
            try:
                db.execute('BEGIN IMMEDIATE')
                db.execute(self.sql['META_DDL'])
                db.execute(self.sql['CATALOG_DDL'])
                db.execute('PRAGMA application_id=1178815793')
                db.execute('PRAGMA user_version=1')
                db.execute('INSERT INTO catalog_meta VALUES(1,?,0)', (CATALOG_ID,))
                db.commit()
            finally:
                db.close()
            reopened = sqlite3.connect(path)
            try:
                self.assertEqual((1178815793,), reopened.execute('PRAGMA application_id').fetchone())
                self.assertEqual((1,), reopened.execute('PRAGMA user_version').fetchone())
                self.assertEqual(('UTF-8',), reopened.execute('PRAGMA encoding').fetchone())
                self.assertEqual([(1, CATALOG_ID, 0)], reopened.execute('SELECT * FROM catalog_meta').fetchall())
                self.assertEqual([], reopened.execute('SELECT * FROM manager_catalog').fetchall())
            finally:
                reopened.close()

    def test_faulted_bootstrap_recipe_rolls_back_but_retains_file(self):
        with tempfile.TemporaryDirectory(prefix='fonesi-catalog-rollback-') as temporary:
            path = Path(temporary) / 'synthetic.db'
            db = sqlite3.connect(path)
            try:
                with self.assertRaisesRegex(RuntimeError, 'synthetic initialization fault'):
                    try:
                        db.execute('BEGIN IMMEDIATE')
                        db.execute(self.sql['META_DDL'])
                        db.execute(self.sql['CATALOG_DDL'])
                        db.execute('PRAGMA application_id=1178815793')
                        db.execute('PRAGMA user_version=1')
                        db.execute('INSERT INTO catalog_meta VALUES(1,?,0)', (CATALOG_ID,))
                        raise RuntimeError('synthetic initialization fault')
                    except RuntimeError:
                        db.rollback()
                        raise
            finally:
                db.close()
            self.assertTrue(path.is_file())
            # Retained incomplete file is not a valid catalog and is not deleted/recreated here.
            reopened = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
            try:
                self.assertEqual([], reopened.execute('SELECT name FROM sqlite_master').fetchall())
                self.assertEqual((0,), reopened.execute('PRAGMA application_id').fetchone())
                self.assertEqual((0,), reopened.execute('PRAGMA user_version').fetchone())
            finally:
                reopened.close()

    def test_readonly_reopen_denies_write_and_preserves_source_bytes(self):
        self.db.commit()
        with tempfile.TemporaryDirectory(prefix='fonesi-catalog-readonly-') as temporary:
            path = Path(temporary) / 'synthetic.db'
            destination = sqlite3.connect(path)
            try:
                self.db.backup(destination)
            finally:
                destination.close()
            before = path.read_bytes()
            readonly = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
            try:
                self.assertEqual(1, len(readonly.execute(self.sql['FIRST_PAGE'], (str(MIN_ROWID),)).fetchall()))
                with self.assertRaises(sqlite3.OperationalError):
                    readonly.execute('UPDATE catalog_meta SET revision=1')
            finally:
                readonly.close()
            self.assertEqual(before, path.read_bytes())

    def test_unknown_schema_objects_violate_exact_recipe_shape(self):
        # Shape assertions only: this does not execute or impersonate CatalogReader.schema().
        expected = [tuple(row) for row in self.db.execute(
            'SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY name')]
        for statement in ('CREATE TABLE unknown_table(value TEXT)',
                          'CREATE INDEX unknown_index ON manager_catalog(label)',
                          'CREATE VIEW unknown_view AS SELECT label FROM manager_catalog'):
            with self.subTest(statement=statement):
                self.db.execute('SAVEPOINT drift')
                try:
                    self.db.execute(statement)
                    actual = [tuple(row) for row in self.db.execute(
                        'SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY name')]
                    self.assertNotEqual(expected, actual)
                    self.assertEqual(len(expected) + 1, len(actual))
                finally:
                    self.db.execute('ROLLBACK TO drift')
                    self.db.execute('RELEASE drift')


if __name__ == '__main__':
    unittest.main()
