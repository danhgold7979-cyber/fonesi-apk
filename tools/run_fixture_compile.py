#!/usr/bin/env python3
"""One compile-only fixture aggregate; no Android installation/execution."""
import hashlib,json,os,pathlib,subprocess,time
from verify_fixture import collect
ROOT=pathlib.Path(__file__).resolve().parents[1]
TASKS=[':storage-fixture:assembleDebug', ':storage-fixture-test:assembleDebug',
       ':storage-fixture:lintDebug', ':storage-fixture-test:lintDebug', ':storage-fixture-test:verifyFixtureDependencies']
def main():
    os.chdir(ROOT)
    source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
    assert source==os.environ['EXPECTED_SOURCE_SHA']
    java=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True)
    assert 'version "17.' in java
    subprocess.run(['python3','tools/check_toolchain.py','--android'],check=True)
    receipt=ROOT/'fixture-compile-receipt.json'
    if receipt.exists(): raise SystemExit('Refusing stale fixture receipt')
    start=time.time()
    command=['bash','gradlew','--no-daemon','--console=plain']+TASKS
    result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (ROOT/'fixture-compile.log').write_text(result.stdout)
    print(result.stdout)
    failures=[];verified=None
    if result.returncode:failures.append('Gradle failed with exit '+str(result.returncode))
    # Retain all bounded manifest/resource/signature observations even when a gate failed.
    try: verified=collect(source,start)
    except Exception as error: failures.append('Fixture verification: '+type(error).__name__+' '+str(error))
    if failures: raise SystemExit('; '.join(failures))
    verified.update({'java':java.strip(),'command':command,'exit':0,
                     'build_log_sha256':hashlib.sha256((ROOT/'fixture-compile.log').read_bytes()).hexdigest()})
    receipt.write_text(json.dumps(verified,indent=2)+'\n')
    print('PASS owned fixture COMPILE only; Android runtime NOT_RUN')
if __name__=='__main__':main()
