#!/usr/bin/env python3
"""Finite debug-baseline notice gate; no Gradle failure is waived by this script."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NOTICE = 'OldTargetApi'


def validate_report(xml, root):
    report = ET.fromstring(xml)
    assert report.tag == 'issues', 'not a lint report'
    issues = list(report)
    assert len(issues) == 1 and issues[0].tag == 'issue', 'missing, duplicate or additional diagnostic'
    issue = issues[0]
    assert issue.get('id') == NOTICE
    # Exact upstream XML severity spelling; unknown aliases fail closed.
    assert issue.get('severity') == 'Information'
    assert issue.get('message', '').startswith('Not targeting the latest versions of Android;')
    locations = list(issue)
    assert len(locations) == 1 and locations[0].tag == 'location'
    location = locations[0]
    path = Path(location.get('file', ''))
    if not path.is_absolute():
        path = root / path
    assert path.resolve() == (root / 'app/build.gradle').resolve(), 'wrong notice source'
    line = int(location.get('line', '0'))
    lines = path.read_text().splitlines()
    assert 0 < line <= len(lines) and lines[line - 1].strip() == 'targetSdk 36'
    return {'id': NOTICE, 'severity': 'Information', 'file': 'app/build.gradle',
            'line': line, 'message': issue.get('message')}


def verify_files(report, root, not_before, expected_sha, actual_sha):
    assert expected_sha == actual_sha and len(actual_sha) == 40, 'source SHA mismatch'
    assert not_before > 0
    files = [report, report.with_suffix('.txt'), report.with_suffix('.html')]
    for path in files:
        assert path.is_file() and path.stat().st_size > 0, 'missing/empty lint evidence'
        assert path.stat().st_mtime >= not_before, 'stale lint evidence'
    notice = validate_report(report.read_text(), root)
    assert NOTICE in files[1].read_text() and NOTICE in files[2].read_text(), 'notice hidden in reports'
    return {'source_sha': actual_sha, 'scope': 'FOUNDATION_DEBUG_BASELINE_ONLY',
            'result': 'PASS_WITH_RECORDED_TARGET36_NOTICE_NOT_ZERO_WARNINGS',
            'notice': notice,
            'reports': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
            'release_acceptance': False, 'ANDROID': 'NOT_RUN', 'PROVIDER': 'NOT_RUN', 'PHONE': 'NOT_RUN'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('report', type=Path)
    parser.add_argument('--not-before', type=int, required=True)
    parser.add_argument('--source-sha', required=True)
    args = parser.parse_args()
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    print(json.dumps(verify_files(args.report, ROOT, args.not_before, args.source_sha, actual), indent=2))


if __name__ == '__main__':
    main()
