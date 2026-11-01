#!/usr/bin/env python3
"""Inspect packaged semantics; this is COMPILE evidence, never runtime proof."""
import argparse,pathlib,subprocess,xml.etree.ElementTree as ET,hashlib,json,os,re,zipfile,datetime
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

def main():
    p=argparse.ArgumentParser();p.add_argument('apk');args=p.parse_args()
    apk=pathlib.Path(args.apk);sdk=pathlib.Path(os.environ['ANDROID_HOME'])
    analyzer=sdk/'cmdline-tools/latest/bin/apkanalyzer';signer=sdk/'build-tools/35.0.0/apksigner'
    xml=subprocess.check_output([str(analyzer),'manifest','print',str(apk)],text=True)
    validate_manifest(xml)
    resource_dump=subprocess.check_output([str(sdk/'build-tools/35.0.0/aapt2'),'dump','resources',str(apk)],text=True)
    rules_xml=subprocess.check_output([str(analyzer),'resources','xml','--file',RULE_PATH,str(apk)],text=True)
    with zipfile.ZipFile(apk) as archive:
        validate_packaged_rules(xml,resource_dump,rules_xml,archive.namelist())
    signature=subprocess.check_output([str(signer),'verify','--verbose','--print-certs',str(apk)],text=True)
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature or 'Verified using v3 scheme (APK Signature Scheme v3): true' in signature
    source_sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
    expected=os.environ.get('EXPECTED_SOURCE_SHA',source_sha)
    assert source_sha==expected,'artifact receipt source differs from expected SHA'
    print(json.dumps({'application_id':'vn.fonesi.app','version_code':'1','version_name':'0.1.0-foundation','timestamp_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':'python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk','exit':0,'build_pins':{'gradle':'8.14.6','AGP':'8.13.2','minSdk':26,'compileSdk':36,'targetSdk':36,'build_tools':'35.0.0','java':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True).strip()},'source_sha':source_sha,'tier':'COMPILE','artifact':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'manifest':'PASS','backup_rules':'PASS_PACKAGED_STATIC_ONLY','signature':'PASS_DEBUG_ONLY','ANDROID':'NOT_RUN','PROVIDER':'NOT_RUN','PHONE':'NOT_RUN'},indent=2))

if __name__=='__main__':main()
