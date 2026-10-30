#!/usr/bin/env python3
"""Deterministic tests for E77's bounded Android UI fingerprint reader."""
import unittest
from types import SimpleNamespace

from run_guest_code_emulator import (
    UI_CONFIGURATION_COMMAND,
    UI_CONFIGURATION_READ_TIMEOUT_SECONDS,
    read_ui_configuration,
)


class UiConfigurationReaderTests(unittest.TestCase):
    def test_reads_all_seven_values_with_one_bounded_adb_shell_call(self):
        output = """[wm-size]
Physical size: 1080x1920
Override size: 480x800
[wm-density]
Physical density: 420
font-scale=1.0
user-rotation=0
locale=en-US
language=en
country=US
"""
        calls = []

        def fake_device(*args, **kwargs):
            calls.append((args, kwargs))
            return SimpleNamespace(returncode=0, stdout=output)

        snapshot = read_ui_configuration(fake_device)

        self.assertEqual(
            snapshot,
            (
                "[wm-size]",
                "Physical size: 1080x1920",
                "Override size: 480x800",
                "[wm-density]",
                "Physical density: 420",
                "font-scale=1.0",
                "user-rotation=0",
                "locale=en-US",
                "language=en",
                "country=US",
            ),
        )
        self.assertEqual(len(calls), 1)
        args, kwargs = calls[0]
        self.assertEqual(args[:3], ("shell", "sh", "-c"))
        self.assertEqual(args[3], UI_CONFIGURATION_COMMAND)
        self.assertEqual(kwargs, {"timeout": UI_CONFIGURATION_READ_TIMEOUT_SECONDS, "check": False})
        for required in (
            "wm size",
            "wm density",
            "settings get system font_scale",
            "settings get system user_rotation",
            "getprop persist.sys.locale",
            "getprop persist.sys.language",
            "getprop persist.sys.country",
        ):
            self.assertIn(required, UI_CONFIGURATION_COMMAND)

    def test_nonzero_adb_exit_fails_closed(self):
        def failed_device(*args, **kwargs):
            return SimpleNamespace(returncode=1, stdout="partial")

        with self.assertRaisesRegex(RuntimeError, "Cannot read Android UI configuration"):
            read_ui_configuration(failed_device)

    def test_empty_fingerprint_fails_closed(self):
        def empty_device(*args, **kwargs):
            return SimpleNamespace(returncode=0, stdout="\\n  \\n")

        with self.assertRaisesRegex(RuntimeError, "fingerprint is empty"):
            read_ui_configuration(empty_device)

    def test_per_sample_timeout_can_be_reduced_to_remaining_window(self):
        calls = []

        def fake_device(*args, **kwargs):
            calls.append(kwargs)
            return SimpleNamespace(returncode=0, stdout="locale=en-US\\n")

        read_ui_configuration(fake_device, timeout_seconds=3)
        self.assertEqual(calls, [{"timeout": 3, "check": False}])

    def test_unbounded_timeout_is_rejected_before_adb_call(self):
        calls = []

        def fake_device(*args, **kwargs):
            calls.append((args, kwargs))
            return SimpleNamespace(returncode=0, stdout="locale=en-US\\n")

        with self.assertRaises(ValueError):
            read_ui_configuration(fake_device, timeout_seconds=21)
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
