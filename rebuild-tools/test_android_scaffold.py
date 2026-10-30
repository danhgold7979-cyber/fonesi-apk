"""Source contracts, not Android execution tests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "rebuild-android"
MAIN = ROOT / "app/src/main"
A = "{http://schemas.android.com/apk/res/android}"


class ScaffoldTests(unittest.TestCase):
    def test_no_permissions_or_background_components(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        self.assertEqual([node.tag for node in manifest], ["application"])
        app = manifest.find("application")
        self.assertEqual(app.get(A + "allowBackup"), "false")
        self.assertEqual([node.tag for node in app], ["activity"])
        self.assertEqual(app.find("activity").get(A + "name"), ".ManagerActivity")

    def test_all_backup_domains_explicitly_excluded(self):
        app = ET.parse(MAIN / "AndroidManifest.xml").getroot().find("application")
        self.assertEqual(app.get(A + "fullBackupContent"), "@xml/backup_exclusions")
        self.assertEqual(app.get(A + "dataExtractionRules"), "@xml/data_extraction_rules")
        expected = {"root", "file", "database", "sharedpref", "external", "device_root",
                    "device_file", "device_database", "device_sharedpref"}
        legacy = ET.parse(MAIN / "res/xml/backup_exclusions.xml").getroot()
        modern = ET.parse(MAIN / "res/xml/data_extraction_rules.xml").getroot()
        self.assertEqual([x.tag for x in modern], ["cloud-backup", "device-transfer"])
        for section in [legacy, *modern]:
            self.assertEqual(len(section), 9)
            self.assertEqual({x.get("domain") for x in section}, expected)
            self.assertTrue(all(x.tag == "exclude" and x.get("path") == "." for x in section))

    def test_all_four_actions_disabled_without_handlers(self):
        root = ET.parse(MAIN / "res/layout/manager.xml").getroot()
        buttons = list(root.iter("Button"))
        self.assertEqual(len(buttons), 4)
        self.assertEqual({b.get(A + "id") for b in buttons},
                         {"@+id/" + x for x in ("import_apk", "create_clone", "launch_clone", "migrate_data")})
        for button in buttons:
            self.assertEqual(button.get(A + "enabled"), "false")
            self.assertIsNone(button.get(A + "onClick"))

    def test_activity_has_no_adapter_or_enable_path(self):
        sources = list((MAIN / "java").rglob("*.java"))
        self.assertEqual({p.name for p in sources}, {"ManagerActivity.java", "ManagerModel.java"})
        source = (MAIN / "java/vn/fonesi/rebuild/ManagerActivity.java").read_text()
        self.assertEqual([line.strip() for line in source.splitlines() if line.startswith("import ")],
                         ["import android.app.Activity;", "import android.os.Bundle;", "import android.widget.TextView;",
                          "import vn.fonesi.rebuild.model.ManagerModel;"])
        for forbidden in ("setEnabled", "setOnClickListener", "getFilesDir", "getDatabasePath", "Runtime", "Class.forName"):
            self.assertNotIn(forbidden, source)

    def test_isolated_build_and_pinned_versions(self):
        build = (ROOT / "app/build.gradle.kts").read_text()
        for expected in ('applicationId = "vn.fonesi.rebuild.debug"', 'compileSdk = 36',
                         'minSdk = 26', 'targetSdk = 36', 'buildToolsVersion = "36.0.0"', 'VERSION_17'):
            self.assertIn(expected, build)
        self.assertNotIn("dependencies", build)
        self.assertIn('version "8.11.1"', (ROOT / "build.gradle.kts").read_text())
        settings = (ROOT / "settings.gradle.kts").read_text()
        self.assertNotIn("includeBuild", settings)
        self.assertNotIn("rebuild-kernel", settings)

    def test_no_sdk_or_java_toolchain_download(self):
        props = (ROOT / "gradle.properties").read_text()
        self.assertIn("android.builder.sdkDownload=false", props)
        self.assertIn("org.gradle.java.installations.auto-download=false", props)

    def test_all_resource_xml_parses_and_status_is_honest(self):
        for file in (MAIN / "res").rglob("*.xml"):
            ET.parse(file)
        values = ET.parse(MAIN / "res/values/strings.xml").getroot()
        text = values.find("string[@name='status']").text
        self.assertIn("No runtime provider", text)
        self.assertIn("does not prove guest isolation", text)


if __name__ == "__main__":
    unittest.main()
