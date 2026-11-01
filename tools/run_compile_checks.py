#!/usr/bin/env python3
"""Collect independent compile diagnostics, but fail aggregate on ANY gate failure."""
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
APK = 'app/build/outputs/apk/debug/app-debug.apk'
REPORTS = ['app/build/reports/lint-results-debug.' + suffix for suffix in ['xml', 'txt', 'html']]


def run_checks(root, expected_sha, runner=subprocess.run):
    # Fixed generated outputs only: no previous build may satisfy this invocation.
    for name in [APK, *REPORTS, 'lint-receipt.json', 'compile-receipt.json',
                 'baseline-receipt.json', 'baseline-checklist.md']:
        (root / name).unlink(missing_ok=True)
    started = int(time.time())
    build = runner(['./gradlew', '--no-daemon', '--console=plain', ':app:assembleDebug', ':app:lintDebug'], cwd=root)
    results = {'gradle': build.returncode}
    for name in REPORTS[:2]:
        path = root / name
        if path.is_file():
            data = path.read_text()
            print('=== raw ' + name + ' ===', flush=True)
            print(data[:200000], flush=True)
            if len(data) > 200000:
                results['diagnostic_bound'] = 1
    checks = [
        ('lint', ['python3', 'tools/verify_lint.py', REPORTS[0], '--not-before', str(started), '--source-sha', expected_sha], 'lint-receipt.json'),
        ('package', ['python3', 'tools/verify_apk.py', APK], 'compile-receipt.json'),
    ]
    for name, command, receipt in checks:
        if name == 'package' and not (root / APK).is_file():
            results[name] = 1
            print('APK absent: package verification NOT_RUN; aggregate fails', file=sys.stderr)
            continue
        process = runner(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        results[name] = process.returncode
        print(process.stderr, file=sys.stderr, end='', flush=True)
        print(process.stdout, end='', flush=True)
        if process.returncode == 0:
            (root / receipt).write_text(process.stdout)
    print('Compile gate exit codes: ' + str(results), flush=True)
    return 0 if all(code == 0 for code in results.values()) else 1


def main():
    expected = os.environ['EXPECTED_SOURCE_SHA']
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == expected
    result = run_checks(ROOT, expected)
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == expected
    assert not subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip()
    raise SystemExit(result)


if __name__ == '__main__':
    main()
