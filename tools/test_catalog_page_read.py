"""Dormant adapter source and exact SQL interface checks, not Android execution."""
import hashlib
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT/'app/src/main/java/vn/fonesi/app/storage/CatalogPageRead.java').read_text()

class PageReadSourceTest(unittest.TestCase):
    def test_no_runtime_dispatch_or_mutating_storage(self):
        self.assertIn('final class CatalogPageRead', SOURCE)
        self.assertNotIn('public final class CatalogPageRead', SOURCE)
        for forbidden in ['openDatabase(', 'openOrCreateDatabase(', 'execSQL(', 'beginTransaction(',
                          'setTransactionSuccessful(', 'OwnedCatalogWriter.apply(', '.delete(',
                          'new ExistingCatalogRead.Request(', 'Log.', 'getMessage(', 'printStackTrace(']:
            self.assertNotIn(forbidden, SOURCE)
        main = (ROOT/'app/src/main/java/vn/fonesi/app/MainActivity.java').read_text()
        self.assertNotIn('CatalogPageRead', main)
        self.assertIn('ExistingCatalogRead.openExisting(context, request,', SOURCE)
        self.assertIn('access.check(exactRequest)', SOURCE)
        uncertain = SOURCE.index('if (opened.status == ExistingCatalogRead.Status.RESOURCE_UNCERTAIN)')
        ordinary = SOURCE.index('if (opened.status != ExistingCatalogRead.Status.OPEN_UNVALIDATED)')
        self.assertLess(uncertain, ordinary)
        self.assertNotIn('budget.check()', SOURCE[uncertain:ordinary])

    def test_exact_assets_and_native_sql_column_interfaces(self):
        pins = re.findall(r'asset\(assets, "([^"]+)", "([0-9a-f]{64})"\)', SOURCE)
        self.assertEqual(len(pins), 3)
        for name, digest in pins:
            self.assertEqual(hashlib.sha256((ROOT/'storage/sql'/name).read_bytes()).hexdigest(), digest)
        db = sqlite3.connect(':memory:')
        try:
            db.executescript((ROOT/'storage/sql/catalog_v1_schema.sql').read_text())
            meta = re.findall(r'"([a-z_]+)"', SOURCE.split('private static final String[] META = {')[1].split('};')[0])
            page = meta[2:] + ['row_present','row_position']
            for field in ['engine_key','clone_id','package_name','label','group_name']:
                page += [field+suffix for suffix in ['_type','_is_null','_byte_length','_over_budget','_bytes']]
            page += ['readiness_inert','launch_inert']
            for name, expected, args in [('catalog_read_meta.sql',meta,()),
                                          ('catalog_page_first.sql',page,(-2**63,)),
                                          ('catalog_page_next.sql',page,(2**63-1,))]:
                cursor=db.execute((ROOT/'storage/sql'/name).read_text(),args)
                self.assertEqual([col[0] for col in cursor.description],expected)
        finally: db.close()
        self.assertIn('Arrays.equals(rows.getColumnNames(), expected)', SOURCE)

    def test_typed_getters_and_payload_guard_before_blob(self):
        field=SOURCE[SOURCE.index('    private static void field('):SOURCE.index('    private static Object[] projection(')]
        self.assertLess(field.index('length >= 0 && length <= max'), field.index('rows.getBlob('))
        self.assertLess(field.index('Cursor.FIELD_TYPE_BLOB'), field.index('rows.getBlob('))
        self.assertIn('value.length == length', field)
        integer=SOURCE[SOURCE.index('    private static Object integer('):SOURCE.index('    private static Object kind(')]
        self.assertLess(integer.index('Cursor.FIELD_TYPE_INTEGER'),integer.index('rows.getLong('))
        self.assertIn('query.bindLong(1, position.longValue())',SOURCE)
        self.assertIn('}, sql, null, null, budget.signal)',SOURCE)
        self.assertIn('Long.MIN_VALUE : continuation.lastAcceptedPosition()',SOURCE)

    def test_lookahead_transport_and_close_before_finish(self):
        projection=SOURCE[SOURCE.index('    private static Object[] projection('):SOURCE.index('    private static Object[] metadata(')]
        self.assertLess(projection.index('if (lookahead) return raw'),projection.index('for (int offset = 10'))
        self.assertLess(projection.index('raw[9] = integer'),projection.index('if (lookahead) return raw'))
        self.assertIn('projection(rows, submitted == 129)',SOURCE)
        self.assertIn('if (step == CatalogPage.Step.STOP_BEFORE_ROW) break',SOURCE)
        self.assertIn('            }\n            builder.endInput();\n            frame.inspect(assets);\n            page = builder.finish(metadata(frame, meta));',SOURCE)
        meta=SOURCE[SOURCE.index('    private static Object[] metadata('):SOURCE.index('    private static String asset(')]
        self.assertIn('require(rows.moveToFirst())',meta)
        self.assertIn('require(!rows.moveToNext())',meta)
        self.assertIn('try (ReadCursor resource',meta)

    def test_observed_deadline_and_lease_quarantine_order(self):
        for required in ['millis >= 1 && millis <= 5000', 'SystemClock.elapsedRealtime()',
                         'require(now >= start)', 'now - start >= millis', 'Status.DEADLINE',
                         'signal.throwIfCanceled()', 'frame.checked();', 'frame.uncertain = true']:
            self.assertIn(required,SOURCE)
        cleanup=SOURCE[SOURCE.index('        } finally {'):SOURCE.index('    private static Status entryStatus')]
        self.assertLess(cleanup.index('lease.quarantineResources()'),cleanup.index('lease.close()'))
        self.assertIn('status == Status.READ_INTERNAL_ONLY ? page : null',cleanup)
        self.assertIn('catch (RuntimeException failure) { status = Status.RESOURCE_UNCERTAIN; }',cleanup)

    def test_mandatory_host_and_changed_compile_inputs(self):
        from run_host_checks import COMMANDS
        self.assertEqual(COMMANDS.count(['python3','tools/test_catalog_page_read.py']),1)
        from compile_scope import requires_compile
        for name in ['CatalogPageRead','ExistingCatalogRead','OwnedCatalogWriter']:
            self.assertTrue(requires_compile(['app/src/main/java/vn/fonesi/app/storage/'+name+'.java'],'push'))
        self.assertNotIn('return new Result(Status.READ_INTERNAL_ONLY,',SOURCE)

if __name__=='__main__': unittest.main(verbosity=2)
