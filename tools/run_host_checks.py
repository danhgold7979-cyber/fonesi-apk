#!/usr/bin/env python3
"""One HOST aggregate owner; receipts are emitted only after actual successful checks."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
COMMANDS = [
    ['python3', 'tools/check_plan.py'],
    ['python3', 'tools/check_toolchain.py'],
    *[['python3', 'tools/' + name] for name in (
        'test_plan_integrity.py', 'test_toolchain_contract.py', 'test_shell_contract.py',
        'test_apk_contract.py', 'test_lint_policy.py', 'test_baseline_receipt.py', 'test_compile_aggregate.py', 'test_host_integration.py', 'test_style_migration.py',
        'test_catalog_writer_sql.py', 'test_android_catalog_contract.py', 'test_existing_read_entry.py',
        'test_catalog_page_sql.py', 'test_catalog_page_read.py', 'test_catalog_reservation_integration.py')],
    ['bash', 'tools/test_core.sh'],
    ['bash', 'tools/test_style_core.sh'],
    ['bash', 'tools/test_catalog_key.sh'],
    ['bash', 'tools/test_catalog_ownership.sh'],
    ['bash', 'tools/test_catalog_page.sh'],
    ['bash', 'tools/test_catalog_reservation.sh'],
]


def check_kind(command):
    if command[-1] in ('tools/test_core.sh', 'tools/test_style_core.sh', 'tools/test_catalog_key.sh', 'tools/test_catalog_ownership.sh', 'tools/test_catalog_page.sh', 'tools/test_catalog_reservation.sh'):
        return 'synthetic_assertions'
    return 'unittest_cases' if Path(command[-1]).name.startswith('test_') else 'integrity_command'


def parse_count(command, output):
    if command[-1] == 'tools/test_catalog_reservation.sh':
        assert 'LIMITED HOST' not in output, 'limited compiler cannot produce JDK17 receipt'
        matches = re.findall(r'(?m)^CatalogReservation HOST assertions: (\d+) PASS; no auth/file/Android acceptance$', output)
        assert len(matches) == 1 and int(matches[0]) > 0, 'missing/duplicate/nonpositive reservation count'
        return {'kind': 'synthetic_assertions', 'count': int(matches[0])}
    if command[-1] == 'tools/test_catalog_page.sh':
        assert 'LIMITED HOST' not in output, 'limited compiler cannot produce JDK17 receipt'
        matches = re.findall(r'(?m)^PASS CatalogPage owned HOST assertions=(\d+); no Android/schema/auth/provider acceptance$', output)
        assert len(matches) == 1 and int(matches[0]) > 0, 'missing/duplicate/nonpositive page count'
        return {'kind': 'synthetic_assertions', 'count': int(matches[0])}
    if command[-1] == 'tools/test_catalog_ownership.sh':
        assert 'LIMITED HOST' not in output, 'limited compiler cannot produce JDK17 receipt'
        matches = re.findall(r'(?m)^CatalogOwnership HOST assertions: (\d+) PASS$', output)
        assert len(matches) == 1 and int(matches[0]) > 0, 'missing/duplicate/nonpositive ownership count'
        return {'kind': 'synthetic_assertions', 'count': int(matches[0])}
    if command[-1] == 'tools/test_catalog_key.sh':
        assert 'LIMITED HOST' not in output, 'limited compiler cannot produce JDK17 receipt'
        matches = re.findall(r'(?m)^PASS CatalogKey owned HOST assertions=(\d+); no SQLite adapter/Android/auth/provider acceptance$', output)
        assert len(matches) == 1 and int(matches[0]) > 0, 'missing/duplicate/nonpositive catalog assertion count'
        return {'kind': 'synthetic_assertions', 'count': int(matches[0])}
    if command[-1] == 'tools/test_style_core.sh':
        assert 'LIMITED HOST' not in output, 'limited compiler cannot produce JDK17 receipt'
        matches = re.findall(r'(?m)^PASS StyleValue owned HOST assertions=(\d+); no Android/rendering/persistence/provider acceptance$', output)
        assert len(matches) == 1 and int(matches[0]) > 0, 'missing/duplicate/nonpositive style assertion count'
        return {'kind': 'synthetic_assertions', 'count': int(matches[0])}
    if command[-1].endswith('test_core.sh'):
        match = re.search(r'UpdatePreflightTest PASS: (\d+) checks; HOST synthetic display-only;', output)
        assert match and int(match[1]) > 0, 'missing/nonpositive Java check count'
        return {'kind': 'synthetic_assertions', 'count': int(match[1])}
    if Path(command[-1]).name.startswith('test_'):
        counts = re.findall(r'Ran (\d+) tests? in ', output)
        summaries = re.findall(r'(?m)^OK[^\n]*$', output)
        assert len(counts) == 1 and int(counts[0]) > 0 and summaries == ['OK'], 'missing/zero/skipped/failed test suite'
        return {'kind': 'unittest_cases', 'count': int(counts[0])}
    assert 'PASS' in output, 'missing documentary/infrastructure receipt'
    return {'kind': 'integrity_command', 'count': None}


def main():
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    assert sha == os.environ['EXPECTED_SOURCE_SHA']
    assert not subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip(), 'tracked source changed'
    java = subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True)
    assert re.search(r'version "17\.', java), 'full JDK17 required, limited-JRE mode not accepted'
    checks = []
    log = java
    for command in COMMANDS:
        process = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        print(process.stdout, end='', flush=True)
        log += '\n$ ' + ' '.join(command) + '\n' + process.stdout
        (ROOT / 'foundation-host.log').write_text(log)
        if process.returncode:
            raise SystemExit(process.returncode)
        checks.append({'command': command, 'exit': 0, **parse_count(command, process.stdout)})
    assert sha == subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    assert not subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip()
    receipt = {'source_sha': sha, 'tier': 'HOST', 'result': 'PASS', 'java': java.strip(),
               'checks': checks, 'log_sha256': hashlib.sha256(log.encode()).hexdigest(),
               'timestamp_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'ANDROID': 'NOT_RUN', 'PROVIDER': 'NOT_RUN', 'PHONE': 'NOT_RUN'}
    (ROOT / 'host-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write('receipt=' + json.dumps(receipt, separators=(',', ':')) + '\n')


if __name__ == '__main__':
    main()
