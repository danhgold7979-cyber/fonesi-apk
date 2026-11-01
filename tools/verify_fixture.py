#!/usr/bin/env python3
"""Inspect owned platform fixture APKs. Never install/load/execute APK code."""
import hashlib,json,os,pathlib,re,struct,subprocess,xml.etree.ElementTree as ET,zipfile
from verify_apk import validate_catalog_assets, validate_packaged_rules, RULE_PATH
ROOT=pathlib.Path(__file__).resolve().parents[1]
TARGET='vn.fonesi.fixture.storage'
TEST=TARGET+'.test'
RUNNER=TARGET+'.StorageInstrumentation'
A='{http://schemas.android.com/apk/res/android}'
def validate_manifest(xml, test):
    root=ET.fromstring(xml)
    assert root.get('package')==(TEST if test else TARGET)
    assert A+'sharedUserId' not in root.attrib
    assert sorted(x.tag for x in root)==(['application','instrumentation','uses-sdk'] if test else ['application','uses-sdk'])
    sdk=root.find('uses-sdk');assert sdk.get(A+'minSdkVersion')=='26' and sdk.get(A+'targetSdkVersion')=='36'
    app=root.find('application');assert not list(app)
    for key,value in [('debuggable','true'),('testOnly','true'),('allowBackup','false'),('fullBackupContent','false')]:
        assert app.get(A+key)==value
    assert app.get(A+'dataExtractionRules')
    assert app.get(A+'name') in (None,'android.app.Application')
    assert app.get(A+'process') in (None,TEST if test else TARGET)
    if test:
        entry=root.find('instrumentation')
        assert entry.get(A+'name')==RUNNER and entry.get(A+'targetPackage')==TARGET
        assert entry.get(A+'targetProcesses') in (None,TARGET)
        assert entry.get(A+'functionalTest') in (None,'false')
        assert entry.get(A+'handleProfiling') in (None,'false')
        assert set(entry.attrib)<= {A+x for x in ('name','targetPackage','targetProcesses','functionalTest','handleProfiling','label')}

def validate_lint(xml,module):
    root=ET.fromstring(xml);assert root.tag=='issues'
    assert module in ('storage-fixture','storage-fixture-test')
    if module=='storage-fixture-test':
        assert not list(root), 'Every instrumentation-module lint diagnostic is fatal'
        return None
    issues=list(root);assert len(issues)==1
    issue=issues[0];assert issue.tag=='issue' and issue.get('id')=='OldTargetApi' and issue.get('severity')=='Hint'
    assert issue.get('message','').startswith('Not targeting the latest versions of Android;')
    locations=list(issue);assert len(locations)==1 and locations[0].tag=='location'
    path=pathlib.Path(locations[0].get('file',''));path=path if path.is_absolute() else ROOT/path
    assert path.resolve()==(ROOT/module/'build.gradle').resolve()
    line=int(locations[0].get('line','0'));lines=path.read_text().splitlines()
    assert 0<line<=len(lines) and lines[line-1].strip()=='targetSdk 36'
    return {'id':'OldTargetApi','severity':'Hint','file':module+'/build.gradle','line':line,'message':issue.get('message')}


def dex_classes(data):
    # Read actual DEX class_def/type/string tables, never string-search evidence.
    assert data[:4] == b'dex\n' and len(data) >= 112
    def word(offset):
        assert 0 <= offset <= len(data)-4
        return struct.unpack_from('<I', data, offset)[0]
    string_count,string_offset=word(56),word(60)
    type_count,type_offset=word(64),word(68)
    class_count,class_offset=word(96),word(100)
    assert string_offset+4*string_count<=len(data) and type_offset+4*type_count<=len(data)
    assert class_offset+32*class_count<=len(data)
    result=set()
    for number in range(class_count):
        index=word(class_offset+number*32);assert index<type_count
        string_index=word(type_offset+index*4);assert string_index<string_count
        offset=word(string_offset+string_index*4)
        for _ in range(5):
            assert offset<len(data)
            byte=data[offset];offset+=1
            if not byte&128:break
        else:raise AssertionError('Invalid DEX string length')
        end=data.index(0,offset)
        # Expected class descriptors are ASCII; other descriptors can be safely ignored.
        result.add(data[offset:end].decode('utf-8',errors='strict'))
    return result


def validate_class_set(classes,test):
    expected={RUNNER.replace('.','/')} if test else {
        'vn/fonesi/app/storage/'+name for name in ['StorageFixture','ExistingStyleWrite','OwnedCatalogWriter',
            'CatalogPageRead','ExistingCatalogRead','CatalogPath','CatalogProcessGate']}
    assert {'L'+name+';' for name in expected} <= classes
    assert 'Lvn/fonesi/app/MainActivity;' not in classes
    if test:
        assert not any(c.startswith(('Lvn/fonesi/app/storage/','Lvn/fonesi/apk/foundation/')) for c in classes)
        assert 'Lvn/fonesi/fixture/storage/BuildConfig;' not in classes


def source_hashes():
    paths=set()
    for folder in ['core/src/main/java','app/src/main/java/vn/fonesi/app/storage','storage/sql','storage-fixture/src','storage-fixture-test/src']:
        paths.update(p for p in (ROOT/folder).rglob('*') if p.is_file())
    paths.update(ROOT/p for p in ['storage-fixture/build.gradle','storage-fixture-test/build.gradle','settings.gradle','build.gradle','gradle.properties'])
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}

def merger_provenance(not_before):
    result={}
    for module,kind in [('storage-fixture','owned_target'),('storage-fixture-test','instrumentation')]:
        directory=ROOT/module/'build/outputs/logs'
        reports=sorted(directory.glob('manifest-merger-*-report.txt'))
        assert len(reports)==1 and reports[0].name=='manifest-merger-debug-report.txt', 'Missing/extra fixture merger report'
        report=reports[0]
        assert report.stat().st_mtime>=not_before and 0<report.stat().st_size<=200000
        text=report.read_text();assert text.startswith('-- Merging decision tree log ---')
        expected=(ROOT/module/'src/main/AndroidManifest.xml').resolve()
        origins={}
        for line in text.splitlines():
            match=re.fullmatch(r'\s*([A-Z_]+) from (.+)',line)
            if not match:
                assert ' from ' not in line, 'Unknown merger-origin syntax'
                continue
            raw=match.group(2)
            reason=' reason: use-sdk injection requested'
            if raw.endswith(reason):
                assert match.group(1)=='INJECTED'
                raw=raw[:-len(reason)]
            raw=re.sub(r':\d+(?::\d+)?(?:-\d+(?::\d+)?)?$', '',raw)
            origin=pathlib.Path(raw)
            if not origin.is_absolute():origin=ROOT/origin
            origin=origin.resolve()
            assert origin==expected, 'Unreviewed manifest origin: '+str(origin)
            assert origin.is_file()
            origins[str(origin.relative_to(ROOT))]=hashlib.sha256(origin.read_bytes()).hexdigest()
        assert origins, 'No source origin in merger report'
        result[kind]={'report':str(report.relative_to(ROOT)),
                      'sha256':hashlib.sha256(report.read_bytes()).hexdigest(),'origins':origins}
    return result

TARGET_JAR='storage-fixture/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar'
def validate_dependencies(deps,source_sha,jar_sha):
    assert deps=={'source_sha':source_sha,'configurations':{'targetRuntime':[],'testRuntime':[],
        'testCompile':[TARGET_JAR]},'target_jar_sha256':jar_sha}

def target_jar_provenance(not_before):
    jar=ROOT/TARGET_JAR
    assert jar.stat().st_mtime>=not_before and 0<jar.stat().st_size<=2097152
    with zipfile.ZipFile(jar) as z:
        names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
        assert all(not n.startswith('/') and '..' not in pathlib.Path(n).parts for n in names)
        assert 'vn/fonesi/app/storage/StorageFixture.class' in names
        assert 'vn/fonesi/app/MainActivity.class' not in names
        members={n:hashlib.sha256(z.read(n)).hexdigest() for n in names if not n.endswith('/')}
    return {'path':TARGET_JAR,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'members':members}


def signature_digest(signature):
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
    assert 'CN=Android Debug' in signature
    digests=re.findall(r'(?m)^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})$',signature)
    assert len(digests)==1 and 'Signer #2' not in signature
    return digests[0].lower()

def validate_source_binding(generated,source_sha,not_before):
    assert 'String SOURCE_SHA = "'+source_sha+'";' in generated.read_text()
    assert generated.stat().st_mtime>=not_before


def verify_pair(source_sha, not_before, observations):
    reports=ROOT/'storage-fixture/build/reports'
    depfile=ROOT/'storage-fixture-test/build/reports/fixture-dependencies.json'
    deps=json.loads(depfile.read_text())
    jar=target_jar_provenance(not_before)
    validate_dependencies(deps,source_sha,jar['sha256'])
    provenance=merger_provenance(not_before)
    assert depfile.stat().st_mtime>=not_before
    lint_files=[ROOT/module/'build/reports'/('lint-results-debug.'+suffix)
                for module in ['storage-fixture','storage-fixture-test'] for suffix in ['xml','txt','html']]
    for path in lint_files:
        assert path.is_file() and path.stat().st_size and path.stat().st_mtime>=not_before
    notice=None
    for report in lint_files:
        module=report.relative_to(ROOT).parts[0]
        if module=='storage-fixture':assert 'OldTargetApi' in report.read_text()
        if report.suffix=='.xml':
            result=validate_lint(report.read_text(),module)
            if result is not None:notice=result
    receipts=[];certificates=[]
    for test,apk,observed in observations:
        assert apk.stat().st_mtime>=not_before
        validate_manifest(observed['manifest'],test)
        signature=observed['signature']
        certificates.append(signature_digest(signature))
        with zipfile.ZipFile(apk) as z:
            assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
            namespace=TEST if test else TARGET
            # Reuse strict nine-domain/default-resource predicate with exact verified namespace remapping.
            validate_packaged_rules(observed['manifest'].replace('@'+namespace+':','@vn.fonesi.app:'),
                observed['resources'].replace(namespace+':xml/','vn.fonesi.app:xml/'),observed['rules'],z.namelist())
            classes=set()
            for member in z.namelist():
                if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex',member):classes.update(dex_classes(z.read(member)))
            validate_class_set(classes,test)
            assets={} if test else validate_catalog_assets(z)
            if test:assert not [n for n in z.namelist() if n.startswith('assets/')]
        receipts.append({'kind':'instrumentation' if test else 'owned_target','package':namespace,
                         'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'assets':assets})
    assert certificates[0]==certificates[1]
    generated=ROOT/'storage-fixture/build/generated/source/buildConfig/debug/vn/fonesi/fixture/storage/BuildConfig.java'
    validate_source_binding(generated,source_sha,not_before)
    return {'source_sha':source_sha,'tier':'COMPILE','scope':'OWNED_STORAGE_FIXTURE_ONLY','result':'PASS',
            'apks':receipts,'source_inputs':source_hashes(),'same_debug_certificate':certificates[0],
            'dependencies':deps,'target_compile_jar':jar,'merger_provenance':provenance,'lint':'ONE_RECORDED_TARGET36_HINT_TARGET_ONLY_TEST_ZERO_ISSUES','notice':notice,'release_acceptance':False,
            'lint_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in lint_files},
            'ANDROID':'NOT_RUN','PROVIDER':'NOT_RUN','PHONE':'NOT_RUN','accepted_tasks_added':[]}

def collect(source_sha,not_before):
    sdk=pathlib.Path(os.environ['ANDROID_HOME']);diagnostics=ROOT/'fixture-diagnostics';diagnostics.mkdir(exist_ok=True)
    observations=[];failures=[]
    for test,name in [(False,'storage-fixture/build/outputs/apk/debug/storage-fixture-debug.apk'),
                       (True,'storage-fixture-test/build/outputs/apk/debug/storage-fixture-test-debug.apk')]:
        apk=ROOT/name;observed={};kind='test' if test else 'target'
        commands={'manifest':[str(sdk/'cmdline-tools/latest/bin/apkanalyzer'),'manifest','print',str(apk)],
                  'resources':[str(sdk/'build-tools/35.0.0/aapt2'),'dump','resources',str(apk)],
                  'rules':[str(sdk/'cmdline-tools/latest/bin/apkanalyzer'),'resources','xml','--file',RULE_PATH,str(apk)],
                  'signature':[str(sdk/'build-tools/35.0.0/apksigner'),'verify','--verbose','--print-certs',str(apk)]}
        for key,command in commands.items():
            try:
                result=subprocess.run(command,capture_output=True,text=True,timeout=120)
                (diagnostics/(kind+'-'+key+'.txt')).write_text(result.stdout+'\nSTDERR\n'+result.stderr)
                observed[key]=result.stdout
                if result.returncode or len(result.stdout)>200000 or len(result.stderr)>200000:failures.append(kind+'-'+key)
            except (OSError,subprocess.TimeoutExpired): failures.append(kind+'-'+key);observed[key]=''
        observations.append((test,apk,observed))
    if failures:raise ValueError('Fixture observations failed: '+','.join(failures))
    return verify_pair(source_sha,not_before,observations)
