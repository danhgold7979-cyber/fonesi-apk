#!/usr/bin/env python3
"""E54: skip blocked queue work only across unrelated packages, never around same-package barriers."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
runner = (root / "core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt").read_text()
tests = (root / "core/src/test/kotlin/vn/fonesi/core/update/UpdateRunnerTest.kt").read_text()
entry = runner.split("override fun runNext(): RunResult {", 1)[1].split("override fun recover(): Boolean {", 1)[0]
assert "val eligible = jobs.filter { candidate ->" in entry
assert "jobs.takeWhile { it.id != candidate.id }.none" in entry
assert "it.packageName == candidate.packageName && !it.state.terminal" in entry
assert "it.packageName == candidate.packageName && it.state == TaskState.REVIEW_REQUIRED" in entry
assert "eligible.firstOrNull(::mayUseLegacySource)" in entry
assert "eligible.isEmpty()) RunResult.Idle else RunResult.PolicyBlocked" in entry
assert entry.index("val eligible = jobs.filter") < entry.index("eligible.firstOrNull(::mayUseLegacySource)")
assert "if (!mayUseLegacySource(next)) return@use RunResult.PolicyBlocked" not in entry
for name in (
    "retained private head does not starve unrelated package work",
    "owner only private head does not starve unrelated package work",
    "same package original work cannot bypass protected predecessor",
):
    assert name in tests, "Missing E54 regression: " + name
assert "const val supportsSplits = false" in (root / "app/src/candidate/java/vn/fonesi/app/runtime/RuntimeBindings.kt").read_text()
print("PASS: E54 selects allowed work across unrelated packages while preserving same-package FIFO/review barriers.")
print("PASS: denied authority remains fail-closed and split/provider activation remains disabled.")
