"""Synthetic negative tests for manifest predicate; not APK/package proof."""
import pathlib,unittest,copy,xml.etree.ElementTree as ET
from verify_apk import validate_manifest,validate_packaged_rules,RULE_PATH
A='{http://schemas.android.com/apk/res/android}'
class ManifestPredicateTest(unittest.TestCase):
    def setUp(self):
        self.root=ET.parse(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/AndroidManifest.xml').getroot()
        self.root.set('package','vn.fonesi.app')
        self.root.set(A+'versionCode','1');self.root.set(A+'versionName','0.1.0-foundation')
        self.root.find('application').set(A+'debuggable','true')
        ET.SubElement(self.root,'uses-sdk',{A+'minSdkVersion':'26',A+'targetSdkVersion':'36'})
    def validate(self):validate_manifest(ET.tostring(self.root,encoding='unicode'))
    def test_positive(self):self.validate()
    def test_wrong_or_missing_version_rejected(self):
        for field,value in [('versionCode','2'),('versionName','other')]:
            root=copy.deepcopy(self.root);root.set(A+field,value)
            with self.assertRaises(AssertionError):validate_manifest(ET.tostring(root,encoding='unicode'))
            del root.attrib[A+field]
            with self.assertRaises(AssertionError):validate_manifest(ET.tostring(root,encoding='unicode'))
    def test_permission_rejected(self):
        ET.SubElement(self.root,'uses-permission',{A+'name':'android.permission.INTERNET'})
        with self.assertRaises(AssertionError):self.validate()
    def test_backup_rejected(self):
        self.root.find('application').set(A+'allowBackup','true')
        with self.assertRaises(AssertionError):self.validate()
    def test_missing_backup_fields_rejected(self):
        for field in ['fullBackupContent', 'dataExtractionRules']:
            root=copy.deepcopy(self.root)
            del root.find('application').attrib[A+field]
            with self.assertRaises(AssertionError):validate_manifest(ET.tostring(root,encoding='unicode'))
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
class PackagedRulesPredicateTest(unittest.TestCase):
    def setUp(self):
        source=pathlib.Path(__file__).resolve().parents[1]/'app/src/main'
        self.manifest=ET.parse(source/'AndroidManifest.xml').getroot()
        self.rules=ET.parse(source/RULE_PATH).getroot()
        self.dump='Package name=vn.fonesi.app id=7f\n  type xml id=0a entryCount=1\n    resource 0x7f0a0000 xml/data_extraction_rules\n      () (file) '+RULE_PATH+' type=XML\n'
        self.members=[RULE_PATH]
    def validate(self):
        validate_packaged_rules(ET.tostring(self.manifest,encoding='unicode'),self.dump,
                                ET.tostring(self.rules,encoding='unicode'),self.members)
    def test_named_and_numeric_references(self):
        self.validate()
        for ref in ['@0x7f0a0000','@ref/0x7f0a0000']:
            self.manifest.find('application').set(A+'dataExtractionRules',ref)
            self.validate()
    def test_unrelated_manifest_reference(self):
        self.manifest.find('application').set(A+'dataExtractionRules','@ref/0x7f0a0001')
        with self.assertRaises(AssertionError):self.validate()
    def test_foreign_resource_package_rejected(self):
        self.dump=self.dump.replace('0x7f0a0000','0x010a0000')
        self.manifest.find('application').set(A+'dataExtractionRules','@ref/0x010a0000')
        with self.assertRaises(AssertionError):self.validate()
    def test_missing_domain_in_each_mode(self):
        original=copy.deepcopy(self.rules)
        for mode in range(2):
            for domain in range(9):
                self.rules=copy.deepcopy(original)
                self.rules[mode].remove(self.rules[mode][domain])
                with self.assertRaises(AssertionError):self.validate()
    def test_include_or_narrow_path(self):
        self.rules[0][0].tag='include'
        with self.assertRaises(AssertionError):self.validate()
        self.rules[0][0].tag='exclude';self.rules[0][0].set('path','narrow')
        with self.assertRaises(AssertionError):self.validate()
    def test_unbound_or_variant_resource(self):
        original=self.dump
        for dump in ['',original.replace(RULE_PATH,'res/xml/other.xml'),original+'      (v31) (file) res/xml-v31/data_extraction_rules.xml type=XML\n',original+original]:
            self.dump=dump
            with self.assertRaises(AssertionError):self.validate()
    def test_missing_duplicate_or_qualified_member(self):
        for members in [[],[RULE_PATH,RULE_PATH],[RULE_PATH,'res/xml-v31/data_extraction_rules.xml']]:
            self.members=members
            with self.assertRaises(AssertionError):self.validate()

if __name__=='__main__':unittest.main(verbosity=2)
