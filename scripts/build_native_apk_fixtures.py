#!/usr/bin/env python3
"""Build/sign real NDK ELF APK fixtures for the opt-in source component only."""
import argparse
from pathlib import Path
import subprocess
import struct
import sys
import zipfile
from prepare_source_engine import NDK


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    subprocess.run([sys.executable, str(Path(__file__).with_name("build_apk_audit_fixtures.py")),
                    "--output", str(output), "--sdk", str(args.sdk), "--java-home", str(args.java_home)], check=True)
    toolchain = args.sdk / f"ndk/{NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin"

    def library(name, target="aarch64", page=16384, value=7):
        compiler = toolchain / ("aarch64-linux-android26-clang" if target == "aarch64" else "x86_64-linux-android26-clang")
        result = output / (name + ".so")
        subprocess.run([str(compiler), "-x", "c", "-", "-shared", "-fPIC", f"-Wl,-z,max-page-size={page}",
                        "-Wl,--build-id=none", "-Wl,-soname,libfixture.so", "-o", str(result)],
                       input=f"int fonesi_fixture_value(void) {{ return {value}; }}\n", text=True, check=True)
        return result.read_bytes()

    arm = library("arm16")
    x86 = library("x86", target="x86_64")
    changed = library("different", value=8)
    four = library("arm4", page=4096)

    def apk(name, entries, manifest="main-disguised"):
        unsigned = output / (name + "-unsigned.apk")
        with zipfile.ZipFile(output / manifest / "unsigned.apk") as base, zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as destination:
            for entry in base.infolist(): destination.writestr(entry, base.read(entry))
            for path, data in entries: destination.writestr(path, data)
        subprocess.run([str(args.sdk / "build-tools/36.0.0/apksigner"), "sign", "--ks", str(output / "primary.p12"),
            "--ks-pass", "pass:android", "--ks-key-alias", "fixture", "--v1-signing-enabled", "false",
            "--v2-signing-enabled", "true", "--v3-signing-enabled", "true", "--out", str(output / (name + ".apk")), str(unsigned)],
            check=True, stdout=subprocess.DEVNULL)

    apk("native-base", [("lib/arm64-v8a/libpayload.so", arm), ("lib/x86_64/libpayload.so", x86)])
    apk("native-feature", [("lib/arm64-v8a/libfeature.so", changed)], manifest="base")
    apk("native-same-config", [("lib/arm64-v8a/libpayload.so", arm)], manifest="base-config")
    apk("native-conflict-config", [("lib/arm64-v8a/libpayload.so", changed)], manifest="base-config")
    apk("native-four-k", [("lib/arm64-v8a/libpayload.so", four)])
    apk("native-wrong-machine", [("lib/arm64-v8a/libpayload.so", x86)])
    apk("native-bad-elf", [("lib/arm64-v8a/libpayload.so", b"this is not an ELF file")])
    apk("native-escape", [("lib/arm64-v8a/../outside.so", arm)])
    apk("native-x86-only", [("lib/x86_64/libpayload.so", x86)])
    # Negative fixtures start from real ELF, but deliberately violate program-header semantics.
    def broken_header(name, overflow=False):
        data = bytearray(arm)
        phoff = struct.unpack_from("<Q", data, 32)[0]
        count = struct.unpack_from("<H", data, 56)[0]
        first_load = next(phoff + i * 56 for i in range(count) if struct.unpack_from("<I", data, phoff + i * 56)[0] == 1)
        if overflow:
            struct.pack_into("<Q", data, first_load + 16, (1 << 63) - 16384)
            struct.pack_into("<Q", data, first_load + 40, 1 << 20)
        else:
            address = struct.unpack_from("<Q", data, first_load + 16)[0]
            struct.pack_into("<Q", data, first_load + 16, address + 4096)
        apk(name, [("lib/arm64-v8a/libpayload.so", data)])
    broken_header("native-misaligned-segment")
    broken_header("native-overflow-segment", overflow=True)
    print("Built signed native APK fixtures with real ARM64/x86_64 ELF; no guest or library executed.")


if __name__ == "__main__":
    main()
