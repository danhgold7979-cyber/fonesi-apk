#!/usr/bin/env python3
"""Build/test the separate source component; never infer Android guest execution."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from prepare_source_engine import ROOT, TOOLS, DOBBY, DOBBY_COMMIT, NDK, CMAKE


def run(command, log, timeout=900):
    with log.open("w") as output:
        result = subprocess.run([str(arg) for arg in command], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
    if result.returncode:
        print(log.read_text()[-8000:])
        raise SystemExit(result.returncode)


def main():
    subprocess.run([sys.executable, str(ROOT / "scripts/prepare_source_engine.py")], check=True)
    output = ROOT / "engine-source/build"
    reports = output / "reports"
    reports.mkdir(parents=True, exist_ok=True)
    cmake = TOOLS / f"android-sdk/cmake/{CMAKE}/bin/cmake"
    ninja = cmake.parent / "ninja"
    run([cmake, "-S", ROOT / "engine-source/src/main/cpp", "-B", output / "host", "-G", "Ninja",
         f"-DCMAKE_MAKE_PROGRAM={ninja}", "-DCMAKE_BUILD_TYPE=Release", f"-DDOBBY_ROOT={DOBBY}"], reports / "host-configure.log")
    run([cmake, "--build", output / "host", "--target", "fonesi_native_test", "--parallel", "4"], reports / "host-build.log")
    run([output / "host/fonesi_native_test"], reports / "host-test.log", timeout=30)
    print("PASS: actual host libc routing in two isolated test processes; original markers preserved.", flush=True)
    run([ROOT / "scripts/gradle.sh", "-PincludeSourceEngine=true", ":engine-source:assembleDebug", ":engine-source:lintDebug", "--console=plain"], reports / "android-build.log")
    run([ROOT / "scripts/gradle.sh", "-PincludeSourceEngine=true", ":engine-source:testDebugUnitTest", "--console=plain"], reports / "native-libraries-test.log")
    tests = [ET.parse(path).getroot() for path in (output / "test-results/testDebugUnitTest").glob("TEST-*.xml")]
    native_tests = {key: sum(int(result.get(key, "0")) for result in tests) for key in ("tests", "failures", "errors", "skipped")}
    assert tests and native_tests["tests"] > 0 and not any(native_tests[key] for key in ("failures", "errors", "skipped")), native_tests
    archive = output / "outputs/aar/engine-source-debug.aar"
    # The following x86_64 probe build may replace the conventional output filename.
    verified_archive = reports / "engine-source-arm64-verified.aar"
    shutil.copyfile(archive, verified_archive)
    with zipfile.ZipFile(archive) as z:
        native = [entry.filename for entry in z.infolist() if entry.filename.startswith("jni/") and not entry.is_dir()]
        assert native == ["jni/arm64-v8a/libfonesi_runtime.so"], native
        blob = z.read(native[0])
        manifest = ET.fromstring(z.read("AndroidManifest.xml"))
        assert not manifest.findall("uses-permission")
        app = manifest.find("application")
        assert app is None or not list(app)
        for name in ("Dobby.txt", "TINYSTL.txt", "Fonesi-source-runtime.md", "apksig.md"):
            assert len(z.read("assets/licenses/" + name)) > 100
        assert not any(name.endswith((".a", ".apk", ".keystore", ".p12")) for name in z.namelist())
    assert blob[:6] == b"\x7fELF\x02\x01"
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", blob)
    assert header[1:3] == (3, 183)
    segments = [struct.unpack_from("<IIQQQQQQ", blob, header[5] + index * header[9]) for index in range(header[10])]
    loads = [p for p in segments if p[0] == 1]
    assert loads and all(p[7] >= 16384 and (p[3] - p[2]) % 16384 == 0 and p[1] & 3 != 3 for p in loads)
    assert any(p[0] == 0x6474e552 for p in segments), "RELRO missing"
    assert all(p[1] & 1 == 0 for p in segments if p[0] == 0x6474e551), "Executable stack"
    # Inspect the packaged bytes, never a stale intermediate library.
    library = reports / "libfonesi_runtime-verified.so"
    library.write_bytes(blob)
    readelf = TOOLS / f"android-sdk/ndk/{NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
    dynamic = subprocess.check_output([readelf, "-d", library], text=True)
    dependencies = re.findall(r"\(NEEDED\).*\[([^]]+)\]", dynamic)
    assert set(dependencies) <= {"libc.so", "libm.so", "libdl.so", "liblog.so"}, dependencies
    symbols = subprocess.check_output([readelf, "--dyn-syms", "--wide", library], text=True)
    exported = {line.split()[-1].split("@")[0] for line in symbols.splitlines()
                if " FUNC " in line and " GLOBAL " in line and " UND " not in line}
    assert exported == {"JNI_OnLoad", "Java_vn_fonesi_engine_NativeRuntime_configureNative",
                        "Java_vn_fonesi_engine_NativeRuntime_enableNative", "Java_vn_fonesi_engine_NativeRuntime_stateNative"}, exported
    lint = ET.parse(reports / "lint-results-debug.xml").getroot().findall("issue")
    assert not any(item.get("severity") in ("Error", "Fatal") for item in lint)
    report = {"source": DOBBY_COMMIT, "patch_sha256": hashlib.sha256((ROOT / "engine-source/patches/dobby-compat.patch").read_bytes()).hexdigest(),
              "ndk": NDK, "cmake": CMAKE, "host_native_integration_passed": True, "android_aar_built": True,
              "android_native_execution_verified": False, "app_provider_integrated": False,
              "native_library_host_tests": native_tests, "verified_aar": str(verified_archive.relative_to(ROOT)),
              "aar_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(), "aar_bytes": archive.stat().st_size,
              "elf_sha256": hashlib.sha256(blob).hexdigest(), "elf_bytes": len(blob), "load_alignments": [p[7] for p in loads],
              "dependencies": dependencies, "jni_exports": sorted(exported), "lint_warnings": sum(i.get("severity") == "Warning" for i in lint)}
    (reports / "source-engine-verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print("PASS: source-built ARM64 AAR, ELF 16 KB alignment, JNI exports, permissions, licenses and lint.")
    print("NOT VERIFIED: Android guest execution, APK cluster loading, Binder/lifecycle or accounts.")


if __name__ == "__main__":
    main()
