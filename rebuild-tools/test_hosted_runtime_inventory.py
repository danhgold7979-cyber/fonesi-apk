"""Synthetic file-only runtime inventory tests: never execute SDK tools or inspect AVDs."""
import hashlib
import os
import stat
from types import SimpleNamespace
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import hosted_android_inventory as inventory


class RuntimeInventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="fonesi-runtime-inventory-fixture-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sdk = self.root / "sdk"
        self.sdk.mkdir()
        self.kvm = self.root / "not-present-kvm"

    def output(self, machine="x86_64", sdk=True):
        # Calling any external tool or network API is a test failure.
        with patch("subprocess.run", side_effect=AssertionError("No subprocess")), \
             patch("subprocess.Popen", side_effect=AssertionError("No process")), \
             patch("os.system", side_effect=AssertionError("No shell")), \
             patch("socket.socket", side_effect=AssertionError("No network")):
            result = inventory.runtime_inventory(self.sdk if sdk else None, machine, self.kvm)
        self.assertEqual(result["purpose"], "READ_ONLY_RUNTIME_INVENTORY")
        self.assertIn(result["emulator"]["status"], ("PRESENT", "ABSENT", "UNKNOWN"))
        self.assertIn(result["images_status"], ("PRESENT", "ABSENT", "UNKNOWN"))
        return result

    def emulator(self):
        directory = self.sdk / "emulator"
        directory.mkdir(exist_ok=True)
        binary = directory / "emulator"
        binary.write_bytes(b"not an executable program: must only stat")
        binary.chmod(0o700)
        (directory / "source.properties").write_text("Pkg.Revision=36.1.9\nPkg.Desc=Android Emulator\n")
        return directory

    def image(self, api="26", abi="x86_64", variant="google_apis"):
        directory = self.sdk / "system-images" / ("android-" + api) / variant / abi
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "source.properties").write_text(
            f"AndroidVersion.ApiLevel={api}\nSystemImage.Abi={abi}\nSystemImage.TagId={variant}\nPkg.Revision=1\n")
        for name in ("system.img", "ramdisk.img", "userdata.img", "kernel-ranchu"):
            (directory / name).write_bytes(b"synthetic file, stat only")
        return directory

    def test_unconfigured_sdk_unknown_not_absent(self):
        result = self.output(sdk=False)
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")
        self.assertEqual(result["images_status"], "UNKNOWN")

    def test_empty_known_sdk_proves_absent(self):
        result = self.output()
        self.assertEqual(result["emulator"]["status"], "ABSENT")
        self.assertEqual(result["images_status"], "ABSENT")
        self.assertTrue(result["scan_complete"])
        self.assertEqual(result["images"], [])
        self.assertEqual(result["summary"], "NO_API26_CANDIDATE_IN_COMPLETE_SCAN")

    def test_complete_api26_candidate_not_runtime_acceptance(self):
        self.emulator(); self.image()
        result = self.output()
        self.assertEqual(result["emulator"]["status"], "PRESENT")
        self.assertTrue(result["scan_complete"])
        self.assertEqual(result["images_status"], "PRESENT")
        self.assertEqual(result["summary"], "API26_CANDIDATE_FILES_PRESENT")
        self.assertEqual(len(result["images"]), 1)
        self.assertTrue(result["images"][0]["api26_candidate"])
        self.assertNotIn("READY_TO_BOOT", str(result))

    def test_newer_images_not_mistaken_for_api26(self):
        self.image(api="36")
        result = self.output()
        self.assertTrue(result["scan_complete"])
        self.assertFalse(result["images"][0]["api26_candidate"])
        self.assertEqual(result["summary"], "NO_API26_CANDIDATE_IN_COMPLETE_SCAN")

    def test_host_architecture_mismatch_not_candidate(self):
        self.image()
        result = self.output(machine="aarch64")
        self.assertFalse(result["images"][0]["api26_candidate"])
        self.assertNotEqual(result["summary"], "API26_CANDIDATE_FILES_PRESENT")

    def test_x86_guest_on_x86_64_host_is_candidate(self):
        self.image(abi="x86")
        self.assertTrue(self.output()["images"][0]["api26_candidate"])

    def test_each_kernel_alternative(self):
        directory = self.image()
        (directory / "kernel-ranchu").unlink()
        for name in ("kernel-ranchu", "kernel-ranchu-64", "kernel-qemu"):
            with self.subTest(kernel=name):
                file = directory / name; file.write_bytes(b"kernel")
                self.assertTrue(self.output()["images"][0]["api26_candidate"])
                file.unlink()
        self.assertFalse(self.output()["images"][0]["api26_candidate"])

    def test_missing_or_zero_core_file_not_candidate(self):
        directory = self.image()
        for name in ("system.img", "ramdisk.img", "userdata.img"):
            with self.subTest(file=name):
                file = directory / name; data = file.read_bytes(); file.unlink()
                self.assertFalse(self.output()["images"][0]["api26_candidate"])
                file.write_bytes(b"")
                self.assertFalse(self.output()["images"][0]["api26_candidate"])
                file.write_bytes(data)

    def test_metadata_ambiguities_are_unknown_not_absence(self):
        directory = self.image(); props = directory / "source.properties"
        valid = props.read_bytes()
        cases = [b"\xff", valid + b"AndroidVersion.ApiLevel=26\n", valid + b"SystemImage.Abi=x86\n",
                 valid.replace(b"ApiLevel=26", b"ApiLevel=36"), valid.replace(b"Abi=x86_64", b"Abi=arm64-v8a"),
                 valid.replace(b"Pkg.Revision=1\n", b""), valid + b"#" * 65537,
                 valid.replace(b"Pkg.Revision=1", b"Pkg.Revision=" + b"1" * 513)]
        for contents in cases:
            with self.subTest(contents=contents[:60]):
                props.write_bytes(contents)
                result = self.output()
                self.assertFalse(result["scan_complete"])
                self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")
                self.assertFalse(any(p["api26_candidate"] for p in result["images"]))

    def test_symlink_root_or_package_not_followed(self):
        directory = self.image()
        elsewhere = self.root / "elsewhere"; directory.rename(elsewhere)
        directory.symlink_to(elsewhere, target_is_directory=True)
        result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")
        self.assertFalse(any(p["api26_candidate"] for p in result["images"]))

    def test_symlink_metadata_is_unknown(self):
        directory = self.image(); props = directory / "source.properties"
        external = self.root / "metadata"; props.rename(external); props.symlink_to(external)
        result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertFalse(any(p["api26_candidate"] for p in result["images"]))

    def test_symlink_image_is_not_validated(self):
        directory = self.image(); image = directory / "system.img"
        external = self.root / "image"; image.rename(external); image.symlink_to(external)
        result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertFalse(any(p["api26_candidate"] for p in result["images"]))

    def test_directory_scan_budget_marks_partial_unknown(self):
        images = self.sdk / "system-images"; images.mkdir()
        for index in range(129): (images / f"android-{index}").mkdir()
        result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")
        self.assertLessEqual(len(result["images"]), 128)

    def test_unexpected_layout_not_reported_as_absent(self):
        images = self.sdk / "system-images"; images.mkdir()
        (images / "unexpected-file").write_bytes(b"unknown shape")
        result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")

    def test_only_metadata_read_no_image_or_avd_reads_and_no_mutation(self):
        emulator = self.emulator(); image = self.image()
        avd = self.sdk / ".android"; avd.mkdir(); (avd / "private").write_bytes(b"must not read")
        paths = [p for p in self.sdk.rglob("*") if p.is_file()]
        before = {str(p): (p.stat().st_mode, p.stat().st_mtime_ns, hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths}
        original_open = Path.open
        def permitted_open(path, *args, **kwargs):
            self.assertEqual(path.name, "source.properties", f"Unexpected content read: {path}")
            self.assertIn(path.parent, (emulator, image))
            mode = args[0] if args else kwargs.get("mode", "r")
            self.assertNotIn("w", mode); self.assertNotIn("a", mode); self.assertNotIn("+", mode)
            return original_open(path, *args, **kwargs)
        original_os_open = os.open
        def permitted_os_open(path, flags, *args, **kwargs):
            path = Path(path)
            self.assertEqual(path.name, "source.properties")
            self.assertIn(path.parent, (emulator, image))
            self.assertEqual(flags & os.O_ACCMODE, os.O_RDONLY)
            self.assertFalse(flags & (os.O_CREAT | os.O_TRUNC | os.O_APPEND))
            return original_os_open(path, flags, *args, **kwargs)
        with patch.object(Path, "open", permitted_open), patch("os.open", permitted_os_open):
            result = self.output()
        self.assertTrue(result["images"][0]["api26_candidate"])
        after = {str(p): (p.stat().st_mode, p.stat().st_mtime_ns, hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths}
        self.assertEqual(before, after)
        self.assertEqual(set(paths), {p for p in self.sdk.rglob("*") if p.is_file()})

    def test_kvm_absence_observed_without_open(self):
        result = self.output()
        self.assertEqual(result["kvm"]["status"], "ABSENT")
        self.assertFalse(self.kvm.exists())

    def test_regular_file_kvm_is_not_character_device(self):
        self.kvm.write_bytes(b"not kvm")
        result = self.output()
        self.assertFalse(result["kvm"]["character_device"])
        self.assertEqual(result["kvm"]["status"], "UNKNOWN")


    def test_emulator_nonexecutable_or_missing_revision_unknown(self):
        directory = self.emulator()
        binary = directory / "emulator"
        binary.chmod(0o600)
        self.assertEqual(self.output()["emulator"]["status"], "UNKNOWN")
        binary.chmod(0o700)
        (directory / "source.properties").write_text("Pkg.Desc=Emulator\n")
        self.assertEqual(self.output()["emulator"]["status"], "UNKNOWN")
        binary.unlink()
        self.assertEqual(self.output()["emulator"]["status"], "ABSENT")

    def test_unreadable_metadata_retained_unknown(self):
        directory = self.image()
        original = os.open
        def unreadable(path, flags, *args, **kwargs):
            if Path(path) == directory / "source.properties": raise PermissionError("synthetic")
            return original(path, flags, *args, **kwargs)
        with patch("os.open", unreadable): result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")
        self.assertFalse(result["images"][0]["api26_candidate"])

    def test_unreadable_tree_unknown_not_empty(self):
        images = self.sdk / "system-images"; images.mkdir()
        with patch("os.scandir", side_effect=PermissionError("synthetic")):
            result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")

    def test_sdk_symlink_unknown_not_followed(self):
        real = self.sdk
        link = self.root / "sdk-link"; link.symlink_to(real, target_is_directory=True)
        self.sdk = link
        result = self.output()
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")

    def test_kvm_stat_failure_unknown(self):
        original = Path.lstat
        def lstat(path, *args, **kwargs):
            if path == self.kvm: raise PermissionError("synthetic")
            return original(path, *args, **kwargs)
        with patch.object(Path, "lstat", lstat): result = self.output()
        self.assertEqual(result["kvm"]["status"], "UNKNOWN")

    def test_kvm_character_metadata_does_not_claim_usable_acceleration(self):
        original = Path.lstat
        def lstat(path, *args, **kwargs):
            if path == self.kvm: return SimpleNamespace(st_mode=stat.S_IFCHR | 0o600)
            return original(path, *args, **kwargs)
        with patch.object(Path, "lstat", lstat), patch("os.access", return_value=False):
            result = self.output()
        self.assertEqual(result["kvm"]["status"], "PRESENT")
        self.assertTrue(result["kvm"]["character_device"])
        self.assertFalse(result["kvm"]["read_access"])
        self.assertFalse(result["kvm"]["write_access"])
        self.assertFalse(result["runtime_started"])


    def test_exact_metadata_and_known_value_bounds(self):
        directory = self.image(); props = directory / "source.properties"
        base = props.read_bytes().replace(b"Pkg.Revision=1", b"Pkg.Revision=" + b"1" * 512)
        contents = base + b"#" + b"x" * (65536 - len(base) - 1)
        self.assertEqual(len(contents), 65536)
        props.write_bytes(contents)
        result = self.output()
        self.assertTrue(result["scan_complete"])
        self.assertTrue(result["images"][0]["api26_candidate"])
        props.write_bytes(contents + b"x")
        self.assertEqual(self.output()["summary"], "INCOMPLETE_INVENTORY")

    def test_partial_scan_with_valid_candidate_stays_incomplete(self):
        self.image()
        other = self.image(api="36")
        (other / "source.properties").write_bytes(b"invalid metadata")
        result = self.output()
        self.assertTrue(any(p["api26_candidate"] for p in result["images"]))
        self.assertFalse(result["scan_complete"])
        self.assertEqual(result["summary"], "INCOMPLETE_INVENTORY")


if __name__ == "__main__":
    unittest.main(verbosity=2)
