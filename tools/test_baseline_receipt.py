"""Synthetic receipt identity/failure tests; fake bytes never count as APK evidence."""
import copy
import hashlib
from pathlib import Path
import unittest
from record_baseline import bind
from run_host_checks import COMMANDS, parse_count


class BaselineReceiptTest(unittest.TestCase):
    def setUp(self):
        self.sha = 'a' * 40
        self.bytes = b'synthetic-only-not-an-apk'
        base = {'source_sha': self.sha, 'ANDROID': 'NOT_RUN', 'PROVIDER': 'NOT_RUN', 'PHONE': 'NOT_RUN'}
        checks = []
        for command in COMMANDS:
            kind = 'synthetic_assertions' if command[-1].endswith('test_core.sh') else ('unittest_cases' if Path(command[-1]).name.startswith('test_') else 'integrity_command')
            checks.append({'command': command, 'exit': 0, 'kind': kind, 'count': None if kind == 'integrity_command' else 1})
        self.host = {**base, 'tier': 'HOST', 'result': 'PASS', 'checks': checks}
        self.compile = {**base, 'tier': 'COMPILE', 'manifest': 'PASS', 'signature': 'PASS_DEBUG_ONLY',
                        'backup_rules': 'PASS_PACKAGED_STATIC_ONLY', 'application_id': 'vn.fonesi.app',
                        'version_code': '1', 'version_name': '0.1.0-foundation',
                        'bytes': len(self.bytes), 'sha256': hashlib.sha256(self.bytes).hexdigest()}
        self.lint = {**base, 'scope': 'FOUNDATION_DEBUG_BASELINE_ONLY', 'release_acceptance': False,
                     'result': 'PASS_WITH_RECORDED_TARGET36_NOTICE_NOT_ZERO_WARNINGS',
                     'notice': {'id': 'OldTargetApi', 'severity': 'Information'},
                     'reports': {name: '0'*64 for name in ['lint-results-debug.xml', 'lint-results-debug.txt', 'lint-results-debug.html']}}

    def check(self):
        return bind(self.host, self.compile, self.lint, self.bytes, self.sha)

    def test_positive_never_accepts_task_or_runtime(self):
        receipt = self.check()
        self.assertEqual(receipt['accepted_tasks_added'], [])
        self.assertEqual(receipt['PROVIDER'], 'NOT_RUN')

    def test_stale_receipt_or_changed_apk(self):
        for receipt in [self.host, self.compile, self.lint]:
            receipt['source_sha'] = 'b'*40
            with self.assertRaises(AssertionError): self.check()
            receipt['source_sha'] = self.sha
        self.bytes += b'changed'
        with self.assertRaises(AssertionError): self.check()

    def test_missing_zero_or_failed_host_checks(self):
        original = copy.deepcopy(self.host['checks'])
        self.host['checks'].pop()
        with self.assertRaises(AssertionError): self.check()
        for field, value in [('count', 0), ('exit', 1), ('kind', 'integrity_command')]:
            self.host['checks'] = copy.deepcopy(original)
            self.host['checks'][2][field] = value
            with self.assertRaises(AssertionError): self.check()

    def test_wrong_artifact_semantics_and_runtime_uplift(self):
        for field, value in [('version_code', '2'), ('version_name', 'wrong'),
                             ('signature', 'NOT_RUN'), ('backup_rules', 'NOT_RUN'),
                             ('manifest', 'NOT_RUN'), ('application_id', 'other'), ('PHONE', 'PASS')]:
            old = self.compile[field]
            self.compile[field] = value
            with self.assertRaises(AssertionError): self.check()
            self.compile[field] = old

    def test_release_or_hidden_lint_notice_rejected(self):
        self.lint['release_acceptance'] = True
        with self.assertRaises(AssertionError): self.check()
        self.lint['release_acceptance'] = False
        del self.lint['reports']['lint-results-debug.xml']
        with self.assertRaises(AssertionError): self.check()

    def test_actual_count_parser_rejects_zero_skipped_or_missing(self):
        self.assertEqual(parse_count(['python3', 'test_example.py'], '\nRan 3 tests in 0.1s\n\nOK\n')['count'], 3)
        self.assertEqual(parse_count(['python3', 'test_example.py'], '\nRan 3 tests in 0.1s\n\nOK\nPASS pinned infrastructure\n')['count'], 3)
        for output in ['Ran 0 tests in 0.1s\n\nOK\n', 'Ran 3 tests in 0.1s\n\nOK (skipped=1)\n', 'PASS']:
            with self.assertRaises(AssertionError): parse_count(['python3', 'test_example.py'], output)
        with self.assertRaises(AssertionError): parse_count(['bash', 'tools/test_core.sh'], 'unverified')
        self.assertEqual(parse_count(['bash', 'tools/test_core.sh'], 'UpdatePreflightTest PASS: 83 checks; HOST synthetic display-only; runtime unavailable')['count'], 83)


if __name__ == '__main__':
    unittest.main(verbosity=2)
