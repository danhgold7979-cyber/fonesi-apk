"""Static source and synthetic ZIP predicates; NOT Android SQLite/auth execution."""
import io
import hashlib
import re
import sqlite3
from pathlib import Path
import unittest
import warnings
import zipfile
from verify_apk import validate_catalog_assets, CATALOG_ASSETS

ROOT = Path(__file__).resolve().parents[1]


class AndroidCatalogContractTest(unittest.TestCase):
    def archive(self, mutation=None):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            for name in CATALOG_ASSETS:
                if mutation == ('missing', name): continue
                data = (ROOT / 'storage/sql' / name).read_bytes()
                if mutation == ('changed', name): data += b'changed'
                archive.writestr('assets/' + name, data)
            if mutation and mutation[0] == 'duplicate':
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    archive.writestr('assets/' + mutation[1], b'other')
            if mutation and mutation[0] == 'extra': archive.writestr('assets/unreviewed.sql', b'SELECT 1;')
        stream.seek(0)
        return zipfile.ZipFile(stream)

    def test_exact_sql_assets(self):
        with self.archive() as archive:
            self.assertEqual(len(validate_catalog_assets(archive)), 5)

    def test_missing_changed_duplicate_and_extra_assets_rejected(self):
        for name in CATALOG_ASSETS:
            for kind in ['missing', 'changed', 'duplicate']:
                with self.subTest(name=name, kind=kind), self.archive((kind,name)) as archive:
                    with self.assertRaises(AssertionError): validate_catalog_assets(archive)
        with self.archive(('extra','')) as archive:
            with self.assertRaises(AssertionError): validate_catalog_assets(archive)

    def test_adapter_is_package_private_unwired_and_has_no_opener(self):
        p = ROOT / 'app/src/main/java/vn/fonesi/app/storage/OwnedCatalogWriter.java'
        source = p.read_text()
        self.assertIn('final class OwnedCatalogWriter', source)
        self.assertNotIn('public final class OwnedCatalogWriter', source)
        for other in (ROOT / 'app/src/main/java').rglob('*.java'):
            if other != p: self.assertNotIn('OwnedCatalogWriter', other.read_text())
        for forbidden in ['openDatabase(', 'getWritableDatabase(', 'SQLiteOpenHelper',
                          'CREATE_IF_NECESSARY', 'execSQL(', 'startActivity(', 'Log.',
                          'new CommitBoundary', 'implements CommitBoundary', 'System.out']:
            self.assertNotIn(forbidden, source)
        self.assertIn('void checkBeforeStaging(Snapshot snapshot, CatalogKey key, StyleValue style);', source)
        self.assertIn('void atCommit(Snapshot snapshot, CatalogKey key, StyleValue style, CommitAction action);', source)
        self.assertEqual(source.count('boundary.checkBeforeStaging(snapshot, key, style);'), 2)
        self.assertIn('boundary.atCommit(snapshot, key, style, () -> {', source)

    def test_compile_inputs_and_one_shot_guard_source(self):
        source = (ROOT / 'app/src/main/java/vn/fonesi/app/storage/OwnedCatalogWriter.java').read_text()
        for required in ['beginTransactionNonExclusive()', 'executeUpdateDelete()',
                         'query.bindBlob(1, engine)', 'available.compareAndSet(true, false)',
                         'Thread.currentThread() != owner', 'available.set(false)',
                         'Status.INDETERMINATE', 'badge.length() > 4096',
                         'badgeBytes.length > 16384', 'sqlite_temp_master', 'pragma_database_list() LIMIT 3',
                         "CASE WHEN typeof(revision)='integer' THEN revision END",
                         'frame.uncertain = true', 'Tracked<Cursor>', 'Tracked<SQLiteStatement>',
                         'sql IS NULL', 'PRAGMA main.index_xinfo', 'workerOnly()']:
            self.assertIn(required, source)
        build = (ROOT / 'app/build.gradle').read_text()
        self.assertIn("main.java.srcDir '../core/src/main/java'", build)
        self.assertIn("main.assets.srcDir '../storage/sql'", build)
        from compile_scope import requires_compile
        for path in ['app/build.gradle', 'core/src/main/java/vn/fonesi/apk/foundation/CatalogKey.java',
                     'core/src/main/java/vn/fonesi/apk/foundation/StyleValue.java',
                     'storage/sql/catalog_v1_schema.sql', 'storage/sql/style_columns_3_to_4.sql']:
            self.assertTrue(requires_compile([path], 'push'))


    def test_runtime_asset_pins_match_reviewed_source(self):
        source = (ROOT / 'app/src/main/java/vn/fonesi/app/storage/OwnedCatalogWriter.java').read_text()
        pins = re.findall(r'asset\(assets, "([^"]+)", "([0-9a-f]{64})"\)',source)
        self.assertEqual(len(pins),4)
        self.assertEqual({name for name,_ in pins},set(CATALOG_ASSETS)-{'style_columns_3_to_4.sql'})
        for name,digest in pins:
            self.assertEqual(hashlib.sha256((ROOT/'storage/sql'/name).read_bytes()).hexdigest(),digest)

    def test_owned_sql_projection_budget_primitives(self):
        # Native SQLite fixture only; no Android CursorWindow/OEM evidence.
        db=sqlite3.connect(':memory:')
        try:
            db.execute('CREATE TABLE sample(revision)')
            for raw in [b'x'*20000,'x'*20000,1.5,0]:
                db.execute('DELETE FROM sample')
                db.execute('INSERT INTO sample VALUES(?)',(raw,))
                kind,value=db.execute("SELECT typeof(revision),CASE WHEN typeof(revision)='integer' THEN revision END FROM sample").fetchone()
                self.assertEqual(value,0 if kind=='integer' else None)
            alias='x'*500
            db.execute('ATTACH DATABASE ? AS "'+alias+'"',(':memory:',))
            rows=db.execute("SELECT CASE WHEN typeof(name)='text' AND length(CAST(name AS BLOB))<=16 THEN CAST(name AS BLOB) END FROM pragma_database_list() LIMIT 3").fetchall()
            self.assertEqual(rows,[(b'main',),(None,)])
        finally: db.close()


if __name__ == '__main__':
    unittest.main(verbosity=2)
