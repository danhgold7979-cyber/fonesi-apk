"""Synthetic protocol fixtures only; no Android execution."""
import unittest
from dataclasses import FrozenInstanceError
from parse_fixture_result import parse_result, CASES, SCHEMA, TARGET

SHA = 'a' * 40

def receipt():
    values = {'schema': SCHEMA, 'target': TARGET, 'source_sha': SHA,
              'cases': ','.join(CASES), 'result': 'PASS'}
    values.update({'assertions_' + case: str(i + 1) for i, case in enumerate(CASES)})
    return '\n'.join('INSTRUMENTATION_RESULT: ' + k + '=' + v for k, v in sorted(values.items())) + '\nINSTRUMENTATION_CODE: -1\n'

class FixtureResultTest(unittest.TestCase):
    def reject(self, text):
        with self.assertRaises(ValueError): parse_result(text.encode(), SHA)
    def test_scalar_order_and_immutable(self):
        result = parse_result(receipt().encode(), SHA)
        self.assertEqual(result.counts, (1,2,3,4,5))
        with self.assertRaises(FrozenInstanceError): result.source_sha = 'b' * 40
        lines=receipt().splitlines()
        self.assertEqual(parse_result(('\n'.join(list(reversed(lines[:-1]))+[lines[-1]])).encode(),SHA),result)
        self.assertEqual(parse_result(receipt().replace('\n','\r\n').encode(),SHA),result)
    def test_exact_identity_and_success(self):
        for a,b in [(SHA,'b'*40),(TARGET,'vn.fonesi.app'),(SCHEMA,'owned-storage-fixture-v1'),
                    ('result=PASS','result=FAIL'),(',cancelled',',other')]:
            with self.subTest(a=a): self.reject(receipt().replace(a,b))
    def test_missing_duplicate_unknown_keys(self):
        lines=receipt().splitlines()
        for changed in [lines[1:], [lines[0]]+lines, lines[:-1]+['INSTRUMENTATION_RESULT: extra=1',lines[-1]],
                        [lines[0]]+lines[:-2]+[lines[-1]]]:
            self.reject('\n'.join(changed))
    def test_counts_canonical_bounded(self):
        for value in ['0','-1','01','+1',' 1','1 ','1.0','2147483648','9'*500,'١','[I@123','']:
            with self.subTest(value=value): self.reject(receipt().replace('assertions_missing=1','assertions_missing='+value))
        self.assertEqual(parse_result(receipt().replace('assertions_missing=1','assertions_missing=2147483647').encode(),SHA).counts[0],2147483647)
    def test_terminal_and_transport_failures(self):
        for text in [receipt().replace('CODE: -1','CODE: 0'),receipt().replace('CODE: -1','CODE: -1\nINSTRUMENTATION_CODE: -1'),
                     receipt()+'trailing',receipt()+'\n','INSTRUMENTATION_STATUS: stream=crash\n'+receipt(),
                     receipt().replace('INSTRUMENTATION_CODE: -1\n',''),receipt().replace('\n','\r')]:
            self.reject(text)
    def test_encoding_and_input_limits(self):
        for raw in [b'', b'\xff', b'x'*8193,bytearray(receipt().encode()),receipt()]:
            with self.assertRaises(ValueError): parse_result(raw,SHA)
        for sha in ['',SHA.upper(),'a'*39,None]:
            with self.assertRaises(ValueError): parse_result(receipt().encode(),sha)
    def test_scalar_source_contract(self):
        from pathlib import Path
        root=Path(__file__).resolve().parents[1]
        text=(root/'storage-fixture/src/main/java/vn/fonesi/app/storage/StorageFixture.java').read_text()
        self.assertIn('output.putInt("assertions_" + CASES[i], fixture.counts[i])',text)
        self.assertNotIn('putIntArray',text);self.assertNotIn('putStringArray',text)
        self.assertIn('output.putString("cases", "'+','.join(CASES)+'")',text)
        self.assertIn(SCHEMA,text)
        from run_host_checks import COMMANDS
        self.assertEqual(COMMANDS.count(['python3','tools/test_fixture_result.py']),1)

if __name__ == '__main__': unittest.main(verbosity=2)
