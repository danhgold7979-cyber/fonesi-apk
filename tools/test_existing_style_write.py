"""Source ordering/retention predicates only; never an Android write or auth proof."""
from pathlib import Path
import hashlib
import unittest
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/java/vn/fonesi/app/storage'
SOURCE=(BASE/'ExistingStyleWrite.java').read_text()
PATH=(BASE/'CatalogPath.java').read_text()
READ=(BASE/'ExistingCatalogRead.java').read_text()

class ExistingStyleWriteTest(unittest.TestCase):
    def test_no_runtime_grant_bootstrap_or_unreviewed_sql(self):
        self.assertIn('final class ExistingStyleWrite',SOURCE)
        self.assertNotIn('public final class ExistingStyleWrite',SOURCE)
        for forbidden in ['CREATE_IF_NECESSARY','openOrCreateDatabase','SQLiteOpenHelper','execSQL(',
                          '.delete(','.mkdir(','.mkdirs(','Os.chmod','Os.chown','getMessage(',
                          'Log.','printStackTrace','new OwnedCatalogWriter.CommitBoundary']:
            self.assertNotIn(forbidden,SOURCE)
        self.assertNotIn('ExistingStyleWrite',(ROOT/'app/src/main/java/vn/fonesi/app/MainActivity.java').read_text())
        from run_host_checks import COMMANDS
        self.assertEqual(COMMANDS.count(['python3','tools/test_existing_style_write.py']),1)
        from compile_scope import requires_compile
        self.assertTrue(requires_compile(['app/src/main/java/vn/fonesi/app/storage/ExistingStyleWrite.java'],'push'))

    def test_reservation_and_exact_request_before_first_observation(self):
        entry=SOURCE[SOURCE.index('    static Result apply('):]
        self.assertLess(entry.index('CatalogProcessGate.tryAcquire()'),entry.index('opening.authorize()'))
        self.assertLess(entry.index('opening.authorize()'),entry.index('context.isDeviceProtectedStorage()'))
        self.assertLess(entry.index('opening.authorize()'),entry.index('context.getApplicationContext()'))
        self.assertLess(entry.index('CatalogPath.available(application)'),entry.index('CatalogPath.existing(application)'))
        self.assertIn('boundary.checkBeforeStaging(request.snapshot, request.key, request.style)',SOURCE)
        self.assertIn('final OwnedCatalogWriter.Snapshot snapshot',SOURCE)
        self.assertIn('final CatalogKey key',SOURCE)
        self.assertIn('final StyleValue style',SOURCE)

    def test_ro_guard_confirmed_close_before_rw_same_token(self):
        ro=SOURCE.index('SQLiteDatabase.OPEN_READONLY')
        inspection=SOURCE.index('OwnedCatalogWriter.inspectReadOnly(database')
        close=SOURCE.index('try { database.close(); }',inspection)
        clear=SOURCE.index('database = null;',close)
        rw=SOURCE.index('SQLiteDatabase.OPEN_READWRITE')
        self.assertLess(ro,inspection);self.assertLess(inspection,close);self.assertLess(close,clear);self.assertLess(clear,rw)
        self.assertIn('opening.check(application, path, reservation)',SOURCE[clear:rw])
        self.assertNotIn('reservation.close()',SOURCE[ro:rw])
        self.assertEqual(SOURCE.count('CatalogProcessGate.tryAcquire()'),1)
        self.assertEqual(SOURCE.count('throw new SQLiteDatabaseCorruptException("Catalog source unavailable")'),2)
        self.assertIn('failure.uncertain) { uncertain = true',SOURCE)

    def test_writer_delegation_and_open_budget_handoff(self):
        self.assertIn('OwnedCatalogWriter.apply(database, sql, request.snapshot, request.key,\n                    request.style, cancellation, boundary)',SOURCE)
        self.assertIn('openingBudgetMillis < 1 || openingBudgetMillis > 5000',SOURCE)
        self.assertIn('now - start >= millis',SOURCE)
        handoff=SOURCE.index('            writerEntered = true;')
        cleanup=SOURCE.index('        } finally {',handoff)
        self.assertNotIn('opening.check(',SOURCE[handoff:])
        self.assertNotIn('opening.budget(',SOURCE[handoff:])
        self.assertNotIn('cancellation.throwIfCanceled()',SOURCE[handoff:])
        self.assertNotIn('reservation.close()',SOURCE[handoff:cleanup])

    def test_known_commit_revision_and_other_writer_facts_retained(self):
        self.assertIn('committedRevision = writer.committedRevision',SOURCE)
        self.assertIn('return new Result(status, writer == null ? null : writer.status, committedRevision)',SOURCE)
        mapping=SOURCE[SOURCE.index('    private static Status uncertainStatus('):SOURCE.index('    private static void quarantine(')]
        self.assertLess(mapping.index('revision >= 0'),mapping.index('writer == null && entered'))
        self.assertIn('Status.COMMITTED_RESOURCE_UNCERTAIN',mapping)
        self.assertIn('writer.status == OwnedCatalogWriter.Status.INDETERMINATE',mapping)
        self.assertIn('return Status.RESOURCE_UNCERTAIN',mapping)
        self.assertNotIn('committedRevision = -1',SOURCE[SOURCE.index('        } finally {'):])

    def test_unknown_native_or_close_outcome_quarantines_before_release(self):
        self.assertEqual(SOURCE.count('nativeOpenPending = true;'),2)
        self.assertEqual(SOURCE.count('nativeOpenPending = false;'),3) # declaration and two successful opens
        final=SOURCE[SOURCE.index('        } finally {'):SOURCE.index('    private static Status uncertainStatus(')]
        self.assertIn('if (nativeOpenPending) uncertain = true',final)
        self.assertLess(final.index('database.close()'),final.index('if (uncertain)'))
        self.assertIn('if (uncertain) {\n                quarantine(reservation);',final)
        self.assertLess(final.index('quarantine(reservation)'),final.index('reservation.close()'))
        self.assertEqual(SOURCE.count('SQLiteDatabase.openDatabase('),2)
        self.assertNotIn('while (',SOURCE)

    def test_extracted_observer_and_verify_predicates_match_reviewed_predecessor(self):
        # Hashes derive from reviewed b480607 source, not new rewritten predicates.
        self.assertEqual(hashlib.sha256((PATH[PATH.index('    private static StructStat observed('):PATH.index('    private static void noSidecars(')].strip()).encode()).hexdigest(), '8ad7720b2051e5bf4a112e5d450f9f21ba7adb73f48e5e5651d374e670cc13f9')
        self.assertEqual(hashlib.sha256((PATH[PATH.index('    private static void verifyPaths('):PATH.rindex('\n}')].strip()).encode()).hexdigest(), '03f57581a435afe81d22d987fee74eb1fb8015fa395d4eab0fdb83cb9ad7afbf')

    def test_shared_path_is_observation_only_and_read_entry_stays_readonly(self):
        self.assertIn('private static final String LEAF = "manager-catalog-v1.sqlite"',PATH)
        for forbidden in ['openDatabase(', 'CREATE_IF_NECESSARY', 'OPEN_READWRITE', '.delete(',
                          '.mkdir(', '.mkdirs(', 'Os.chmod', 'Os.chown', 'execSQL(']:
            self.assertNotIn(forbidden,PATH)
        self.assertIn('CatalogPath.existing(application)',READ)
        self.assertIn('SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS',READ)
        self.assertNotIn('OPEN_READWRITE',READ)
        self.assertIn('path.verify()',READ)
        self.assertIn('catch (CatalogPath.Missing error)',READ)

if __name__=='__main__': unittest.main(verbosity=2)
