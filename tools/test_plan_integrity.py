"""Independent negative checks of the documentary validator, not product tests."""
import json,pathlib,shutil,subprocess,tempfile,unittest
SOURCE=pathlib.Path(__file__).resolve().parents[1]
class PlanIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(prefix='apk-plan-integrity-')
        cls.root=pathlib.Path(cls.tmp.name)/'repo'
        shutil.copytree(SOURCE,cls.root,ignore=shutil.ignore_patterns('.git','__pycache__'))
    @classmethod
    def tearDownClass(cls):cls.tmp.cleanup()
    def check_mutation(self,path,mutate):
        p=self.root/path;original=p.read_bytes()
        try:
            value=json.loads(original);mutate(value);p.write_text(json.dumps(value))
            result=subprocess.run(['python3',str(self.root/'tools/check_plan.py')],capture_output=True)
            self.assertNotEqual(result.returncode,0,'corrupt plan was accepted')
        finally:p.write_bytes(original)
    def test_positive_original(self):
        result=subprocess.run(['python3',str(self.root/'tools/check_plan.py')],capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr.decode())
    def test_missing_brg_bullet(self):
        self.check_mutation('docs/requirements.json',lambda x:x['requirements'].pop())
    def test_modified_assertion(self):
        self.check_mutation('docs/requirements.json',lambda x:x['requirements'][0].update(ledger_row_verbatim='shortened'))
    def test_invented_task(self):
        self.check_mutation('docs/requirements.json',lambda x:x['requirements'][0].update(primary_task='APK-P8-T33'))
    def test_imported_historical_acceptance(self):
        self.check_mutation('docs/requirements.json',lambda x:x['requirements'][0]['evidence'].update(PROVIDER='PASS'))
    def test_reference_hash_drift(self):
        self.check_mutation('reference/manifest.json',lambda x:x[0].update(sha256='0'*64))
    def test_schema_preservation_omission(self):
        def omit_schema(rows):
            rows[:]=[r for r in rows if not (r['source_path']=='rebuild-storage/spec/legacy-room/6.json' and r['source_commit']=='dccc27989b9c258ce9f99e28bc620ac03ac696ae')]
        self.check_mutation('reference/manifest.json',omit_schema)
    def test_preservation_obligation_omission(self):
        self.check_mutation('reference/preservation-obligations.json',lambda x:x.pop())
    def test_source_blob_identity_drift(self):
        self.check_mutation('reference/manifest.json',lambda x:x[0].update(source_blob='0'*40))
    def test_removed_path_omission(self):
        self.check_mutation('docs/clean-tree-manifest.json',lambda x:x['paths'].pop())
    def test_shortened_task_contract(self):
        self.check_mutation('docs/tasks.json',lambda x:x['tasks'][0].update(error_cancel_recovery=''))
    def test_missing_requirement_owner(self):
        self.check_mutation('docs/tasks.json',lambda x:x['tasks'][0]['requirement_ids_primary'].pop())
    def test_shortened_nonempty_obligation(self):
        self.check_mutation('docs/requirements.json',lambda x:x['requirements'][0].update(obligation_verbatim='No root.'))
    def test_shortened_nonempty_acceptance(self):
        self.check_mutation('docs/requirements.json',lambda x:x['requirements'][0].update(acceptance_verbatim='P opens.'))
    def test_status_provider_pass(self):
        for tier in ('ANDROID','PROVIDER','PHONE'):
            self.check_mutation('docs/status.json',lambda x:x['evidence'].update({tier:'PASS'}))
    def test_status_false_completed(self):
        self.check_mutation('docs/status.json',lambda x:x.update(completed_tasks=32))
    def test_status_false_requirement_acceptance(self):
        self.check_mutation('docs/status.json',lambda x:x.update(accepted_product_requirements=362))
    def test_tasks_false_completed(self):
        self.check_mutation('docs/tasks.json',lambda x:x.update(completed_count=32))
    def test_tasks_false_total(self):
        self.check_mutation('docs/tasks.json',lambda x:x.update(tasks_total=33))
    def test_task_false_accepted(self):
        self.check_mutation('docs/tasks.json',lambda x:x['tasks'][0].update(status='ACCEPTED'))
    def test_documentary_counter_drift(self):
        self.check_mutation('docs/status.json',lambda x:x['documentary_progress'].update(phase0_task_percent=100))
    def test_documentary_scope_uplift(self):
        self.check_mutation('docs/status.json',lambda x:x['documentary_progress'].update(scope='PRODUCT_COMPLETE'))
    def test_t03_false_acceptance(self):
        self.check_mutation('docs/tasks.json',lambda x:x['tasks'][2].update(status='ACCEPTED_DOCUMENTARY'))
        self.check_mutation('docs/evidence/foundation-acceptance.json',lambda x:x.update(source_sha='0'*40))
        self.check_mutation('docs/status.json',lambda x:x['task_progress'].update(all_task_percent=100))
        self.check_mutation('docs/tasks.json',lambda x:x['tasks'][4].update(status='ACCEPTED_HOST_COMPILE'))
        self.check_mutation('docs/evidence/provider-inventory-acceptance.json',lambda x:x.update(eligible_providers=1))
        self.check_mutation('docs/phase-01/candidate-eligibility.json',lambda x:x['candidates'][0].update(eligible=True))
        self.check_mutation('docs/phase-01/candidate-eligibility.json',lambda x:x['app_compatibility'].update(Facebook='PASS'))
        for index in (5,6,7):
            self.check_mutation('docs/tasks.json',lambda x:x['tasks'][index].update(status='ACCEPTED_DOCUMENTARY'))
        self.check_mutation('docs/status.json',lambda x:x['task_progress'].update(phase1_task_percent=100))
    def test_missing_documentary_receipt(self):
        self.check_mutation('docs/tasks.json',lambda x:x['tasks'][0].update(accepted_evidence=[]))
    def test_archival_main_false_clean(self):
        self.check_mutation('docs/evidence/documentary-acceptance.json',lambda x:x.update(main_is_clean=True))
    def test_old_executable_reference(self):
        p=self.root/'reference/history/forbidden.java';p.write_text('// forbidden historical executable')
        try:
            r=subprocess.run(['python3',str(self.root/'tools/check_plan.py')],capture_output=True)
            self.assertNotEqual(r.returncode,0)
        finally:p.unlink()
if __name__=='__main__':unittest.main(verbosity=2)
