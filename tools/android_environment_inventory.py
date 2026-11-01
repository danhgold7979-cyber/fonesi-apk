"""Bounded descriptor-anchored metadata inventory; no process/network invocation."""
from contextlib import contextmanager
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import stat

SDK = Path('/usr/local/lib/android/sdk')
LIMIT = 256
FIELDS = {'Pkg.Revision', 'AndroidVersion.ApiLevel', 'SystemImage.Abi', 'SystemImage.TagId'}
TOKEN = re.compile(r'[A-Za-z0-9_.;-]{1,128}\Z')


def error_code(error):
    if error.errno == errno.ENOENT: return 'MISSING'
    if error.errno in (errno.ELOOP, errno.ENOTDIR): return 'NOT_DIRECTORY_OR_SYMLINK'
    if error.errno in (errno.EACCES, errno.EPERM): return 'UNREADABLE'
    return 'UNAVAILABLE'


@contextmanager
def directory_fd(path, base_fd=None):
    """Open each component without following links; retain only final descriptor."""
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY) if base_fd is None else os.dup(base_fd)
    try:
        parts = Path(path).parts
        if base_fd is not None and Path(path).is_absolute(): raise ValueError('Invalid relative directory')
        for component in parts:
            if component == '/': continue
            if component in ('.','..'): raise ValueError('Invalid directory component')
            child = os.open(component, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            previous = fd; fd = child
            os.close(previous)
        yield fd
    finally:
        os.close(fd)


def fingerprint(parent_fd, name, limit):
    try:
        before = os.stat(name,dir_fd=parent_fd,follow_symlinks=False)
        if not stat.S_ISREG(before.st_mode) or before.st_size > limit:
            return {'error':'NOT_REGULAR_OR_OVERSIZE'}, None
        fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=parent_fd)
        try:
            opened=os.fstat(fd)
            if not stat.S_ISREG(opened.st_mode) or (opened.st_dev,opened.st_ino)!=(before.st_dev,before.st_ino):
                return {'error':'CHANGED'},None
            data=os.read(fd,limit+1);after=os.fstat(fd)
            if len(data)!=opened.st_size or len(data)>limit or (after.st_size,after.st_mtime_ns)!=(opened.st_size,opened.st_mtime_ns):
                return {'error':'CHANGED_OR_OVERSIZE'},None
            return {'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()},data
        finally: os.close(fd)
    except OSError as error:return {'error':error_code(error)},None


def children(root_fd, relative):
    try:
        with directory_fd(relative,root_fd) as fd, os.scandir(fd) as entries:
            names=[]
            for entry in entries:
                if len(names)==LIMIT:return [],'ENTRY_LIMIT'
                if not TOKEN.fullmatch(entry.name):return [],'INVALID_NAME'
                names.append(entry.name)
            return sorted(names),None
    except OSError as error:return [],error_code(error)


def executable(root_fd,relative):
    try:
        path=Path(relative)
        with directory_fd(path.parent,root_fd) as fd:
            info=os.stat(path.name,dir_fd=fd,follow_symlinks=False)
            regular=stat.S_ISREG(info.st_mode)
            return {'status':'OBSERVED','regular_file':regular,
                    'executable':bool(regular and os.access(path.name,os.X_OK,dir_fd=fd,follow_symlinks=False))}
    except OSError as error:return {'status':error_code(error)}


def collect(root=SDK,kvm=Path('/dev/kvm')):
    result={'schema':'android-environment-inventory-v1','scope':'READ_ONLY_METADATA',
            'sdk_present':False,'packages':[],'licenses':[],'errors':[],
            'ANDROID':'NOT_RUN','license_acceptance':'NOT_PERFORMED',
            'package_license_eligibility':'UNVERIFIED','executables':{}}
    scanned=0
    def scan(fd,path):
        nonlocal scanned
        scanned+=1
        if scanned>LIMIT:raise ValueError('Inventory directory budget exceeded')
        names,error=children(fd,path)
        if error:result['errors'].append({'group':path.split('/')[0],'error':error})
        return names
    try:
        with directory_fd(root) as root_fd:
            result['sdk_present']=True
            candidates=['platform-tools','emulator']
            for group in ('cmdline-tools','platforms','build-tools'):
                candidates += [group+'/'+n for n in scan(root_fd,group)]
            for api in scan(root_fd,'system-images'):
                for tag in scan(root_fd,'system-images/'+api):
                    for abi in scan(root_fd,'system-images/'+api+'/'+tag):
                        candidates.append('system-images/'+api+'/'+tag+'/'+abi)
                        if len(candidates)>LIMIT:raise ValueError('Inventory entry budget exceeded')
            if len(candidates)>LIMIT:raise ValueError('Inventory entry budget exceeded')
            for relative in candidates:
                item={'package':relative}
                try:
                    with directory_fd(relative,root_fd) as fd:
                        for name,limit in [('source.properties',16384),('package.xml',262144)]:
                            item[name],data=fingerprint(fd,name,limit)
                            if name=='source.properties' and data is not None:
                                try:
                                    fields={}
                                    for line in data.decode('utf-8','strict').splitlines():
                                        key,sep,value=line.partition('=');key=key.strip();value=value.strip()
                                        if sep and key in FIELDS:
                                            if key in fields or not TOKEN.fullmatch(value):raise ValueError()
                                            fields[key]=value
                                    item['selected_metadata']=fields
                                except (UnicodeError,ValueError):item['metadata_error']='INVALID_SELECTED_METADATA'
                except OSError as error:item['error']=error_code(error)
                result['packages'].append(item)
            names=scan(root_fd,'licenses')
            if names:
                try:
                    with directory_fd('licenses',root_fd) as fd:
                        for name in names:
                            info,_=fingerprint(fd,name,65536);result['licenses'].append({'name':name,**info})
                except OSError as error:result['errors'].append({'group':'licenses','error':error_code(error)})
            for name,relative in [('adb','platform-tools/adb'),('emulator','emulator/emulator'),('sdkmanager','cmdline-tools/latest/bin/sdkmanager'),('avdmanager','cmdline-tools/latest/bin/avdmanager')]:
                result['executables'][name]=executable(root_fd,relative)
    except OSError as error:result['errors'].append({'group':'sdk_root','error':error_code(error)})
    try:
        with directory_fd(kvm.parent) as fd:
            info=os.stat(kvm.name,dir_fd=fd,follow_symlinks=False);character=stat.S_ISCHR(info.st_mode)
            result['kvm']={'status':'OBSERVED','character_device':character,
                           'readable':bool(character and os.access(kvm.name,os.R_OK,dir_fd=fd,follow_symlinks=False)),
                           'writable':bool(character and os.access(kvm.name,os.W_OK,dir_fd=fd,follow_symlinks=False))}
    except OSError as error:result['kvm']={'status':error_code(error)}
    return result


def main():
    sha=os.environ.get('EXPECTED_SOURCE_SHA','');run=os.environ.get('GITHUB_RUN_ID','')
    if not re.fullmatch('[0-9a-f]{40}',sha) or not re.fullmatch('[0-9]{1,20}',run):raise SystemExit('Invalid source/run binding')
    try:result=collect()
    except (OSError,ValueError):raise SystemExit('Inventory unavailable or budget exceeded') from None
    result.update(source_sha=sha,run_id=run)
    encoded=json.dumps(result,indent=2)+'\n'
    if len(encoded.encode())>262144:raise SystemExit('Inventory output budget exceeded')
    Path('android-environment-inventory.json').write_text(encoded)
    print(encoded,end='')

if __name__=='__main__':main()
