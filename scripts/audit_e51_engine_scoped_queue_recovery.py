#!/usr/bin/env python3
"""E51: execute/recover only tasks owned by the selected engine key."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
runner = (root / "core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt").read_text()
tests = (root / "core/src/test/kotlin/vn/fonesi/core/update/UpdateRunnerTest.kt").read_text()
entry = runner.split("override fun runNext(): RunResult {", 1)[1].split("override fun recover(): Boolean {", 1)[0]
recovery = runner.split("private fun recoverLocked() {", 1)[1].split("private fun process(", 1)[0]
assert "val jobs = store.list().filter { it.engineKey == engineKey }" in entry, "Queue must be scoped before scheduling and checking same-package journals"
assert "for (old in store.list().filter { it.engineKey == engineKey }) {" in recovery, "Recovery must scope engine before mutating journal/snapshots"
assert recovery.index("for (old in store.list().filter { it.engineKey == engineKey }) {") < recovery.index("mayUseLegacySource(old)")
assert "recoverLocked()" in entry and "store.isPaused()" in entry and "mayUseLegacySource(next)" in entry
for name in (
    "foreign only queued work remains untouched instead of ENGINE_CHANGED",
    "foreign queued same-package work ahead of local task cannot steal this runner",
    "foreign preparing journal and snapshot survive recovery and local execution",
    "foreign applying task cannot be recovered from matching local engine metadata",
    "foreign review-required same-package task does not block local queue",
):
    assert name in tests, "Missing JVM regression: " + name
assert "assertEquals(TaskIssue.ENGINE_CHANGED, f.runner().runNext().task().issue)" not in tests
print("PASS: E51 engine scope precedes runner selection and recovery side effects; regression tests present.")
