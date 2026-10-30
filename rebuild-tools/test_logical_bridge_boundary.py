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
                                                 'IdentityText.java', 'ImmutableCollections.java',
                                                 'OperationCodec.java', 'ScopeKey.java'})
        for path in files:
            text = path.read_text()
            self.assertIn('package vn.fonesi.rebuild.logical;', text)
            allowed_imports = {
                'OperationCodec.java': {'java.io.ByteArrayInputStream', 'java.io.ByteArrayOutputStream',
                                        'java.io.DataInputStream', 'java.io.DataOutputStream', 'java.io.IOException'},
                'ScopeKey.java': {'java.nio.charset.StandardCharsets'},
            }.get(path.name, set())
            for imported in re.findall(r'^import (java\.(?:io|nio)\.[^;]+);$', text, re.M):
                self.assertIn(imported, allowed_imports, str(path))
            bounded_text = text
            for imported in allowed_imports:
                bounded_text = bounded_text.replace('import ' + imported + ';', '')
            self.assertNotIn('java.io.', bounded_text)
            self.assertNotIn('java.nio.', bounded_text)
            for symbol in ('java.net.', 'android.', 'java.lang.reflect',
                           'HexFormat', '.isBlank(', '.strip(', '.toList(', '.writeBytes(',
                           'java.util.stream', 'Thread.', 'Runtime.', 'ProcessBuilder'):
                self.assertNotIn(symbol, text, str(path))
            self.assertIsNone(re.search(r'\b(?:List|Set|Map)\s*\.\s*(?:of|copyOf)\s*\(', text))
            self.assertIsNone(re.search(r'\brecord\s+\w+\s*\(', text))
            self.assertNotIn('->', text)

    def test_codec_owned_bounds_and_compare_only_gate(self):
        codec = (SOURCE / 'vn/fonesi/rebuild/logical/OperationCodec.java').read_text()
        self.assertLess(codec.index('bytes.length < 36'), codec.index('byte[] owned = bytes.clone();'))
        body = codec[codec.index('byte[] owned = bytes.clone();') + len('byte[] owned = bytes.clone();'):]
        executable = re.sub(r'"(?:[^"\\]|\\.)*"|//[^\n]*', '', body.split('private static void writeUuid')[0])
        self.assertNotRegex(executable, r'\bbytes\b')
        self.assertIn('Arrays.equals(owned, encode(result))', codec)
        self.assertIn('result.identity().operation().equals(expectedOperation)', codec)
        self.assertNotIn('"Invalid journal value", e', codec)
        for source in ('OperationCodec.java', 'ScopeKey.java'):
            text = (SOURCE / 'vn/fonesi/rebuild/logical' / source).read_text()
            self.assertNotRegex(text, r'\b(?:File|Path|FileChannel|Socket|URL|ClassLoader|Thread)\b')
        script = (ROOT / 'rebuild-logic/check-codec.sh').read_text()
        self.assertIn('--release 17 -Xlint:all -Werror', script)
        self.assertIn('REQUIRE_JDK17', script)
        self.assertIn('cmp "$ROOT/spec/codec-bridge/b3c/desired.txt" "$OUT/host.txt"', script)
        self.assertIn('cmp "$ROOT/spec/codec-bridge/b3c/desired.txt" "$OUT/portable.txt"', script)
        self.assertIn('cmp "$OUT/host.txt" "$OUT/portable.txt"', script)
        self.assertNotRegex(script, r'>\s*["\']?[^\n]*spec/codec-bridge')
        self.assertIn('./check-codec.sh', (ROOT / 'rebuild-logic/check.sh').read_text())

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
