"""Own native SQLite projection fixtures; NOT an Android reader/runtime proof."""
from contextlib import closing
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SQL = {name: (ROOT / 'storage/sql' / ('catalog_' + name + '.sql')).read_text()
       for name in ['page_first', 'page_next', 'read_meta']}
FIELDS = ['engine_key', 'clone_id', 'package_name', 'label', 'group_name']
META_COLUMNS = ['catalog_id_type', 'catalog_id_is_null', 'catalog_id_byte_length',
                'catalog_id_over_budget', 'catalog_id_bytes', 'revision_type',
                'revision_valid', 'catalog_revision']
SUFFIXES = ['type', 'is_null', 'byte_length', 'over_budget', 'bytes']
PAGE_COLUMNS = META_COLUMNS + ['row_present', 'row_position'] + [
    field + '_' + suffix for field in FIELDS for suffix in SUFFIXES] + ['readiness_inert', 'launch_inert']
GENERATION = '00000000-0000-0000-0000-000000000123'
MIN, MAX = -(1 << 63), (1 << 63) - 1


class CatalogPageSqlTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'owned.sqlite'
        self.path.open('xb').close()
        self.db = sqlite3.connect(self.path, isolation_level=None)
        self.db.executescript((ROOT / 'storage/sql/catalog_v1_schema.sql').read_text())
        self.db.execute('INSERT INTO catalog_meta VALUES(1,?,0)', (GENERATION,))

    def tearDown(self):
        self.db.close()
        self.temp.cleanup()

    def insert(self, position, engine='owned', clone=None, label='Label', group=None):
        self.db.execute('''INSERT INTO manager_catalog
            (rowid,engine_key,clone_id,package_name,label,group_name,readiness,launch_allowed)
            VALUES(?,?,?,'owned.fixture',?,?,'UNVERIFIED',0)''',
            (position, engine, str(position) if clone is None else clone, label, group))

    def query(self, name, params=(), connection=None):
        cursor = (connection or self.db).execute(SQL[name], params)
        try:
            names = [column[0] for column in cursor.description]
            return names, [dict(zip(names, row)) for row in cursor.fetchall()]
        finally:
            cursor.close()

    def page(self, position=MIN, next_page=False, connection=None):
        names, rows = self.query('page_next' if next_page else 'page_first', (position,), connection)
        self.assertEqual(names, PAGE_COLUMNS)
        self.assertEqual(len(names), 37)
        return rows

    def meta(self, connection=None):
        """Small owned fixture guard, not production Android schema/UTF8 adapter."""
        names, rows = self.query('read_meta', connection=connection)
        self.assertEqual(names, ['meta_id_type', 'meta_id'] + META_COLUMNS)
        if len(rows) != 1:
            raise ValueError('fixture metadata cardinality')
        row = rows[0]
        if not (row['meta_id_type'] == 'integer' and row['meta_id'] == 1
                and row['catalog_id_type'] == 'text' and row['catalog_id_is_null'] == 0
                and row['catalog_id_byte_length'] == 36 and row['catalog_id_over_budget'] == 0
                and isinstance(row['catalog_id_bytes'], bytes)
                and row['revision_type'] == 'integer' and row['revision_valid'] == 1
                and type(row['catalog_revision']) is int and 0 <= row['catalog_revision'] <= MAX):
            raise ValueError('fixture metadata shape')
        value = row['catalog_id_bytes'].decode('ascii')
        if not re.fullmatch(r'[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}', value):
            raise ValueError('fixture generation format')
        return value, row['catalog_revision']

    def fingerprint(self):
        # Compare exact storage classes and raw values, not lossy TEXT decode/file bytes.
        result = []
        for table in ['catalog_meta', 'manager_catalog']:
            fields = [r[1] for r in self.db.execute('PRAGMA table_info(' + table + ')')]
            expression = ','.join('typeof(' + f + '),hex(CAST(' + f + ' AS BLOB))' for f in fields)
            result.append(list(self.db.execute('SELECT rowid,' + expression + ' FROM ' + table + ' ORDER BY rowid')))
        return result

    def test_empty_metadata_sentinel_not_missing_source(self):
        before = self.meta()
        rows = self.page()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['row_present'], 0)
        self.assertTrue(all(rows[0][name] is None for name in PAGE_COLUMNS[9:]))
        self.assertEqual((rows[0]['catalog_id_bytes'].decode(), rows[0]['catalog_revision']), before)
        self.assertEqual(self.meta(), before)

    def test_one_128_129_and_lookahead_limit(self):
        for position in range(1, 131):
            self.insert(position)
            if position in [1, 128, 129, 130]:
                rows = self.page()
                self.assertEqual(len(rows), min(position, 129))
                self.assertTrue(all(r['row_present'] == 1 for r in rows))
                self.assertTrue(all((r['catalog_id_bytes'], r['catalog_revision']) == (GENERATION.encode(), 0) for r in rows))
        self.assertEqual([r['row_position'] for r in self.page(128, True)], [129, 130])

    def test_signed_rowid_zero_min_max_and_no_increment(self):
        positions = [MIN, -7, 0, 2, MAX]
        for p in positions:
            self.insert(p)
        self.assertEqual([r['row_position'] for r in self.page()], positions)
        self.assertTrue(all(r['row_present'] == 1 for r in self.page()))
        self.assertEqual([r['row_position'] for r in self.page(MIN, True)], positions[1:])
        self.assertEqual(self.page(MAX, True)[0]['row_present'], 0)

    def test_exact_opaque_namespace_keys(self):
        pairs = [('', ''), (' ', '\x00x'), ('Case', 'same'), ('case', 'same'), ('other', 'same')]
        for n, (engine, clone) in enumerate(pairs):
            self.insert(n, engine, clone)
        before = self.fingerprint()
        self.assertEqual([(r['engine_key_bytes'], r['clone_id_bytes']) for r in self.page()],
                         [(a.encode(), b.encode()) for a, b in pairs])
        self.assertEqual(self.fingerprint(), before)

    def test_embedded_nul_byte_budget_and_null_empty(self):
        self.insert(1, label='a\x00' + 'b' * 16382)
        self.insert(2, label='a\x00' + 'b' * 16383, group='')
        self.insert(3, label='é' * 8192)
        self.insert(4, label='é' * 8193)
        before = self.fingerprint()
        first, second, third, fourth = self.page()
        self.assertEqual(first['label_byte_length'], 16384)
        self.assertEqual(len(first['label_bytes']), 16384)
        self.assertEqual(second['label_byte_length'], 16385)
        self.assertEqual(second['label_over_budget'], 1)
        self.assertEqual(third['label_byte_length'], 16384)
        self.assertEqual(len(third['label_bytes']), 16384)
        self.assertEqual(fourth['label_byte_length'], 16386)
        self.assertIsNone(fourth['label_bytes'])
        self.assertIsNone(second['label_bytes'])
        self.assertEqual((first['group_name_type'], first['group_name_is_null'], first['group_name_bytes']), ('null', 1, None))
        self.assertEqual((second['group_name_type'], second['group_name_is_null'], second['group_name_bytes']), ('text', 0, b''))
        self.assertEqual(self.fingerprint(), before)

    def test_invalid_utf8_and_blob_affinity_retained_not_repaired(self):
        self.insert(1)
        self.db.execute("UPDATE manager_catalog SET engine_key=CAST(X'FF00' AS TEXT), label=?, group_name=?", (b'\xff' * 20000, b'\x00'))
        before = self.fingerprint()
        row = self.page()[0]
        self.assertEqual(row['engine_key_type'], 'text')
        self.assertEqual(row['engine_key_bytes'], b'\xff\x00')
        self.assertEqual(row['label_type'], 'blob')
        self.assertEqual(row['label_byte_length'], -1)
        self.assertEqual(row['label_over_budget'], 0)
        self.assertIsNone(row['label_bytes'])
        self.assertIsNone(row['group_name_bytes'])
        self.assertEqual(self.fingerprint(), before)

    def test_metadata_large_wrong_type_projection_is_bounded(self):
        self.db.execute('UPDATE catalog_meta SET catalog_id=?,revision=?', ('a\x00' + 'b' * 20000, 'x' * 20000))
        before = self.fingerprint()
        row = self.page()[0]
        self.assertEqual(row['catalog_id_byte_length'], 20002)
        self.assertEqual(row['catalog_id_over_budget'], 1)
        self.assertIsNone(row['catalog_id_bytes'])
        self.assertEqual(row['revision_type'], 'text')
        self.assertEqual(row['revision_valid'], 0)
        self.assertIsNone(row['catalog_revision'])
        with self.assertRaises(ValueError):
            self.meta()
        self.assertEqual(self.fingerprint(), before)

    def test_inert_flags_are_exact_type_and_bytes(self):
        self.insert(1)
        self.assertEqual((self.page()[0]['readiness_inert'], self.page()[0]['launch_inert']), (1, 1))
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        for readiness, launch in [('unverified', 0), ('UNVERIFIED\x00', 0), (b'UNVERIFIED', 0), ('UNVERIFIED', b'0')]:
            self.db.execute('UPDATE manager_catalog SET readiness=?,launch_allowed=?', (readiness, launch))
            before = self.fingerprint()
            row = self.page()[0]
            self.assertNotEqual((row['readiness_inert'], row['launch_inert']), (1, 1))
            self.assertEqual(self.fingerprint(), before)
        self.db.execute('PRAGMA ignore_check_constraints=OFF')

    def test_missing_extra_wrong_id_metadata_never_empty_success(self):
        self.db.execute('DELETE FROM catalog_meta')
        self.assertEqual(self.page(), [])
        with self.assertRaises(ValueError): self.meta()
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        self.db.execute('INSERT INTO catalog_meta VALUES(2,?,0)', (GENERATION,))
        with self.assertRaises(ValueError): self.meta()
        self.db.execute('INSERT INTO catalog_meta VALUES(1,?,0)', (GENERATION,))
        self.assertEqual(len(self.query('read_meta')[1]), 2)
        with self.assertRaises(ValueError): self.meta()

    def test_fresh_post_close_metadata_detects_revision_and_shrink(self):
        self.insert(1)
        with closing(sqlite3.connect('file:' + str(self.path) + '?mode=ro', uri=True, isolation_level=None)) as reader:
            snapshot = self.meta(reader)
            rows = self.page(connection=reader)  # query closes cursor before next independent read
            self.assertEqual(rows[0]['catalog_revision'], snapshot[1])
            self.db.execute('BEGIN IMMEDIATE')
            self.db.execute('DELETE FROM manager_catalog')
            self.db.execute('UPDATE catalog_meta SET revision=revision+1')
            self.db.execute('COMMIT')
            self.assertNotEqual(self.meta(reader), snapshot)
            self.assertEqual(self.page(connection=reader)[0]['row_present'], 0)

    def test_generation_change_with_same_revision_is_stale(self):
        snapshot = self.meta()
        self.page()
        self.db.execute('UPDATE catalog_meta SET catalog_id=?', ('00000000-0000-0000-0000-000000000456',))
        self.assertNotEqual(self.meta(), snapshot)

    def test_negative_revision_and_invalid_generation_are_not_accepted(self):
        self.db.execute('PRAGMA ignore_check_constraints=ON')
        self.db.execute('UPDATE catalog_meta SET revision=-1')
        self.assertEqual(self.page()[0]['revision_valid'], 0)
        with self.assertRaises(ValueError): self.meta()
        self.db.execute("UPDATE catalog_meta SET revision=0,catalog_id=CAST(X'FF' AS TEXT)")
        with self.assertRaises(ValueError): self.meta()


if __name__ == '__main__':
    print('Owned native SQLite version=' + sqlite3.sqlite_version, flush=True)
    unittest.main(verbosity=2)
