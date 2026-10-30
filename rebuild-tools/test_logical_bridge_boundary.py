"""Focused source boundary tripwires; not a full API analyzer or device execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'rebuild-logic/src/main/java'


class LogicalBridgeBoundaryTests(unittest.TestCase):
    def test_reviewed_source_surface_and_api_helpers(self):
        files = list(SOURCE.rglob('*.java'))
        self.assertEqual({p.name for p in files}, {'CapabilityPolicy.java', 'OperationProtocol.java',
                                                 'IdentityText.java', 'ImmutableCollections.java'})
        for path in files:
            text = path.read_text()
            self.assertIn('package vn.fonesi.rebuild.logical;', text)
            for symbol in ('java.io.', 'java.nio.', 'java.net.', 'android.', 'java.lang.reflect',
                           'HexFormat', '.isBlank(', '.strip(', '.toList(', '.writeBytes(',
                           'java.util.stream', 'Thread.', 'Runtime.', 'ProcessBuilder'):
                self.assertNotIn(symbol, text, str(path))
            self.assertIsNone(re.search(r'\b(?:List|Set|Map)\s*\.\s*(?:of|copyOf)\s*\(', text))
            self.assertIsNone(re.search(r'\brecord\s+\w+\s*\(', text))
            self.assertNotIn('->', text)

    def test_no_app_import_or_source_set_wiring(self):
        for path in (ROOT / 'rebuild-android').rglob('*'):
            if path.is_file() and path.suffix in ('.java', '.kts'):
                self.assertNotIn('rebuild-logic', path.read_text())
                self.assertNotIn('vn.fonesi.rebuild.logical', path.read_text())

    def test_ci_requires_installed_jdk17_and_compares_not_updates(self):
        script = (ROOT / 'rebuild-logic/check.sh').read_text()
        self.assertIn('--release 17 -Xlint:all -Werror', script)
        self.assertIn('REQUIRE_JDK17', script)
        self.assertIn('cmp "$OUT/frozen.txt" "$OUT/host.txt"', script)
        self.assertIn('decode_logical_fixture.py" b2 > "$OUT/frozen.txt"', script)
        self.assertIn('cmp "$OUT/host.txt" "$OUT/portable.txt"', script)
        self.assertNotRegex(script, r'>\s*["\']?[^\n]*spec/logical-bridge')
        self.assertIn('REQUIRE_JDK17: "1"', (ROOT / '.github/workflows/rebuild-kernel.yml').read_text())


if __name__ == '__main__':
    unittest.main()
