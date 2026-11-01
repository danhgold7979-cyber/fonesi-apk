"""HOST static source contract only; does not execute Android or prove accessibility."""
import copy
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main'
A = '{http://schemas.android.com/apk/res/android}'
REASON = 'Chưa có bộ máy clone được kiểm chứng'

def validate_manifest(root):
    assert root.tag == 'manifest'
    assert [child.tag for child in root] == ['application']
    app = root.find('application')
    assert app.get(A + 'allowBackup') == 'false'
    assert [child.tag for child in app] == ['activity']
    activity = app.find('activity')
    assert activity.get(A + 'name') == '.MainActivity'
    assert activity.get(A + 'exported') == 'true'
    assert [child.tag for child in activity] == ['intent-filter']
    intent = activity.find('intent-filter')
    assert [(child.tag, child.get(A+'name')) for child in intent] == [
        ('action', 'android.intent.action.MAIN'),
        ('category', 'android.intent.category.LAUNCHER')]

def validate_layout(root, strings):
    assert root.tag == 'ScrollView'
    assert all(node.tag in ('ScrollView', 'LinearLayout', 'TextView', 'Button')
               for node in root.iter())
    buttons = list(root.iter('Button'))
    assert len(buttons) == 4
    assert {b.get(A+'id') for b in buttons} == {
        '@+id/action_create', '@+id/action_open', '@+id/action_stop', '@+id/action_delete'}
    for button in buttons:
        assert button.get(A+'enabled') == 'false'
        description = button.get(A+'contentDescription', '')
        assert description.startswith('@string/')
        assert REASON in strings[description.removeprefix('@string/')]
    assert any(node.get(A+'text') == '@string/provider_unavailable' for node in root.iter())
    for node in root.iter():
        assert A+'onClick' not in node.attrib
        assert A+'autoLink' not in node.attrib
        assert A+'inputType' not in node.attrib
    assert strings['provider_unavailable'] == REASON
    assert 'Chưa thể tạo hoặc chạy bản clone' in strings['build_limit']
    assert 'Không có dữ liệu mẫu' in strings['empty_description']
    assert strings['empty_title']=='Chưa đọc danh sách bản clone'
    assert 'chưa đọc dữ liệu' in strings['empty_description']

class ShellSourceContractTest(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
        self.layout = ET.parse(ROOT / 'res/layout/activity_main.xml').getroot()
        self.strings = {e.get('name'): e.text for e in ET.parse(ROOT / 'res/values/strings.xml').getroot()}

    def test_manifest_minimal_no_permissions(self):
        validate_manifest(self.manifest)

    def test_layout_truthful_disabled_empty_state(self):
        validate_layout(self.layout, self.strings)

    def test_unread_catalog_cannot_claim_zero_instances(self):
        self.strings['empty_title']='Chưa có bản clone'
        with self.assertRaises(AssertionError): validate_layout(self.layout, self.strings)

    def test_permission_injection_rejected(self):
        ET.SubElement(self.manifest, 'uses-permission', {A+'name': 'android.permission.INTERNET'})
        with self.assertRaises(AssertionError): validate_manifest(self.manifest)

    def test_extra_component_rejected(self):
        ET.SubElement(self.manifest.find('application'), 'service')
        with self.assertRaises(AssertionError): validate_manifest(self.manifest)

    def test_enabled_guest_control_rejected(self):
        for index in range(4):
            layout = copy.deepcopy(self.layout)
            list(layout.iter('Button'))[index].set(A+'enabled', 'true')
            with self.assertRaises(AssertionError): validate_layout(layout, self.strings)

    def test_missing_accessible_reason_rejected(self):
        list(self.layout.iter('Button'))[0].attrib.pop(A+'contentDescription')
        with self.assertRaises(AssertionError): validate_layout(self.layout, self.strings)

    def test_credential_widget_rejected(self):
        ET.SubElement(self.layout.find('LinearLayout'), 'EditText')
        with self.assertRaises(AssertionError): validate_layout(self.layout, self.strings)

    def test_source_has_no_dispatch_or_persistent_state_path(self):
        sources = list((ROOT / 'java').rglob('*.java'))
        self.assertEqual([p.name for p in sources], ['MainActivity.java'])
        source = sources[0].read_text()
        self.assertIn('package vn.fonesi.app;', source)
        self.assertIn('setContentView(R.layout.activity_main)', source)
        for forbidden in ['setOnClickListener', 'setEnabled(', 'startActivity(', 'WebView',
                          'java.net', 'http://', 'https://', 'SQLite', 'SharedPreferences',
                          'getPackageManager', 'startService', 'EditText', 'Runtime.getRuntime']:
            self.assertNotIn(forbidden, source)
        self.assertIn('spacing + safe.top', source)
        self.assertNotIn('getPaddingTop()', source)

    def test_brand_and_vector(self):
        styles = ET.parse(ROOT / 'res/values/styles.xml').getroot()
        colors = {e.get('name'): e.text for e in styles.findall('color')}
        self.assertEqual(colors['brand_green'], '#126B55')
        vector = ET.parse(ROOT / 'res/drawable/ic_fonesi.xml').getroot()
        self.assertEqual(vector.tag, 'vector')
        self.assertEqual(vector.find('path').get(A+'fillColor'), '#126B55')

if __name__ == '__main__':
    unittest.main(verbosity=2)
