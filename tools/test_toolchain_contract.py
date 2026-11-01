"""Synthetic preflight fixtures, never SDK installation or licensing."""
import pathlib,tempfile,unittest
from check_toolchain import validate_sdk
class SdkPredicateTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='apk-sdk-contract-');self.root=pathlib.Path(self.tmp.name)
        paths=['platforms/android-36/'+n for n in ('android.jar','source.properties','package.xml')]
        paths+=['build-tools/35.0.0/'+n for n in ('source.properties','package.xml','aapt2','apksigner','aidl','d8','zipalign','lib/apksigner.jar','lib/d8.jar')]
        for name in paths:
            p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('synthetic not executable')
        (self.root/'platforms/android-36/source.properties').write_text('AndroidVersion.ApiLevel=36\n')
        (self.root/'build-tools/35.0.0/source.properties').write_text('Pkg.Revision=35.0.0\n')
    def tearDown(self):self.tmp.cleanup()
    def test_present_expected(self):validate_sdk(self.root)
    def test_missing_component(self):
        (self.root/'build-tools/35.0.0/lib/d8.jar').unlink()
        with self.assertRaises(ValueError):validate_sdk(self.root)
    def test_wrong_platform(self):
        (self.root/'platforms/android-36/source.properties').write_text('AndroidVersion.ApiLevel=35\n')
        with self.assertRaises(ValueError):validate_sdk(self.root)
    def test_wrong_build_tools(self):
        (self.root/'build-tools/35.0.0/source.properties').write_text('Pkg.Revision=36.0.0\n')
        with self.assertRaises(ValueError):validate_sdk(self.root)
    def test_missing_identity(self):
        (self.root/'platforms/android-36/source.properties').write_text('# no identity\n')
        with self.assertRaises(ValueError):validate_sdk(self.root)
if __name__=='__main__':unittest.main(verbosity=2)
