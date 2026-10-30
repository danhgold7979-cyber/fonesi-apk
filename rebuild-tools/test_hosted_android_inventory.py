import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from hosted_android_inventory import inspect, WRAPPER_SHA, SCRIPT_SHA, ZIP_SHA


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="fonesi-inventory-fixture-"); self.root = Path(self.temp.name)
        self.sdk = self.root / "sdk"; self.jdk = self.root / "jdk"
        for relative in ("platforms/android-36/android.jar", "build-tools/36.0.0/aapt2", "build-tools/36.0.0/apksigner", "build-tools/36.0.0/zipalign"):
            file = self.sdk / relative; file.parent.mkdir(parents=True, exist_ok=True); file.write_text("fixture"); file.chmod(0o700)
        (self.sdk / "platforms/android-36/source.properties").write_text("AndroidVersion.ApiLevel=36\nPkg.Revision=2\n")
        (self.sdk / "build-tools/36.0.0/source.properties").write_text("Pkg.Revision=36.0.0\n")
        (self.jdk / "bin").mkdir(parents=True); (self.jdk / "bin/java").write_text(""); (self.jdk / "bin/javac").write_text("")
        for name in ("java", "javac"): (self.jdk / "bin" / name).chmod(0o700)
        (self.jdk / "release").write_text('JAVA_VERSION="17.0.21"\nIMPLEMENTOR="fixture"\n')
        wrapper = self.root / "gradle/wrapper"; wrapper.mkdir(parents=True)
        (wrapper / "gradle-wrapper.properties").write_text(f"distributionSha256Sum={ZIP_SHA}\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\n")
        self.env = {"ANDROID_HOME": str(self.sdk), "ANDROID_SDK_ROOT": str(self.sdk), "JAVA_HOME_17_X64": str(self.jdk)}
    def tearDown(self): self.temp.cleanup()
    def output(self):
        with patch("hosted_android_inventory.sha", side_effect=lambda p: WRAPPER_SHA if p.name.endswith(".jar") else SCRIPT_SHA):
            return inspect(self.root, self.env)
    def test_complete_fixture_ready_for_review_not_compile(self):
        result = self.output(); self.assertEqual("READY_FOR_BOUNDED_COMPILE_REVIEW", result["status"])
        self.assertFalse(result["sdk_install_attempted"]); self.assertFalse(result["sdk_license_action"]); self.assertFalse(result["gradle_executed"])
    def test_missing_sdk_stays_not_ready(self):
        self.env = {}; self.assertEqual("NOT_READY", self.output()["status"])
    def test_missing_tool_not_installed_by_inspection(self):
        file = self.sdk / "build-tools/36.0.0/apksigner"; file.unlink()
        self.assertEqual("NOT_READY", self.output()["status"]); self.assertFalse(file.exists())
    def test_nonexecutable_tool_rejected(self):
        (self.sdk / "build-tools/36.0.0/aapt2").chmod(0o600)
        self.assertEqual("NOT_READY", self.output()["status"])
    def test_wrong_jdk_rejected(self):
        (self.jdk / "release").write_text('JAVA_VERSION="21.0.13"\n'); self.assertEqual("NOT_READY", self.output()["status"])
    def test_mismatched_sdk_roots_rejected(self):
        self.env["ANDROID_SDK_ROOT"] = str(self.root / "other"); self.assertFalse(self.output()["checks"]["sdk_roots_consistent"])
    def test_wrong_wrapper_refused(self):
        with patch("hosted_android_inventory.sha", return_value="unverified"):
            self.assertFalse(inspect(self.root, self.env)["checks"]["wrapper_verified"])


if __name__ == "__main__": unittest.main(verbosity=2)
