"""Owned synthetic manifest/diagnostic negatives, not actual packaged proof."""
import copy,json,os,struct,subprocess,tempfile,unittest,xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch
from verify_fixture import validate_manifest,validate_lint,TARGET,TEST,RUNNER,A,ROOT

def manifest(test):
    r=ET.Element('manifest',{'package':TEST if test else TARGET})
    ET.SubElement(r,'uses-sdk',{A+'minSdkVersion':'26',A+'targetSdkVersion':'36'})
    ET.SubElement(r,'application',{A+'debuggable':'true',A+'testOnly':'true',A+'allowBackup':'false',
        A+'fullBackupContent':'false',A+'dataExtractionRules':'@xml/data_extraction_rules'})
    if test:ET.SubElement(r,'instrumentation',{A+'name':RUNNER,A+'targetPackage':TARGET})
    return r
class FixtureVerifierTest(unittest.TestCase):
    def test_exact_synthetic_pair(self):
        for test in (False,True):validate_manifest(ET.tostring(manifest(test)),test)
    def test_permission_queries_components_shared_uid_rejected(self):
        for test in (False,True):
            for node in ['uses-permission','queries','instrumentation']:
                r=manifest(test);ET.SubElement(r,node)
                with self.assertRaises(AssertionError):validate_manifest(ET.tostring(r),test)
            for node in ['activity','provider','receiver','service','meta-data']:
                r=manifest(test);ET.SubElement(r.find('application'),node)
                with self.assertRaises(AssertionError):validate_manifest(ET.tostring(r),test)
            r=manifest(test);r.set(A+'sharedUserId','android.uid.system')
            with self.assertRaises(AssertionError):validate_manifest(ET.tostring(r),test)
    def test_production_or_wildcard_target_rejected(self):
        for key,value in [('targetPackage','vn.fonesi.app'),('targetProcesses','*'),('name','other.Runner')]:
            r=manifest(True);r.find('instrumentation').set(A+key,value)
            with self.assertRaises(AssertionError):validate_manifest(ET.tostring(r),True)
    def test_non_debug_backup_or_missing_test_only_rejected(self):
        for key in ['debuggable','testOnly','allowBackup','fullBackupContent','dataExtractionRules']:
            r=manifest(True);r.find('application').attrib.pop(A+key)
            with self.assertRaises(AssertionError):validate_manifest(ET.tostring(r),True)
    def test_lint_exact_notice_and_all_other_issues_fatal(self):
        path=ROOT/'storage-fixture/build.gradle'
        line=next(i+1 for i,s in enumerate(path.read_text().splitlines()) if s.strip()=='targetSdk 36')
        r=ET.Element('issues');issue=ET.SubElement(r,'issue',{'id':'OldTargetApi','severity':'Hint','message':'Not targeting the latest versions of Android; expected baseline'})
        ET.SubElement(issue,'location',{'file':str(path),'line':str(line)})
        validate_lint(ET.tostring(r))
        for field,value in [('id','NewApi'),('severity','Warning'),('severity','Information')]:
            bad=copy.deepcopy(r);bad[0].set(field,value)
            with self.assertRaises(AssertionError):validate_lint(ET.tostring(bad))
        for count in [0,2]:
            bad=ET.Element('issues')
            for _ in range(count):bad.append(copy.deepcopy(issue))
            with self.assertRaises(AssertionError):validate_lint(ET.tostring(bad))
        bad=copy.deepcopy(r);bad[0][0].set('file',str(ROOT/'app/build.gradle'))
        with self.assertRaises(AssertionError):validate_lint(ET.tostring(bad))
    def test_compile_tasks_no_runtime_actions_and_mandatory_host(self):
        from run_fixture_compile import TASKS
        self.assertEqual(TASKS,[':storage-fixture:assembleDebug',':storage-fixture:assembleDebugAndroidTest',
            ':storage-fixture:lintDebug',':storage-fixture:verifyFixtureDependencies'])
        from run_host_checks import COMMANDS
        for script in ['test_platform_fixture.py','test_fixture_verifier.py']:
            self.assertEqual(COMMANDS.count(['python3','tools/'+script]),1)
    def test_merger_missing_stale_and_unknown_origin_fail(self):
        import verify_fixture as verifier
        with tempfile.TemporaryDirectory() as directory, patch.object(verifier,'ROOT',Path(directory)):
            root=Path(directory);reports=root/'storage-fixture/build/outputs/logs';reports.mkdir(parents=True)
            for name,kind in [('debug','main'),('debug-androidTest','androidTest')]:
                origin=root/'storage-fixture/src'/kind/'AndroidManifest.xml';origin.parent.mkdir(parents=True);origin.write_text('<manifest/>')
                (reports/('manifest-merger-'+name+'-report.txt')).write_text('-- Merging decision tree log ---\nmanifest\nADDED from '+str(origin)+':1:1-2:4\n')
            self.assertEqual(len(verifier.merger_provenance(1)),2)
            target=reports/'manifest-merger-debug-report.txt';good=target.read_text()
            target.write_text(good.replace('/src/main/','/unreviewed-generated/'))
            with self.assertRaises(AssertionError):verifier.merger_provenance(1)
            target.write_text(good);os.utime(target,(0,0))
            with self.assertRaises(AssertionError):verifier.merger_provenance(1)
            target.unlink()
            with self.assertRaises(AssertionError):verifier.merger_provenance(1)
    def test_dependencies_exact_source_and_empty_closure(self):
        from verify_fixture import validate_dependencies
        good={'source_sha':'a'*40,'configurations':{'debugRuntimeClasspath':[],'debugAndroidTestRuntimeClasspath':[]}}
        validate_dependencies(good,'a'*40)
        for key in good['configurations']:
            bad=copy.deepcopy(good);bad['configurations'][key]=['unreviewed.aar']
            with self.assertRaises(AssertionError):validate_dependencies(bad,'a'*40)
        with self.assertRaises(AssertionError):validate_dependencies(good,'b'*40)
        with self.assertRaises(AssertionError):validate_dependencies({},'a'*40)
    def test_signer_debug_verified_single_and_digest(self):
        from verify_fixture import signature_digest
        good='Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate DN: CN=Android Debug\nSigner #1 certificate SHA-256 digest: '+'a'*64+'\n'
        self.assertEqual(signature_digest(good),'a'*64)
        for bad in [good.replace(': true',': false'),good.replace('Android Debug','Other'),good+'Signer #2 certificate DN: CN=Other',good*2]:
            with self.assertRaises(AssertionError):signature_digest(bad)
    def test_generated_source_binding_wrong_missing_stale(self):
        from verify_fixture import validate_source_binding
        with tempfile.TemporaryDirectory() as directory:
            generated=Path(directory)/'BuildConfig.java'
            with self.assertRaises(FileNotFoundError):validate_source_binding(generated,'a'*40,1)
            generated.write_text('String SOURCE_SHA = "'+'a'*40+'";')
            validate_source_binding(generated,'a'*40,1)
            with self.assertRaises(AssertionError):validate_source_binding(generated,'b'*40,1)
            os.utime(generated,(0,0))
            with self.assertRaises(AssertionError):validate_source_binding(generated,'a'*40,1)
    def test_dex_requires_actual_class_def_not_descriptor_string(self):
        from verify_fixture import dex_classes
        descriptor=b'Lowned/Fixture;'
        data=bytearray(160+len(descriptor)+2);data[:8]=b'dex\n035\0'
        for offset,value in [(56,1),(60,112),(64,1),(68,116),(96,1),(100,120),(112,160),(116,0),(120,0)]:struct.pack_into('<I',data,offset,value)
        data[160]=len(descriptor);data[161:161+len(descriptor)]=descriptor
        self.assertEqual(dex_classes(bytes(data)),{'Lowned/Fixture;'})
        struct.pack_into('<I',data,96,0)
        self.assertEqual(dex_classes(bytes(data)),set())
        with self.assertRaises(AssertionError):dex_classes(b'not dex')
    def test_gradle_or_verifier_failure_cannot_emit_pass_receipt(self):
        import run_fixture_compile as aggregate
        for gradle_code,verification_error in [(1,None),(0,ValueError('owned failure'))]:
            with tempfile.TemporaryDirectory() as directory, patch.object(aggregate,'ROOT',Path(directory)), \
                 patch.object(aggregate.os,'chdir'), patch.dict(os.environ,{'EXPECTED_SOURCE_SHA':'a'*40}), \
                 patch.object(aggregate.subprocess,'check_output',side_effect=['a'*40+'\n','openjdk version "17.0.21"']), \
                 patch.object(aggregate.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0),subprocess.CompletedProcess([],gradle_code,'owned log')]), \
                 patch.object(aggregate,'collect',side_effect=verification_error,return_value={}) as collect, patch('builtins.print'):
                with self.assertRaises(SystemExit):aggregate.main()
                collect.assert_called_once()
                self.assertFalse((Path(directory)/'fixture-compile-receipt.json').exists())
                self.assertEqual((Path(directory)/'fixture-compile.log').read_text(),'owned log')
if __name__=='__main__':unittest.main(verbosity=2)
