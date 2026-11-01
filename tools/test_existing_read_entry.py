"""Source invariants only; no Android file/SQLite/access-boundary execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'app/src/main/java/vn/fonesi/app/storage/ExistingCatalogRead.java').read_text()


class ExistingReadSourceTest(unittest.TestCase):
    def test_fixed_readonly_unwired_non_deleting_entry(self):
        self.assertIn('final class ExistingCatalogRead', SOURCE)
        self.assertNotIn('public final class ExistingCatalogRead', SOURCE)
        self.assertIn('private static final String LEAF = "manager-catalog-v1.sqlite"', SOURCE)
        self.assertIn('SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS', SOURCE)
        self.assertIn('throw new SQLiteDatabaseCorruptException("Catalog source unavailable")', SOURCE)
        for forbidden in ['CREATE_IF_NECESSARY', 'OPEN_READWRITE', 'openOrCreateDatabase(',
                          'SQLiteOpenHelper', '.delete(', '.mkdir(', '.mkdirs(', '.execSQL(',
                          '.setVersion(', '.setLocale(', '.enableWriteAheadLogging(', 'Os.chmod(',
                          'Os.chown(', 'Os.umask(', 'Log.', 'printStackTrace(', 'getMessage(']:
            self.assertNotIn(forbidden, SOURCE)
        self.assertNotIn('ExistingCatalogRead', (ROOT / 'app/src/main/java/vn/fonesi/app/MainActivity.java').read_text())

    def test_exclusivity_and_access_before_observation(self):
        opening = SOURCE[SOURCE.index('    static Result openExisting('):]
        self.assertLess(opening.index('RESERVED.compareAndSet(false, true)'), opening.index('check(cancellation, access, request)'))
        self.assertLess(opening.index('check(cancellation, access, request)'), opening.index('context.getApplicationContext()'))
        self.assertLess(opening.index('available(application)'), opening.index('application.getNoBackupFilesDir()'))
        self.assertIn('interface AccessBoundary { void check(Request request); }', SOURCE)
        self.assertIn('static final class Request { }', SOURCE)
        self.assertIn('new Result(Status.BUSY, null)', opening)
        self.assertNotIn('implements AccessBoundary', SOURCE)

    def test_exact_observations_and_sidecar_errors(self):
        for expected in ['Os.lstat(path.getPath())', 'OsConstants.S_ISDIR(stat.st_mode)',
                         'OsConstants.S_ISREG(stat.st_mode)', 'OsConstants.S_ISLNK(stat.st_mode)',
                         'stat.st_mode & 07777', 'CatalogOwnership.assess(',
                         'originalParent.getCanonicalFile().equals(parent)',
                         'directory.verify(observed(originalParent', 'directory.verify(observed(parent',
                         'leaf.verify(current)', 'file.getCanonicalFile().equals(file)',
                         'file.getParentFile().equals(parent)', 'new String[]{"-journal", "-wal", "-shm"}']:
            self.assertIn(expected, SOURCE)
        sidecars = SOURCE[SOURCE.index('    private static void noSidecars'):SOURCE.index('    private static void available')]
        self.assertIn('if (error.errno == OsConstants.ENOENT) continue;', sidecars)
        self.assertIn('throw error;', sidecars)
        self.assertIn('throw new Failure(Status.REJECTED)', sidecars)
        self.assertNotIn('.exists()', SOURCE)

    def test_external_cursor_uncertainty_is_owner_only_sticky_before_close(self):
        quarantine = SOURCE[SOURCE.index('        void quarantineResources()'):SOURCE.index('        @Override public void close()')]
        self.assertIn('Thread.currentThread() == owner && !closed', quarantine)
        self.assertIn('invalidated = true', quarantine)
        self.assertIn('externalResourcesUncertain = true', quarantine)
        self.assertNotIn('RESERVED', quarantine)
        close = SOURCE[SOURCE.index('        @Override public void close()'):SOURCE.index('    private static final class Identity')]
        check = 'if (externalResourcesUncertain) throw new Failure(Status.RESOURCE_UNCERTAIN)'
        self.assertLess(close.index('database.close()'), close.index(check))
        self.assertLess(close.index(check), close.index('closeUncertain = false'))
        self.assertLess(close.index(check), close.index('RESERVED.set(false)'))

    def test_resource_transfer_close_failure_quarantine(self):
        close = SOURCE[SOURCE.index('        @Override public void close()'):SOURCE.index('    private static final class Identity')]
        self.assertLess(close.index('require(Thread.currentThread() == owner)'), close.index('if (closed) {'))
        self.assertLess(close.index('database.close()'), close.index('RESERVED.set(false)'))
        self.assertIn('catch (RuntimeException error) { throw new Failure(Status.RESOURCE_UNCERTAIN); }', close)
        self.assertNotIn('finally', close)
        self.assertIn('if (closeUncertain) throw new Failure(Status.RESOURCE_UNCERTAIN)', close)
        self.assertLess(close.index('closeUncertain = true'), close.index('database.close()'))
        self.assertIn('if (uncertain) status = Status.RESOURCE_UNCERTAIN;\n                else RESERVED.set(false);', SOURCE)
        self.assertIn('} else if (openAttempted) {', SOURCE)
        self.assertIn('if (!transferred)', SOURCE)
        self.assertIn('require(!closed && !invalidated)', SOURCE)
        lease_use = SOURCE[SOURCE.index('        SQLiteDatabase database()'):SOURCE.index('        /** Owner only, before close:')]
        self.assertIn('verifyPaths(originalParent, parent, file, directory, leaf)', lease_use)
        self.assertEqual(lease_use.count('check(cancellation, access, request)'), 2)
        self.assertIn('static final class Failure extends RuntimeException', SOURCE)
        self.assertNotIn('private static final class Failure', SOURCE)
        self.assertIn('catch (OperationCanceledException error)', lease_use)
        self.assertIn('catch (Exception error)', lease_use)
        self.assertIn('throw new Failure(error.status)', lease_use)
        self.assertNotIn('throw error', lease_use)
        self.assertEqual(lease_use.count('invalidated = true'), 3)
        self.assertEqual(lease_use.count('require(!closed && !invalidated)'), 2)
        self.assertGreater(lease_use.rindex('require(!closed && !invalidated)'), lease_use.rindex('check(cancellation, access, request)'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
