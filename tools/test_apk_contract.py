"""Synthetic negative tests for manifest predicate; not APK/package proof."""
import pathlib,unittest,xml.etree.ElementTree as ET
from verify_apk import validate_manifest
A='{http://schemas.android.com/apk/res/android}'
class ManifestPredicateTest(unittest.TestCase):
    def setUp(self):
        self.root=ET.parse(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/AndroidManifest.xml').getroot()
        self.root.set('package','vn.fonesi.app')
        self.root.find('application').set(A+'debuggable','true')
        ET.SubElement(self.root,'uses-sdk',{A+'minSdkVersion':'26',A+'targetSdkVersion':'36'})
    def validate(self):validate_manifest(ET.tostring(self.root,encoding='unicode'))
    def test_positive(self):self.validate()
    def test_permission_rejected(self):
        ET.SubElement(self.root,'uses-permission',{A+'name':'android.permission.INTERNET'})
        with self.assertRaises(AssertionError):self.validate()
    def test_backup_rejected(self):
        self.root.find('application').set(A+'allowBackup','true')
        with self.assertRaises(AssertionError):self.validate()
    def test_nondebuggable_rejected(self):
        self.root.find('application').set(A+'debuggable','false')
        with self.assertRaises(AssertionError):self.validate()
    def test_missing_debuggable_rejected(self):
        del self.root.find('application').attrib[A+'debuggable']
        with self.assertRaises(AssertionError):self.validate()
    def test_wrong_activity_identity_rejected(self):
        self.root.find('application/activity').set(A+'name','.OtherActivity')
        with self.assertRaises(AssertionError):self.validate()
    def test_full_activity_identity_accepted(self):
        self.root.find('application/activity').set(A+'name','vn.fonesi.app.MainActivity')
        self.validate()
    def test_provider_rejected(self):
        ET.SubElement(self.root.find('application'),'provider',{A+'name':'.P'})
        with self.assertRaises(AssertionError):self.validate()
    def test_sdk_downgrade_rejected(self):
        self.root.find('uses-sdk').set(A+'targetSdkVersion','28')
        with self.assertRaises(AssertionError):self.validate()
    def test_app_identity_rejected(self):
        self.root.set('package','other.package')
        with self.assertRaises(AssertionError):self.validate()
    def test_deep_link_action_rejected(self):
        ET.SubElement(self.root.find('application/activity/intent-filter'),'action',{A+'name':'android.intent.action.VIEW'})
        with self.assertRaises(AssertionError):self.validate()
if __name__=='__main__':unittest.main(verbosity=2)
