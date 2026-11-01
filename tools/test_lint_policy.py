"""Synthetic finite-report and source-contract checks, NOT actual AGP execution."""
import copy
import os
from pathlib import Path
import tempfile
import time
import unittest
import xml.etree.ElementTree as ET
from verify_lint import validate_report, verify_files

ROOT = Path(__file__).resolve().parents[1]


class LintPolicyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle').write_text('android {\n targetSdk 36\n}\n')
        self.report = ET.fromstring('<issues format="6"><issue id="OldTargetApi" severity="Hint" message="Not targeting the latest versions of Android; compatibility modes apply."><location file="app/build.gradle" line="2"/></issue></issues>')

    def tearDown(self):
        self.tmp.cleanup()

    def check(self, report=None):
        return validate_report(ET.tostring(self.report if report is None else report, encoding='unicode'), self.root)

    def test_positive_notice(self):
        self.check()

    def test_zero_duplicate_or_additional_diagnostics(self):
        with self.assertRaises(AssertionError): self.check(ET.fromstring('<issues/>'))
        for id_, severity in [('OldTargetApi', 'Hint'), ('NewApi', 'Error'),
                              ('ExportedService', 'Warning'), ('Overdraw', 'Warning'),
                              ('AndroidGradlePluginVersion', 'Hint')]:
            mutated = copy.deepcopy(self.report)
            ET.SubElement(mutated, 'issue', {'id': id_, 'severity': severity})
            with self.assertRaises(AssertionError): self.check(mutated)

    def test_unknown_or_promoted_severity(self):
        for severity in ['Information', 'Informational', 'Warning', 'Error', 'Fatal', 'Ignore', '']:
            self.report[0].set('severity', severity)
            with self.assertRaises(AssertionError): self.check()

    def test_wrong_source_target_and_message(self):
        for field, value in [('file', 'other.gradle'), ('line', '1'), ('line', '0')]:
            mutated = copy.deepcopy(self.report)
            mutated[0][0].set(field, value)
            with self.assertRaises(AssertionError): self.check(mutated)
        (self.root / 'app/build.gradle').write_text('android {\n targetSdk 35\n}\n')
        with self.assertRaises(AssertionError): self.check()
        (self.root / 'app/build.gradle').write_text('android {\n targetSdk 36\n}\n')
        self.report[0].set('message', 'Unknown target security failure')
        with self.assertRaises(AssertionError): self.check()

    def test_missing_stale_malformed_or_unbound_evidence(self):
        path = self.root / 'lint-results-debug.xml'
        with self.assertRaises(AssertionError): verify_files(path, self.root, 1, 'a'*40, 'a'*40)
        path.write_text(ET.tostring(self.report, encoding='unicode'))
        path.with_suffix('.txt').write_text('OldTargetApi')
        path.with_suffix('.html').write_text('<html>OldTargetApi</html>')
        start = int(time.time()) - 2
        self.assertFalse(verify_files(path, self.root, start, 'a'*40, 'a'*40)['release_acceptance'])
        with self.assertRaises(AssertionError): verify_files(path, self.root, start, 'a'*40, 'b'*40)
        os.utime(path, (1, 1))
        with self.assertRaises(AssertionError): verify_files(path, self.root, start, 'a'*40, 'a'*40)
        path.write_text('<issues')
        with self.assertRaises(ET.ParseError): verify_files(path, self.root, start, 'a'*40, 'a'*40)
        path.write_text(ET.tostring(self.report, encoding='unicode'))
        path.with_suffix('.html').write_text('<html>hidden</html>')
        with self.assertRaises(AssertionError): verify_files(path, self.root, start, 'a'*40, 'a'*40)

    def test_policy_is_exact_debug_task_set_not_release(self):
        source = (ROOT / 'app/build.gradle').read_text()
        # Source guard, not a claim that AGP has executed under these task sets.
        self.assertIn("gradle.startParameter.taskNames == [':app:assembleDebug', ':app:lintDebug']", source)
        for tasks in [[':app:assembleRelease', ':app:lintRelease'], [':app:lintDebug'],
                      [':app:assembleDebug', ':app:lintDebug', ':app:assembleRelease']]:
            self.assertNotEqual(tasks, [':app:assembleDebug', ':app:lintDebug'])
        for guard in ["minSdkVersion.apiLevel == 26", "targetSdkVersion.apiLevel == 36",
                      'android.compileSdk == 36', "ANDROID_GRADLE_PLUGIN_VERSION == '8.13.2'",
                      "gradle.gradleVersion == '8.14.6'", 'if (foundationLintTasks && foundationLintPins)',
                      "android.lint.informational.add('OldTargetApi')", 'abortOnError true',
                      'warningsAsErrors true', 'absolutePaths true', 'textReport true', 'xmlReport true', 'htmlReport true']:
            self.assertIn(guard, source)
        self.assertEqual(source.count('.informational.add('), 1)
        code = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('//'))
        for forbidden in ['disable ', 'ignoreWarnings', 'baseline ', "informational.add('AndroidGradlePluginVersion')"]:
            self.assertNotIn(forbidden, code)
        workflow = (ROOT / '.github/workflows/phase-foundation.yml').read_text()
        self.assertIn('python3 tools/run_compile_checks.py', workflow)
        self.assertNotIn('continue-on-error', workflow)
        aggregate = (ROOT / 'tools/run_compile_checks.py').read_text()
        self.assertIn('return 0 if all(code == 0 for code in results.values()) else 1', aggregate)
        self.assertIn("'tools/verify_lint.py'", aggregate)
        self.assertIn("'tools/verify_apk.py'", aggregate)
        self.assertIn('.unlink(missing_ok=True)', aggregate)


if __name__ == '__main__':
    unittest.main(verbosity=2)
