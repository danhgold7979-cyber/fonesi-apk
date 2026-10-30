"""Focused audited-source tripwires, not a complete Android API analyzer/runtime test."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'rebuild-android'
SOURCE = APP / 'app/src/main/java'


class ApiBoundaryTests(unittest.TestCase):
    def test_current_android_source_avoids_known_kernel_api_gaps(self):
        files = list(SOURCE.rglob('*.java'))
        self.assertEqual({file.name for file in files},
                         {'ManagerActivity.java', 'ManagerModel.java', 'ManagerController.java',
                          'CatalogReader.java', 'CatalogSql.java', 'CatalogPage.java',
                          'CatalogLoader.java', 'CatalogRequestGate.java', 'CatalogOwnership.java'})
        for file in files:
            source = file.read_text()
            for symbol in ('HexFormat', '.isBlank(', '.strip(', '.toList(', '.writeBytes(',
                           'java.util.stream', 'Thread.ofVirtual', 'java.lang.Record'):
                self.assertNotIn(symbol, source, str(file))
            self.assertIsNone(re.search(r'\b(?:List|Set|Map)\s*\.\s*(?:of|copyOf)\s*\(', source))
            self.assertNotIn('SuppressLint', source)

    def test_no_kernel_dependency_or_unreviewed_core_api_backport(self):
        settings = (APP / 'settings.gradle.kts').read_text()
        build = (APP / 'app/build.gradle.kts').read_text()
        self.assertEqual(re.findall(r'include\("([^"]+)"\)', settings), [':app'])
        self.assertNotIn('rebuild-kernel', settings + build)
        self.assertNotIn('coreLibraryDesugaring', build)
        self.assertNotIn('desugar_jdk_libs', build)
        self.assertIn('minSdk = 26', build)
        self.assertIn('JavaVersion.VERSION_17', build)
        self.assertIn('warningsAsErrors = true', build)
        self.assertNotIn('disable', build)
        self.assertNotIn('baseline', build)

    def test_host_test_only_apis_are_not_packaged_source(self):
        production = '\n'.join(file.read_text() for file in SOURCE.rglob('*.java'))
        for symbol in ('java.lang.reflect', 'CountDownLatch', 'setAccessible'):
            self.assertNotIn(symbol, production)
        # TimeUnit is now approved for the bounded application-owned executor (API1),
        # not a Java21 kernel import or device execution claim.
        self.assertTrue((APP / 'contract-tests/ManagerModelTest.java').is_file())
        self.assertTrue((APP / 'contract-tests/ManagerControllerTest.java').is_file())


if __name__ == '__main__':
    unittest.main()
