#!/usr/bin/env python3
"""Inspect packaged semantics; this is COMPILE evidence, never runtime proof."""
import argparse,pathlib,subprocess,xml.etree.ElementTree as ET,hashlib,json,os
def validate_manifest(xml):
    root=ET.fromstring(xml);A='{http://schemas.android.com/apk/res/android}'
    assert root.attrib['package']=='vn.fonesi.app'
    assert sorted(child.tag for child in root)==['application','uses-sdk']
    assert not root.findall('uses-permission') and not root.findall('uses-permission-sdk-23')
    uses=root.find('uses-sdk');assert uses is not None
    assert uses.get(A+'minSdkVersion')=='26' and uses.get(A+'targetSdkVersion')=='36'
    app=root.find('application');assert app is not None
    assert app.get(A+'allowBackup')=='false'
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

def main():
    p=argparse.ArgumentParser();p.add_argument('apk');args=p.parse_args()
    apk=pathlib.Path(args.apk);sdk=pathlib.Path(os.environ['ANDROID_HOME'])
    analyzer=sdk/'cmdline-tools/latest/bin/apkanalyzer';signer=sdk/'build-tools/35.0.0/apksigner'
    xml=subprocess.check_output([str(analyzer),'manifest','print',str(apk)],text=True)
    validate_manifest(xml)
    signature=subprocess.check_output([str(signer),'verify','--verbose','--print-certs',str(apk)],text=True)
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature or 'Verified using v3 scheme (APK Signature Scheme v3): true' in signature
    source_sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
    expected=os.environ.get('EXPECTED_SOURCE_SHA',source_sha)
    assert source_sha==expected,'artifact receipt source differs from expected SHA'
    print(json.dumps({'source_sha':source_sha,'tier':'COMPILE','artifact':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'manifest':'PASS','signature':'PASS_DEBUG_ONLY','ANDROID':'NOT_RUN','PROVIDER':'NOT_RUN','PHONE':'NOT_RUN'},indent=2))

if __name__=='__main__':main()
