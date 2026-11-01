#!/usr/bin/env python3
"""Bind actual HOST, lint and packaged APK receipts; never accepts a product task."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
from run_host_checks import COMMANDS, check_kind
from verify_apk import catalog_source_hashes

ROOT = Path(__file__).resolve().parents[1]
APK = Path('app/build/outputs/apk/debug/app-debug.apk')


def bind(host, compile_, lint, apk_bytes, source_sha):
    assert len(source_sha) == 40
    for receipt in [host, compile_, lint]:
        assert receipt['source_sha'] == source_sha, 'stale receipt source'
        assert all(receipt[tier] == 'NOT_RUN' for tier in ['ANDROID', 'PROVIDER', 'PHONE'])
    assert host['tier'] == 'HOST' and host['result'] == 'PASS'
    assert [check['command'] for check in host['checks']] == COMMANDS, 'missing/reordered HOST aggregate checks'
    assert all(check['exit'] == 0 for check in host['checks'])
    for check in host['checks']:
        expected_kind = check_kind(check['command'])
        assert check['kind'] == expected_kind
        if check['kind'] != 'integrity_command':
            assert isinstance(check['count'], int) and check['count'] > 0
    assert compile_['catalog_sql_assets'] == catalog_source_hashes(), 'missing/stale packaged SQL proof'
    assert compile_['tier'] == 'COMPILE'
    assert compile_['manifest'] == 'PASS' and compile_['signature'] == 'PASS_DEBUG_ONLY'
    assert compile_['backup_rules'] == 'PASS_PACKAGED_STATIC_ONLY'
    assert compile_['application_id'] == 'vn.fonesi.app'
    assert compile_['version_code'] == '1' and compile_['version_name'] == '0.1.0-foundation'
    assert compile_['bytes'] == len(apk_bytes) > 0
    assert compile_['sha256'] == hashlib.sha256(apk_bytes).hexdigest(), 'APK changed after verification'
    assert lint['scope'] == 'FOUNDATION_DEBUG_BASELINE_ONLY' and lint['release_acceptance'] is False
    assert lint['result'] == 'PASS_WITH_RECORDED_TARGET36_NOTICE_NOT_ZERO_WARNINGS'
    assert lint['notice']['id'] == 'OldTargetApi' and lint['notice']['severity'] == 'Hint'
    assert set(lint['reports']) == {'lint-results-debug.xml', 'lint-results-debug.txt', 'lint-results-debug.html'}
    return {'schema_version': 1, 'source_sha': source_sha,
            'scope': 'FOUNDATION_HOST_COMPILE_EVIDENCE_CANDIDATE_NOT_PRODUCT_ACCEPTANCE',
            'artifact_path': str(APK), 'artifact_sha256': compile_['sha256'],
            'artifact_bytes': len(apk_bytes), 'application_id': compile_['application_id'],
            'version_code': compile_['version_code'], 'version_name': compile_['version_name'],
            'HOST': host, 'COMPILE': compile_, 'lint': lint,
            'ANDROID': 'NOT_RUN', 'PROVIDER': 'NOT_RUN', 'PHONE': 'NOT_RUN',
            'accepted_tasks_added': [], 'provider': 'UNAVAILABLE_NOT_ACCEPTED'}


def main():
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    assert sha == os.environ['EXPECTED_SOURCE_SHA']
    assert not subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip()
    host, compile_, lint = [json.loads((ROOT / name).read_text()) for name in ['host-receipt.json', 'compile-receipt.json', 'lint-receipt.json']]
    for name, expected in lint['reports'].items():
        assert hashlib.sha256((ROOT / 'app/build/reports' / name).read_bytes()).hexdigest() == expected
    receipt = bind(host, compile_, lint, (ROOT / APK).read_bytes(), sha)
    receipt['timestamp_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    receipt['ci_run'] = os.environ.get('GITHUB_RUN_ID', 'LOCAL')
    receipt['reproduction'] = 'docs/P0_BASELINE_REPRODUCTION.md'
    (ROOT / 'baseline-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    (ROOT / 'baseline-checklist.md').write_text(
        '# Foundation debug artifact — not a working clone manager\n\n'
        f'Source `{sha}`; version1 /0.1.0-foundation; APK SHA256 `{receipt["artifact_sha256"]}`.\n\n'
        'Includes inert native shell, synthetic update-display preflight, backup exclusions and pinned build infrastructure. '
        'HOST and COMPILE receipts attached; target36 freshness notice explicitly retained, not zero warnings.\n\n'
        'NOT RUN: Android UI/lifecycle, provider isolation, eight-app compatibility, phone/accounts. '
        'Provider remains unavailable; create/open/stop/delete controls disabled. Debug-only signing; no release key.\n\n'
        'No installation or account operation is authorized by downloading this artifact. '
        'Reproduce with docs/P0_BASELINE_REPRODUCTION.md; compare receipt identities before later acceptance.\n')
    print('Recorded exact-source baseline evidence candidate; T03/T04 acceptance requires independent review.')


if __name__ == '__main__':
    main()
