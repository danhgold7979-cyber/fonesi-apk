"""Negative tests of APK evidence gates using synthetic tool output, not APK build."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import verify_debug_apk as subject

BADGING = """package: name='vn.fonesi.rebuild.debug'
minSdkVersion:'26'
targetSdkVersion:'36'
application-debuggable
launchable-activity: name='vn.fonesi.rebuild.ManagerActivity'
"""


LAYOUT = "".join(f"E: Button (line=1)\n A: http://schemas.android.com/apk/res/android:id(0x010100d0)=@0x7f08000{i}\n A: android:enabled(0x0101000e)=false\n" for i in range(4))


class ApkGateTests(unittest.TestCase):
    def exercise(self, badging=BADGING, manifest='allowBackup(0x01010280)=false\n E: activity (line=1)',
                 layout=LAYOUT, signer='CN=Android Debug', extra=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'rebuild-android/app/build/outputs/apk/debug/app-debug.apk'
            apk.parent.mkdir(parents=True)
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('classes.dex', b'synthetic')
                archive.writestr('AndroidManifest.xml', b'synthetic')
                if extra:
                    archive.writestr(extra, b'synthetic')
            outputs = [badging, manifest, "", layout, signer]
            with patch.object(subject.subprocess, 'run', side_effect=[subprocess.CompletedProcess([], 0, x) for x in outputs]), patch('builtins.print'):
                subject.verify(root, root / 'sdk')

    def test_synthetic_expected_evidence(self):
        self.exercise()

    def test_captured_sdk36_evidence(self):
        fixture = Path(__file__).parent / 'fixtures/sdk36'
        self.exercise(**{key: (fixture / (name + '.txt')).read_text()
                         for key, name in [('badging', 'badging'), ('manifest', 'manifest'),
                                           ('layout', 'layout'), ('signer', 'signature')]})

    def test_wrong_minimum_sdk_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(badging=BADGING.replace("minSdkVersion:'26'", "minSdkVersion:'25'"))

    def test_wrong_target_sdk_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(badging=BADGING.replace("targetSdkVersion:'36'", "targetSdkVersion:'35'"))

    def test_wrong_identity_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(badging=BADGING.replace('vn.fonesi.rebuild.debug', 'vn.fonesi.old'))

    def test_permissions_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(badging=BADGING + "uses-permission: name='android.permission.INTERNET'")

    def test_extra_component_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(manifest='allowBackup(0x01010280)=false\n E: activity (line=1)\n E: service (line=2)')

    def test_backup_enabled_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(manifest='allowBackup(0x01010280)=true')

    def test_enabled_action_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(layout=LAYOUT.replace('=false', '=true', 1))

    def test_duplicate_button_id_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(layout=LAYOUT.replace('0x7f080001', '0x7f080000'))

    def test_unrecognized_format_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(layout=LAYOUT.replace('=false', '=(unknown)0'))

    def test_release_signer_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(signer='CN=Release')

    def test_native_payload_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(extra='lib/arm64-v8a/engine.so')

    def test_no_debug_flag_rejected(self):
        with self.assertRaises(RuntimeError):
            self.exercise(badging=BADGING.replace('application-debuggable', ''))


if __name__ == '__main__':
    unittest.main()
