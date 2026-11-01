"""Owned receipt/routing fixtures, not real JDK17 or APK evidence."""
from pathlib import Path
import unittest
from compile_scope import requires_compile, HOST_ONLY
from run_host_checks import COMMANDS, check_kind, parse_count

STYLE = ['bash', 'tools/test_style_core.sh']
LINE = 'PASS StyleValue owned HOST assertions=128; no Android/rendering/persistence/provider acceptance\n'


class HostIntegrationTest(unittest.TestCase):
    def test_style_is_required_once_without_limited_mode(self):
        self.assertEqual(COMMANDS.count(STYLE), 1)
        self.assertEqual(check_kind(STYLE), 'synthetic_assertions')
        self.assertEqual(parse_count(STYLE, LINE), {'kind': 'synthetic_assertions', 'count': 128})

    def test_rejects_missing_zero_duplicate_limited_or_wrong_tier(self):
        for text in ['', 'PASS', LINE.replace('128', '0'), LINE + LINE,
                     'LIMITED HOST: JDK21\n' + LINE,
                     LINE.replace('no Android/rendering/persistence/provider acceptance', 'Android PASS')]:
            with self.subTest(text=text), self.assertRaises(AssertionError):
                parse_count(STYLE, text)

    def test_style_and_documentary_paths_do_not_recompile_apk(self):
        self.assertFalse(requires_compile(sorted(HOST_ONLY) + ['docs/status.json'], 'push'))
        self.assertFalse(requires_compile([], 'pull_request'))

    def test_manual_and_unknown_changes_require_compile(self):
        self.assertTrue(requires_compile([], 'workflow_dispatch'))
        for path in ['app/src/main/AndroidManifest.xml', 'app/build.gradle', 'settings.gradle',
                     'gradle.properties', 'gradlew', 'gradle/wrapper/gradle-wrapper.jar',
                     'tools/verify_apk.py', 'tools/verify_lint.py', 'tools/run_compile_checks.py',
                     'tools/check_toolchain.py', 'new-unknown-input',
                     'core/src/main/java/vn/fonesi/apk/foundation/UpdatePreflight.java']:
            with self.subTest(path=path):
                self.assertTrue(requires_compile([path], 'push'))
                self.assertTrue(requires_compile(['docs/test.md', path], 'pull_request'))

    def test_workflow_routes_compile_after_host_without_new_apk_for_host_only(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/phase-foundation.yml').read_text()
        self.assertIn('fetch-depth: 0', workflow)
        self.assertIn('run: python3 tools/compile_scope.py', workflow)
        self.assertIn("if: needs.documentary-host.outputs.compile_required == 'true'", workflow)
        self.assertIn('compile-scope.json', workflow)
        self.assertIn('EXPECTED_SOURCE_SHA', workflow)
        self.assertNotIn('continue-on-error', workflow)


if __name__ == '__main__':
    unittest.main(verbosity=2)
