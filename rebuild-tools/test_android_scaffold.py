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
        self.assertEqual(len(buttons), 6)
        self.assertEqual({b.get(A + "id") for b in buttons},
                         {"@+id/" + x for x in ("import_apk", "create_clone", "launch_clone", "migrate_data", "metadata_first", "metadata_next")})
        for button in buttons:
            self.assertEqual(button.get(A + "enabled"), "false")
            self.assertIsNone(button.get(A + "onClick"))

    def test_activity_has_only_owned_catalog_and_no_operation_enable_path(self):
        sources = list((MAIN / "java").rglob("*.java"))
        self.assertEqual({p.name for p in sources}, {"ManagerActivity.java", "ManagerModel.java", "ManagerController.java", "CatalogReader.java", "CatalogSql.java", "CatalogPage.java", "CatalogLoader.java", "CatalogRequestGate.java", "CatalogOwnership.java"})
        source = (MAIN / "java/vn/fonesi/rebuild/ManagerActivity.java").read_text()
        allowed = {"android.app.Activity", "android.os.Bundle", "android.os.Looper", "android.text.Editable", "android.text.TextWatcher", "android.text.InputFilter",
                   "android.widget.CheckBox", "android.widget.EditText", "android.widget.LinearLayout", "android.widget.TextView",
                   "vn.fonesi.rebuild.model.ManagerModel", "vn.fonesi.rebuild.model.ManagerController",
                   "android.widget.Button", "vn.fonesi.rebuild.catalog.CatalogLoader", "vn.fonesi.rebuild.catalog.CatalogPage"}
        self.assertEqual({line[len("import "):-1] for line in source.splitlines() if line.startswith("import ")}, allowed)
        for forbidden in ("getFilesDir", "getDatabasePath", "Runtime", "Class.forName", "controller.publish"):
            self.assertNotIn(forbidden, source)
        for operation in ("import_apk", "create_clone", "launch_clone", "migrate_data"):
            self.assertNotIn("R.id." + operation, source)
        self.assertIn("controller.attach(this)", source)
        self.assertIn("loader.cancel(controller)", source)
        self.assertIn("loader.request(controller, ticket, position)", source)
        self.assertIn("loadPage(false, next.token)", source)
        self.assertIn("loadPage(true, next.token)", source)
        self.assertIn("controller.detach()", source)
        self.assertIn("controller.select(next.token, entry.key, checked)", source)
        self.assertIn("if (rendering || state == null)", source)

    def test_catalog_delivery_source_guards(self):
        base = MAIN / "java/vn/fonesi/rebuild"
        loader = (base / "catalog/CatalogLoader.java").read_text()
        activity = (base / "ManagerActivity.java").read_text()
        reader = (base / "catalog/CatalogReader.java").read_text()
        self.assertIn("TIMEOUT_MILLIS = 2_000", loader)
        self.assertIn("new ArrayBlockingQueue<Runnable>(1)", loader)
        self.assertNotIn("CallerRunsPolicy", loader)
        self.assertIn("state.phase == ManagerModel.Phase.LOADING", activity)
        self.assertIn("controller.beginRefresh(token)", activity)
        self.assertEqual(activity.count("new InputFilter.LengthFilter(4096)"), 2)
        self.assertIn("2 * entry.normalizedUnits()", reader)
        self.assertNotIn("getString(", reader)
        self.assertNotIn("deleteDatabase", reader)
        self.assertNotIn("CREATE_IF_NECESSARY", reader)
        self.assertIn("throw new SQLiteDatabaseCorruptException", reader)

    def test_catalog_directory_policy_adapter_keeps_path_and_file_guards(self):
        source = (MAIN / "java/vn/fonesi/rebuild/catalog/CatalogReader.java").read_text()
        policy = (MAIN / "java/vn/fonesi/rebuild/catalog/CatalogOwnership.java").read_text()
        self.assertIn("Os.lstat(file.getAbsolutePath())", source)
        self.assertIn("directory ? OsConstants.S_ISDIR(s.st_mode) : OsConstants.S_ISREG(s.st_mode)", source)
        self.assertIn("CatalogOwnership.permits(directory, s.st_mode, s.st_uid, s.st_gid,", source)
        self.assertIn("Process.myUid(), Os.geteuid(), Os.getgid(), Os.getegid()", source)
        self.assertIn("actual.st_dev == expected.st_dev && actual.st_ino == expected.st_ino", source)
        self.assertIn("getCanonicalFile()", source)
        self.assertIn("file.createNewFile()", source)
        self.assertIn("noSidecars(file)", source)
        for forbidden in ("Os.chmod", "Os.chown", "Os.umask", "Os.open", "O_CLOEXEC", "setWritable", "setReadable"):
            self.assertNotIn(forbidden, source + policy)
        self.assertIn("(mode & 0002) != 0", policy)
        self.assertIn("(mode & 0020) == 0", policy)
        self.assertIn("return directory && ownerGid == processUid", policy)

    def test_metadata_view_state_is_not_saved(self):
        root = ET.parse(MAIN / "res/layout/manager.xml").getroot()
        names = {"metadata_search", "metadata_group", "metadata_group_filter", "metadata_select_visible"}
        found = [node for node in root.iter() if node.get(A + "id") in {"@+id/" + name for name in names}]
        self.assertEqual(len(found), 4)
        self.assertTrue(all(node.get(A + "saveEnabled") == "false" for node in found))
        source = (MAIN / "java/vn/fonesi/rebuild/ManagerActivity.java").read_text()
        self.assertIn("row.setSaveEnabled(false)", source)
        self.assertIn("Looper.myLooper() != Looper.getMainLooper()", source)

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

    def test_metadata_count_uses_quantity_resource(self):
        values = ET.parse(MAIN / "res/values/strings.xml").getroot()
        plural = values.find("plurals[@name='metadata_unconnected']")
        self.assertEqual({item.get("quantity") for item in plural}, {"one", "other"})
        self.assertTrue(all("%1$d" in item.text for item in plural))
        self.assertIn("loaded entry.", plural.find("item[@quantity='one']").text)
        self.assertIn("loaded entries.", plural.find("item[@quantity='other']").text)
        source = (MAIN / "java/vn/fonesi/rebuild/ManagerActivity.java").read_text()
        self.assertIn("getQuantityString", source)
        self.assertIn("R.plurals.metadata_unconnected, next.totalCount, next.totalCount", source)

    def test_all_resource_xml_parses_and_status_is_honest(self):
        for file in (MAIN / "res").rglob("*.xml"):
            ET.parse(file)
        values = ET.parse(MAIN / "res/values/strings.xml").getroot()
        text = values.find("string[@name='status']").text
        self.assertIn("No runtime provider", text)
        self.assertIn("does not prove guest isolation", text)


if __name__ == "__main__":
    unittest.main()
