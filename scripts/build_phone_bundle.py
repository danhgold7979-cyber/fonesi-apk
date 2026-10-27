#!/usr/bin/env python3
"""Package verified candidate plus two signed synthetic payload revisions for a later phone test."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile
from verify_probe_apks import ROOT, A, inspect_apk


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    candidate = ROOT / "app/build/outputs/candidate/fonesi-0.5.0-candidate.apk"
    report = json.loads((candidate.parent / "verification.json").read_text())
    if digest(candidate) != report["sha256"] or report["engine_runtime_verified"] is not False:
        raise RuntimeError("Run scripts/check.sh --with-engine-candidate before packaging")
    folder = ROOT / "app/build/outputs/phone-bundle"
    folder.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(candidate, folder / candidate.name)
    for revision in [1, 2]:
        log = folder / f"payload-r{revision}-build.log"
        with log.open("w") as stream:
            subprocess.run([str(ROOT / "scripts/gradle.sh"), "-PincludeEngineProbe=true", f"-PpayloadTestRevision={revision}",
                ":probe-payload:assembleDebug", ":probe-payload:lintDebug", "--console=plain"], cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
        apk = ROOT / "probe-payload/build/outputs/apk/debug/probe-payload-debug.apk"
        manifest, permissions, signer = inspect_apk(apk, "vn.fonesi.payload")
        if permissions or manifest.get(A + "versionCode") != str(revision) or signer != report["signer_sha256"]:
            raise RuntimeError("Synthetic payload version/permission/signature mismatch")
        shutil.copyfile(apk, folder / f"fonesi-payload-r{revision}.apk")
        print(f"Verified payload revision {revision}; no native engine or phone was executed.")
    shutil.copyfile(ROOT / "docs/PHONE_TEST_GUIDE.md", folder / "HUONG_DAN_THU.md")
    shutil.copyfile(ROOT / "third_party/spacecore/NOTICE.md", folder / "SDK_NOTICE.md")
    files = [folder / name for name in [candidate.name, "fonesi-payload-r1.apk", "fonesi-payload-r2.apk", "HUONG_DAN_THU.md", "SDK_NOTICE.md"]]
    (folder / "checksums.json").write_text(json.dumps({p.name: digest(p) for p in sorted(files)}, indent=2) + "\n")
    output = folder.parent / "fonesi-0.5.0-phone-test.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted([*files, folder / "checksums.json"]): archive.write(file, file.name)
    print(f"Phone test bundle created: {output}")
    print("This packages a candidate, not a completed or phone-verified product.")


if __name__ == "__main__":
    main()
