"""Injected command results only; no SDK, Gradle or APK execution."""
import contextlib
import io
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile
import verify_apk
from run_compile_checks import run_checks, APK, REPORTS


class CompileAggregateTest(unittest.TestCase):
    def scenario(self, codes, apk=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            # Prove stale success receipts and APK are removed before build.
            for name in [APK, 'baseline-receipt.json', 'compile-receipt.json']:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('stale')
            calls = []
            def runner(command, **kwargs):
                calls.append(command)
                if command[0] == './gradlew':
                    self.assertFalse((root / APK).exists())
                    self.assertFalse((root / 'baseline-receipt.json').exists())
                    if apk: (root / APK).write_bytes(b'synthetic-not-apk')
                    for name in REPORTS:
                        path = root / name
                        path.parent.mkdir(parents=True, exist_ok=True)
                        path.write_text('synthetic raw report')
                    return SimpleNamespace(returncode=codes[0])
                index = 1 if 'tools/verify_lint.py' in command else 2
                return SimpleNamespace(returncode=codes[index], stdout='synthetic receipt\n', stderr='diagnostic\n')
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                status = run_checks(root, 'a'*40, runner)
            self.assertFalse((root / 'baseline-receipt.json').exists())
            if codes[2] or not apk: self.assertFalse((root / 'compile-receipt.json').exists())
            return status, calls

    def test_all_success_required(self):
        status, calls = self.scenario([0, 0, 0])
        self.assertEqual(status, 0)
        self.assertEqual(len(calls), 3)

    def test_each_failure_remains_failure_and_all_diagnostics_run(self):
        for codes in [[1, 0, 0], [0, 1, 0], [0, 0, 1], [1, 1, 1]]:
            status, calls = self.scenario(codes)
            self.assertNotEqual(status, 0)
            self.assertEqual(len(calls), 3)

    def test_packaged_diagnostics_all_collected_before_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/'synthetic.apk'
            with zipfile.ZipFile(apk,'w') as archive: archive.writestr('placeholder','not an APK')
            output=io.StringIO()
            with patch('sys.argv',['verify_apk.py',str(apk)]), patch.dict('os.environ',{'ANDROID_HOME':directory}), \
                    patch('verify_apk.subprocess.run',return_value=SimpleNamespace(returncode=1,stdout='<manifest/>',stderr='synthetic command failure')) as run, \
                    contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit): verify_apk.main()
                self.assertEqual(run.call_count,4)
                self.assertEqual(output.getvalue(),'')

    def test_missing_apk_never_uses_stale_artifact(self):
        status, calls = self.scenario([0, 0, 0], apk=False)
        self.assertNotEqual(status, 0)
        self.assertEqual(len(calls), 2)


if __name__ == '__main__':
    unittest.main(verbosity=2)
