from pathlib import Path
import unittest

from verify_root_refusal import MARKER, MAX_LOG_BYTES, verify

ROOT = Path(__file__).resolve().parents[1]
VALID = ("* Where:\nSettings file '/synthetic/settings.gradle.kts' line: 3\n"
         '* What went wrong:\n' + MARKER + '\nBUILD FAILED in 1s\n').encode()


class RootRetirementTests(unittest.TestCase):
    def test_exact_source_guard(self):
        self.assertFalse((ROOT / 'build.gradle.kts').exists())
        self.assertFalse((ROOT / 'gradle.properties').exists())
        source = (ROOT / 'settings.gradle.kts').read_text()
        code = '\n'.join(line for line in source.splitlines() if not line.startswith('//')).strip()
        self.assertEqual(code, 'throw GradleException("' + MARKER + '")')
        for path in ('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar',
                     'gradle/wrapper/gradle-wrapper.properties'):
            self.assertTrue((ROOT / path).is_file())

    def test_hosted_refusal_after_fresh_build_and_offline(self):
        workflow = (ROOT / '.github/workflows/rebuild-kernel.yml').read_text()
        self.assertLess(workflow.index('Compile fresh isolated debug manager'), workflow.index('Require intentional offline legacy-root refusal'))
        self.assertIn('./gradlew --offline --no-daemon --console=plain', workflow)
        self.assertIn('verify_root_refusal.py "$status" "$log"', workflow)
        self.assertIn('./gradlew -p rebuild-android', workflow)

    def test_valid_refusal(self):
        verify(1, VALID)

    def test_success_is_not_refusal(self):
        with self.assertRaises(ValueError): verify(0, VALID)

    def test_wrong_status(self):
        for status in (True, -1, 256):
            with self.subTest(status=status), self.assertRaises(ValueError): verify(status, VALID)

    def test_generic_failure_not_accepted(self):
        for log in (b'BUILD FAILED\n', VALID.replace(MARKER.encode(), b'Network unavailable'),
                    VALID.replace(b'settings.gradle.kts', b'build.gradle.kts'),
                    VALID.replace(b'* What went wrong:', b'not a diagnostic'),
                    VALID.replace(b'BUILD FAILED', b'BUILD SUCCESSFUL')):
            with self.subTest(log=log), self.assertRaises(ValueError): verify(1, log)

    def test_marker_in_source_or_stack_is_not_exact_diagnostic(self):
        with self.assertRaises(ValueError): verify(1, VALID.replace(MARKER.encode(), ('throw("' + MARKER + '")').encode()))

    def test_tasks_and_download_are_rejected(self):
        for extra in (b'> Task :app:assembleDebug\n', b'Downloading https://example.invalid/gradle.zip\n'):
            with self.subTest(extra=extra), self.assertRaises(ValueError): verify(1, VALID + extra)

    def test_bounded_log(self):
        with self.assertRaises(ValueError): verify(1, b'x' * (MAX_LOG_BYTES + 1))

    def test_invalid_utf8(self):
        with self.assertRaises(UnicodeError): verify(1, VALID + b'\xff')


if __name__ == '__main__':
    unittest.main()
