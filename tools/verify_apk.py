#!/usr/bin/env python3
"""Inspect packaged semantics; this is COMPILE evidence, never runtime proof."""
import argparse,pathlib,subprocess,xml.etree.ElementTree as ET,hashlib,json,os,re,zipfile,datetime,sys
def validate_manifest(xml):
    root=ET.fromstring(xml);A='{http://schemas.android.com/apk/res/android}'
    assert root.attrib['package']=='vn.fonesi.app'
    assert root.get(A+'versionCode')=='1' and root.get(A+'versionName')=='0.1.0-foundation'
    assert sorted(child.tag for child in root)==['application','uses-sdk']
    assert not root.findall('uses-permission') and not root.findall('uses-permission-sdk-23')
    uses=root.find('uses-sdk');assert uses is not None
    assert uses.get(A+'minSdkVersion')=='26' and uses.get(A+'targetSdkVersion')=='36'
    app=root.find('application');assert app is not None
    assert app.get(A+'allowBackup')=='false'
    assert app.get(A+'fullBackupContent')=='false'
    assert app.get(A+'dataExtractionRules'), 'missing packaged extraction rules reference'
    assert app.get(A+'debuggable')=='true'
    assert all(child.tag=='activity' for child in app)
    assert not app.findall('service') and not app.findall('receiver') and not app.findall('provider')
    activities=app.findall('activity');assert len(activities)==1 and not app.findall('activity-alias')
    assert activities[0].get(A+'exported')=='true'
    activity_name=activities[0].get(A+'name','')
    if activity_name.startswith('.'):activity_name=root.attrib['package']+activity_name
    elif '.' not in activity_name:activity_name=root.attrib['package']+'.'+activity_name
    assert activity_name=='vn.fonesi.app.MainActivity'
    assert len(activities[0].findall('intent-filter'))==1
    f=activities[0].find('intent-filter')
    assert sorted(child.tag for child in f)==['action','category']
    assert {x.get(A+'name') for x in f.findall('action')}=={'android.intent.action.MAIN'}
    assert {x.get(A+'name') for x in f.findall('category')}=={'android.intent.category.LAUNCHER'}

RULE_PATH = 'res/xml/data_extraction_rules.xml'
RULE_DOMAINS = {'root', 'file', 'database', 'sharedpref', 'external',
                'device_root', 'device_file', 'device_database', 'device_sharedpref'}

def validate_packaged_rules(manifest_xml, resource_dump, rules_xml, members):
    """Fail closed unless the packaged manifest binds the exact unqualified deny-all XML."""
    app = ET.fromstring(manifest_xml).find('application')
    reference = app.get('{http://schemas.android.com/apk/res/android}dataExtractionRules')
    # aapt2 resource table output is parsed narrowly; unknown formatting is a failure,
    # not permission to fall back to checking an unrelated source resource.
    blocks = re.split(r'(?m)^\s*resource ', resource_dump)[1:]
    matches = []
    for block in blocks:
        header, _, body = block.partition('\n')
        match = re.fullmatch(r'(0x[0-9a-fA-F]{8}) (?:vn\.fonesi\.app:)?xml/data_extraction_rules', header.strip())
        if match:
            matches.append((match.group(1).lower(), body))
    assert len(matches) == 1, 'missing/duplicate extraction resource table entry'
    resource_id, body = matches[0]
    assert resource_id.startswith('0x7f'), 'rules must belong to this app resource package'
    assert reference in ('@xml/data_extraction_rules', '@vn.fonesi.app:xml/data_extraction_rules',
                         '@' + resource_id, '@ref/' + resource_id), 'manifest points to another resource'
    # Exactly one default resource value. No version/locale replacement may enable transfer.
    lines = [line.strip() for line in body.splitlines() if line.strip()]
    assert len(lines) == 1, 'unexpected resource variants/metadata; inspect actual binary dump'
    assert re.fullmatch(r'\(\) \(file\) ' + re.escape(RULE_PATH) + r'(?: type=XML)?', lines[0]), 'unexpected extraction resource value'
    assert members.count(RULE_PATH) == 1, 'missing/duplicate packaged rules file'
    assert [name for name in members if re.fullmatch(r'res/xml[^/]*/data_extraction_rules\.xml', name)] == [RULE_PATH], 'qualified rules not accepted'
    root = ET.fromstring(rules_xml)
    assert root.tag == 'data-extraction-rules'
    assert [mode.tag for mode in root] == ['cloud-backup', 'device-transfer']
    for mode in root:
        assert len(mode) == len(RULE_DOMAINS)
        assert all(child.tag == 'exclude' and child.attrib == {'domain':child.get('domain'), 'path':'.'} for child in mode)
        assert {child.get('domain') for child in mode} == RULE_DOMAINS

CATALOG_ASSETS = ('catalog_v1_schema.sql', 'catalog_claim_revision.sql',
                  'catalog_update_existing_style.sql', 'catalog_read_style.sql',
                  'style_columns_3_to_4.sql', 'catalog_page_first.sql',
                  'catalog_page_next.sql', 'catalog_read_meta.sql')

def catalog_source_hashes():
    root = pathlib.Path(__file__).resolve().parents[1] / 'storage/sql'
    assert {p.name for p in root.iterdir()} == set(CATALOG_ASSETS), 'unexpected SQL source asset'
    return {'assets/' + name: hashlib.sha256((root / name).read_bytes()).hexdigest()
            for name in CATALOG_ASSETS}

def validate_catalog_assets(archive):
    expected = catalog_source_hashes()
    names = [name for name in archive.namelist() if name.startswith('assets/')]
    assert len(names) == len(expected) and set(names) == set(expected), 'missing/extra/duplicate SQL assets'
    for name, digest in expected.items():
        assert archive.getinfo(name).file_size <= 4096, 'SQL asset budget exceeded'
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest, 'packaged SQL differs from reviewed source'
    return expected

def main():
    p=argparse.ArgumentParser();p.add_argument('apk');args=p.parse_args()
    apk=pathlib.Path(args.apk);sdk=pathlib.Path(os.environ['ANDROID_HOME'])
    analyzer=sdk/'cmdline-tools/latest/bin/apkanalyzer';signer=sdk/'build-tools/35.0.0/apksigner'
    # Collect every read-only observation before applying independent predicates.
    # This never installs or loads APK code. Diagnostics cannot emit a PASS receipt.
    commands={
        'manifest':[str(analyzer),'manifest','print',str(apk)],
        'resources':[str(sdk/'build-tools/35.0.0/aapt2'),'dump','resources',str(apk)],
        'extraction_rules':[str(analyzer),'resources','xml','--file',RULE_PATH,str(apk)],
        'signature':[str(signer),'verify','--verbose','--print-certs',str(apk)],
    }
    observations={}; failures=[]
    for name,command in commands.items():
        try:
            process=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            observations[name]=process.stdout
            print('=== APK '+name+' diagnostic; exit='+str(process.returncode)+' ===',file=sys.stderr)
            print(process.stdout[:200000],file=sys.stderr)
            print(process.stderr[:200000],file=sys.stderr)
            if process.returncode or len(process.stdout)>200000 or len(process.stderr)>200000:
                failures.append(name+': command failed or diagnostic bound exceeded')
        except OSError as error:
            observations[name]='';failures.append(name+': '+str(error))
    xml=observations['manifest'];resource_dump=observations['resources']
    rules_xml=observations['extraction_rules'];signature=observations['signature']
    try:validate_manifest(xml)
    except (AssertionError,ET.ParseError,KeyError,AttributeError) as error:failures.append('manifest predicate: '+str(error))
    sql_assets = None
    try:
        with zipfile.ZipFile(apk) as archive:
            sql_assets = validate_catalog_assets(archive)
            validate_packaged_rules(xml,resource_dump,rules_xml,archive.namelist())
    except (AssertionError,ET.ParseError,KeyError,ValueError,AttributeError,OSError,zipfile.BadZipFile) as error:
        failures.append('packaged rules predicate: '+str(error))
    if not ('Verified using v2 scheme (APK Signature Scheme v2): true' in signature or 'Verified using v3 scheme (APK Signature Scheme v3): true' in signature):
        failures.append('signature predicate: v2/v3 not verified')
    if failures:raise SystemExit('APK verification failed: '+'; '.join(failures))
    source_sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
    expected=os.environ.get('EXPECTED_SOURCE_SHA',source_sha)
    assert source_sha==expected,'artifact receipt source differs from expected SHA'
    print(json.dumps({'application_id':'vn.fonesi.app','version_code':'1','version_name':'0.1.0-foundation','timestamp_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':'python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk','exit':0,'build_pins':{'gradle':'8.14.6','AGP':'8.13.2','minSdk':26,'compileSdk':36,'targetSdk':36,'build_tools':'35.0.0','java':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True).strip()},'source_sha':source_sha,'tier':'COMPILE','artifact':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'manifest':'PASS','backup_rules':'PASS_PACKAGED_STATIC_ONLY','catalog_sql_assets':sql_assets,'signature':'PASS_DEBUG_ONLY','ANDROID':'NOT_RUN','PROVIDER':'NOT_RUN','PHONE':'NOT_RUN'},indent=2))

if __name__=='__main__':main()
