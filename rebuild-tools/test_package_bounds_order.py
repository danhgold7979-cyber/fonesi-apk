"""Source-order guard, not a reproduction of denied malformed-envelope execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PackageBoundsOrderTests(unittest.TestCase):
    def test_exact_three_checks_bound_before_unchanged_grammar(self):
        paths = [
            'rebuild-kernel/src/main/java/vn/fonesi/rebuild/OperationProtocol.java',
            'rebuild-kernel/src/main/java/vn/fonesi/rebuild/SharedUpdateDispatcher.java',
            'rebuild-logic/src/main/java/vn/fonesi/rebuild/logical/OperationProtocol.java',
        ]
        for relative in paths:
            current = (ROOT / relative).read_text()
            historical = (ROOT / 'spec/package-bounds/d5/historical-source' / relative).read_text()
            old = next(line for line in historical.splitlines() if 'packageName == null' in line)
            new = next(line for line in current.splitlines() if 'packageName == null' in line)
            expected = old.replace('packageName == null || ', 'packageName == null || packageName.length() > 255 || ').replace(' || packageName.length() > 255)', ')')
            self.assertEqual(new, expected)
            self.assertEqual(current.replace(new, old), historical)


if __name__ == '__main__':
    unittest.main()
