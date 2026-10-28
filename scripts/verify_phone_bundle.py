#!/usr/bin/env python3
"""Fail-closed validation of the seven-entry Fonesi debug phone test bundle.

This does not certify guest execution, session isolation, or product release readiness.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import zipfile

ROOT = Path(__file__).resolve().parent.parent
FILES = (
    "fonesi-0.5.0-candidate.apk",
    "fonesi-payload-r1.apk",
    "fonesi-payload-r2.apk",
    "HUONG_DAN_THU.md",
    "SDK_NOTICE.md",
    "APKSIG_NOTICE.md",
)
EXPECTED = set(FILES) | {"checksums.json"}
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
MAX_APK = 150 * 1024 * 1024
MAX_DOC = 5 * 1024 * 1024
MAX_MANIFEST = 32 * 1024


def digest(path: Path) -> str:
    hashing = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            hashing.update(block)
    return hashing.hexdigest()


def validate_bundle(archive_path: Path, originals: dict[str, Path]) -> dict:
    if set(originals) != set(FILES):
        raise ValueError("Original-file allowlist differs from signed candidate bundle")
    if not archive_path.is_file():
        raise FileNotFoundError(archive_path)

    with zipfile.ZipFile(archive_path) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(entries) != len(EXPECTED) or set(names) != EXPECTED:
            raise ValueError("Archive must contain exactly seven unique whitelisted entries")
        for entry in entries:
            name = entry.filename
            if name.startswith("/") or "\\" in name or "/" in name or name in (".", ".."):
                raise ValueError("Unsafe archive member path")
            kind = (entry.external_attr >> 16) & 0o170000
            if kind not in (0, stat.S_IFREG):
                raise ValueError("Archive may contain ordinary files only")
            size_limit = MAX_APK if name.endswith(".apk") else (
                MAX_MANIFEST if name == "checksums.json" else MAX_DOC
            )
            if not (0 < entry.file_size <= size_limit) or entry.compress_size <= 0:
                raise ValueError("Archive member exceeds budget or is empty")
            if entry.file_size > entry.compress_size * 200:
                raise ValueError("Unreasonable ZIP compression ratio")

        manifest = archive.read("checksums.json")
        if len(manifest) > MAX_MANIFEST:
            raise ValueError("Checksum manifest exceeds budget")
        try:
            checksums = json.loads(manifest.decode("utf-8"), object_pairs_hook=_strict_pairs)
        except (UnicodeError, json.JSONDecodeError) as error:
            raise ValueError("Invalid checksum manifest") from error
        if not isinstance(checksums, dict) or set(checksums) != set(FILES):
            raise ValueError("Checksum manifest file list differs from expected entries")
        if any(not isinstance(checksums[name], str) or SHA256.fullmatch(checksums[name]) is None
               for name in FILES):
            raise ValueError("Invalid SHA-256 digest format")

        verified = {}
        for name in FILES:
            original = originals[name]
            if not original.is_file() or original.is_symlink():
                raise ValueError("Missing or symbolic-link original bundle input")
            disk_hash = digest(original)
            archive_hash = hashlib.sha256()
            read_bytes = 0
            with archive.open(name) as source:
                for block in iter(lambda: source.read(65536), b""):
                    read_bytes += len(block)
                    if read_bytes > (MAX_APK if name.endswith(".apk") else MAX_DOC):
                        raise ValueError("Uncompressed archive data exceeds budget")
                    archive_hash.update(block)
            if read_bytes != archive.getinfo(name).file_size:
                raise ValueError("Truncated archive entry")
            if checksums[name] != disk_hash or checksums[name] != archive_hash.hexdigest():
                raise ValueError("Checksum mismatch between source, ZIP and manifest")
            verified[name] = {"bytes": read_bytes, "sha256": checksums[name]}
    return {"archive_sha256": digest(archive_path), "files": verified,
            "scope": "debug synthetic phone test files; guest runtime NOT VERIFIED"}


def _strict_pairs(pairs: list[tuple]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate checksum manifest key")
        result[key] = value
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path,
                        default=ROOT / "app/build/outputs/fonesi-0.5.0-phone-test.zip")
    parser.add_argument("--inputs", type=Path,
                        default=ROOT / "app/build/outputs/phone-bundle")
    args = parser.parse_args()
    mapping = {name: args.inputs / name for name in FILES}
    evidence = validate_bundle(args.bundle, mapping)
    candidate = json.loads((ROOT / "app/build/outputs/candidate/verification.json").read_text())
    if (candidate.get("engine_runtime_verified") is not False or
            candidate.get("sha256") != evidence["files"]["fonesi-0.5.0-candidate.apk"]["sha256"]):
        raise ValueError("Candidate identity differs from audited, runtime-unverified build")
    print("PASS: exactly seven debug ZIP entries; actual CRC, per-file SHA-256 and candidate "
          "signer-verified build source match.")
    print("NOT VERIFIED: clone accounts, Android device operation, real updates or release signing.")
    print(json.dumps(evidence, indent=2))


if __name__ == "__main__":
    main()
