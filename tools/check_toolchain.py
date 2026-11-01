#!/usr/bin/env python3
"""Verify trusted infrastructure bytes before invoking the wrapper."""
import hashlib,pathlib,os,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
EXPECTED={
 'gradle/wrapper/gradle-wrapper.properties':'934c2b1124fd8837d8937c30867db510a151841676265c13d5a9510eda7effb5',
 'gradlew':'b187b4c52e749f5760afdd6fadc31b2a98ad35fb249bf0dff03b72650f320409',
 'gradlew.bat':'1d297e00bd21de3ace22b4d7f2de1f9dfa858883d66bbf7c1ccbecccec8f4f3b',
 'gradle/wrapper/gradle-wrapper.jar':'ad16bca46cb71bb5887c546860d93992aeee35c1fdaf4a242b0a612375d937e0'}
for path,digest in EXPECTED.items():
    if hashlib.sha256((ROOT/path).read_bytes()).hexdigest()!=digest:raise SystemExit('Unverified toolchain file: '+path)
def properties(path):
    result={}
    for line in path.read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key,value=line.split('=',1);result[key.strip()]=value.strip()
    return result

def validate_sdk(sdk):
    sdk=pathlib.Path(sdk)
    required=('platforms/android-36/android.jar','platforms/android-36/source.properties',
              'platforms/android-36/package.xml','build-tools/35.0.0/source.properties',
              'build-tools/35.0.0/package.xml','build-tools/35.0.0/aapt2',
              'build-tools/35.0.0/apksigner','build-tools/35.0.0/aidl',
              'build-tools/35.0.0/d8','build-tools/35.0.0/zipalign',
              'build-tools/35.0.0/lib/apksigner.jar','build-tools/35.0.0/lib/d8.jar')
    for item in required:
        if not (sdk/item).is_file():raise ValueError('required preinstalled SDK component missing: '+item)
    if properties(sdk/'platforms/android-36/source.properties').get('AndroidVersion.ApiLevel')!='36':
        raise ValueError('preinstalled platform identity differs from API36')
    if properties(sdk/'build-tools/35.0.0/source.properties').get('Pkg.Revision')!='35.0.0':
        raise ValueError('preinstalled Build Tools identity differs from35.0.0')

if '--android' in sys.argv:
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:raise SystemExit('COMPILE NOT RUN: existing authorized Android SDK path missing; no licenses accepted by this script')
    try:validate_sdk(sdk)
    except ValueError as error:raise SystemExit('COMPILE NOT RUN: '+str(error))
print('PASS pinned Gradle infrastructure'+(' and required existing SDK identities/components' if '--android' in sys.argv else ''))
