"""Owned temporary metadata fixtures; not a real SDK/emulator/runtime check."""
import hashlib
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import android_environment_inventory as inv

class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.base=Path(self.tmp.name);self.root=self.base/'sdk';self.kvm=self.base/'no-kvm'
    def collect(self): return inv.collect(self.root,self.kvm)
    def test_missing_is_observation_not_runtime(self):
        r=self.collect();self.assertFalse(r['sdk_present']);self.assertEqual(r['errors'][0]['error'],'MISSING')
        self.assertEqual(r['ANDROID'],'NOT_RUN');self.assertEqual(r['license_acceptance'],'NOT_PERFORMED')
    def test_metadata_and_license_fingerprints_not_contents(self):
        p=self.root/'system-images/android-36/default/x86_64';p.mkdir(parents=True)
        (p/'source.properties').write_text('Pkg.Revision=2\nAndroidVersion.ApiLevel=36\nSystemImage.Abi=x86_64\nsecret=NEVER_EXPORT\n')
        (p/'package.xml').write_text('<package/>')
        lic=self.root/'licenses';lic.mkdir();(lic/'android-sdk-license').write_bytes(b'PRIVATE_LICENSE_BODY')
        r=self.collect();item=next(x for x in r['packages'] if x['package'].startswith('system-images/'))
        self.assertEqual(item['selected_metadata']['SystemImage.Abi'],'x86_64');self.assertNotIn('NEVER_EXPORT',str(r));self.assertNotIn('PRIVATE_LICENSE_BODY',str(r))
        self.assertEqual(r['licenses'][0]['sha256'],hashlib.sha256(b'PRIVATE_LICENSE_BODY').hexdigest())
    def test_symlink_root_and_ancestor_refused(self):
        actual=self.base/'actual';actual.mkdir();self.root.symlink_to(actual,target_is_directory=True)
        self.assertFalse(self.collect()['sdk_present'])
        nested=actual/'inner';nested.mkdir();self.assertFalse(inv.collect(self.root/'inner',self.kvm)['sdk_present'])
    def test_package_and_file_symlinks_refused(self):
        self.root.mkdir();actual=self.base/'private';actual.mkdir();(self.root/'emulator').symlink_to(actual,target_is_directory=True)
        p=self.root/'platform-tools';p.mkdir();(p/'source.properties').symlink_to(self.base/'missing')
        r=self.collect();self.assertEqual(r['packages'][1]['error'],'NOT_DIRECTORY_OR_SYMLINK')
        self.assertEqual(r['packages'][0]['source.properties']['error'],'NOT_REGULAR_OR_OVERSIZE')
    def test_oversize_invalid_metadata_and_no_execution(self):
        p=self.root/'emulator';p.mkdir(parents=True);(p/'source.properties').write_bytes(b'x'*16385)
        exe=p/'emulator';exe.write_text('DO NOT EXECUTE');exe.chmod(0o700)
        r=self.collect();self.assertTrue(r['executables']['emulator']['executable'])
        self.assertEqual(r['packages'][1]['source.properties']['error'],'NOT_REGULAR_OR_OVERSIZE')
        for data in [b'Pkg.Revision=secret value\n',b'Pkg.Revision=1\nPkg.Revision=2',b'\xff']:
            (p/'source.properties').write_bytes(data);self.assertEqual(self.collect()['packages'][1]['metadata_error'],'INVALID_SELECTED_METADATA')
    def test_entry_budget_and_bad_names(self):
        p=self.root/'licenses';p.mkdir(parents=True)
        (p/'bad name').write_text('hidden');self.assertIn({'group':'licenses','error':'INVALID_NAME'},self.collect()['errors'])
        (p/'bad name').unlink()
        with patch.object(inv,'LIMIT',2):
            for n in ('one','two','three'):(p/n).write_text('x')
            with inv.directory_fd(self.root) as fd:
                self.assertEqual(inv.children(fd,'licenses'),([], 'ENTRY_LIMIT'))
    def test_kvm_regular_file_not_device(self):
        self.kvm.write_text('not device');self.assertEqual(self.collect()['kvm'],{'status':'OBSERVED','character_device':False,'readable':False,'writable':False})
    def test_unreadable_different_from_missing(self):
        import errno
        self.assertEqual(inv.error_code(PermissionError(errno.EACCES,'private path')), 'UNREADABLE')
        self.assertEqual(inv.error_code(FileNotFoundError(errno.ENOENT,'private path')), 'MISSING')
    def test_regular_file_replaced_by_fifo_does_not_block_or_read(self):
        import os
        self.root.mkdir();(self.root/'license').write_bytes(b'owned')
        real_open=os.open
        with inv.directory_fd(self.root) as fd:
            def replace(name,flags,**kwargs):
                if name=='license':
                    os.unlink(name,dir_fd=fd);os.mkfifo(name,dir_fd=fd)
                    self.assertTrue(flags & os.O_NONBLOCK)
                return real_open(name,flags,**kwargs)
            with patch.object(inv.os,'open',side_effect=replace), patch.object(inv.os,'read',side_effect=AssertionError('FIFO read forbidden')):
                info,data=inv.fingerprint(fd,'license',65536)
            self.assertEqual(info['error'],'CHANGED');self.assertIsNone(data)
    def test_child_fd_owned_if_parent_close_raises(self):
        import os,errno
        self.root.mkdir();real_close=os.close;closed=[]
        def failing_close(fd):
            real_close(fd);closed.append(fd)
            if len(closed)==1:raise OSError(errno.EIO,'synthetic close uncertainty')
        with patch.object(inv.os,'close',side_effect=failing_close):
            with self.assertRaises(OSError):
                with inv.directory_fd(self.root): self.fail('must not yield')
        self.assertEqual(len(closed),2);self.assertNotEqual(closed[0],closed[1])
        for fd in closed:
            with self.assertRaises(OSError):os.fstat(fd)
    def test_fixed_script_has_no_process_or_network(self):
        text=Path(inv.__file__).read_text()
        for prohibited in ('subprocess','urllib','requests.','os.system','os.popen','eval(','exec('):self.assertNotIn(prohibited,text)
        self.assertIn('os.O_NOFOLLOW',text);self.assertIn('os.fstat(fd)',text)
    def test_replaced_ancestor_does_not_redirect_retained_fd(self):
        self.root.mkdir();original=self.root/'licenses';original.mkdir();(original/'license').write_bytes(b'owned')
        outside=self.base/'outside';outside.mkdir();(outside/'license').write_bytes(b'private')
        with inv.directory_fd(self.root) as rootfd, inv.directory_fd('licenses',rootfd) as licensedir:
            original.rename(self.root/'retained');original.symlink_to(outside,target_is_directory=True)
            info,data=inv.fingerprint(licensedir,'license',65536)
            self.assertEqual(data,b'owned')
            with self.assertRaises(OSError):
                with inv.directory_fd('licenses',rootfd): pass
            self.assertNotEqual(info['sha256'],hashlib.sha256(b'private').hexdigest())

if __name__ == '__main__':unittest.main(verbosity=2)
