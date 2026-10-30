"""Run the synthetic storage suite; zero/skipped tests cannot count as PASS."""
import sys
import unittest
from pathlib import Path

root = Path(__file__).parent
suite = unittest.defaultTestLoader.discover(str(root), pattern="test_*.py")
count = suite.countTestCases()
if count < 30:
    raise SystemExit(f"Incomplete storage suite: {count} tests")
result = unittest.TextTestRunner(verbosity=2).run(suite)
if not result.wasSuccessful() or result.skipped:
    raise SystemExit(1)
print(f"PASS {count} synthetic SQLite tests; no Android migration/provider execution")
sys.exit(0)
