"""Static fixture boundary checks; not Android execution or compiled APK proof."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
F = ROOT / 'storage-fixture'
A = '{http://schemas.android.com/apk/res/android}'
class PlatformFixtureContract(unittest.TestCase):
    def test_no_authored_permissions_components_or_production_target(self):
        for location in ('main', 'androidTest'):
            manifest = ET.parse(F / 'src' / location / 'AndroidManifest.xml').getroot()
            self.assertEqual([x.tag for x in manifest], ['application'])
            app = manifest.find('application')
            self.assertEqual(list(app), [])
            self.assertEqual(app.get(A+'testOnly'), 'true')
            self.assertEqual(app.get(A+'allowBackup'), 'false')
            self.assertEqual(app.get(A+'fullBackupContent'), 'false')
            self.assertEqual(app.get(A+'dataExtractionRules'), '@xml/data_extraction_rules')
    def test_exact_build_inputs_and_identities(self):
        build = (F/'build.gradle').read_text()
        for line in ["applicationId 'vn.fonesi.fixture.storage'", "testApplicationId 'vn.fonesi.fixture.storage.test'",
                     "testInstrumentationRunner 'vn.fonesi.fixture.storage.StorageInstrumentation'",
                     "'../core/src/main/java'", "'../app/src/main/java/vn/fonesi/app/storage'", "main.assets.srcDir '../storage/sql'"]:
            self.assertIn(line, build)
        for forbidden in ('androidx.', 'junit:', 'implementation ', 'androidTestImplementation ', '../app/src/main/java\''):
            self.assertNotIn(forbidden, build)
    def test_no_backup_resource_drift(self):
        original = (ROOT/'app/src/main/res/xml/data_extraction_rules.xml').read_bytes()
        for kind in ('main','androidTest'):
            self.assertEqual((F/'src'/kind/'res/xml/data_extraction_rules.xml').read_bytes(), original)
    def test_runner_worker_exact_context_and_no_ui_shell(self):
        text = (F/'src/androidTest/java/vn/fonesi/fixture/storage/StorageInstrumentation.java').read_text()
        for required in ('extends Instrumentation', 'start();', 'void onStart()', 'getTargetContext()', 'StorageFixture.run(', 'finish('):
            self.assertIn(required, text)
        for forbidden in ('startActivity', 'UiAutomation', 'exec(', 'androidx.', 'JUnit', 'arguments.get'):
            self.assertNotIn(forbidden, text)
    def test_owned_fresh_creation_no_reset(self):
        text = (F/'src/main/java/vn/fonesi/app/storage/StorageFixture.java').read_text()
        for required in ('OsConstants.O_EXCL', 'OsConstants.O_NOFOLLOW', '0600', 'Os.fstat(descriptor)',
                         'finally { Os.close(descriptor); }', 'failure.errno == OsConstants.ENOENT',
                         'same(directory, observe(parent', 'same(owned, observe(file', 'sidecarsAbsent();'):
            self.assertIn(required, text)
        for forbidden in ('delete(', 'deleteDatabase', 'CREATE_IF_NECESSARY', 'openOrCreateDatabase', 'chmod', 'setWritable', 'ContextWrapper', 'readNBytes', 'Map.of('):
            self.assertNotIn(forbidden, text)
    def test_case_and_result_boundaries(self):
        text = (F/'src/main/java/vn/fonesi/app/storage/StorageFixture.java').read_text()
        self.assertIn('{"missing", "fresh_read", "commit_readback", "stale", "cancelled"}', text)
        for value in ('count <= 0', 'BuildConfig.SOURCE_SHA', 'putIntArray("assertions"', 'typeof(badge_text)',
                      'hex(CAST(badge_text AS BLOB))', 'token.requireActive(); token.close();',
                      's == exact.snapshot && k == exact.key && v == exact.style', 'getSystemService(UserManager.class)'):
            self.assertIn(value, text)
        for column in ['engine_key','clone_id','package_name','label','group_name','legacy_launch_token',
                       'accent_key','badge_text','cluster_id','readiness','launch_allowed']:
            self.assertIn('typeof('+column+'),hex(CAST('+column+' AS BLOB))',text)
        self.assertIn('verifyOwned(); sidecarsAbsent(); db.execSQL(sql',text)
        self.assertNotIn('printStackTrace', text)
        self.assertNotIn('getMessage()', text)
if __name__ == '__main__': unittest.main(verbosity=2)
