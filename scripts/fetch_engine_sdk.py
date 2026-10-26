#!/usr/bin/env python3
"""Fetch only the pinned SpaceCore candidate. Does not install or execute it."""
import hashlib
import os
from pathlib import Path
import shutil

from bootstrap_android import download

COMMIT = "3826a2fa1ac492fbbe7435ccb52074c1e2b702de"
SHA256 = "4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f"


def main():
    archive = download(
        f"https://raw.githubusercontent.com/FSpaceCore/SpaceCore/{COMMIT}/app/libs/flib-release.aar",
        f"spacecore-{COMMIT}.aar", SHA256,
    )
    target = Path(os.environ.get("FONESI_TOOLS_DIR", "/workspace/.fonesi-tools")) / "engine-sdk/spacecore" / COMMIT / "flib-release.aar"
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != SHA256:
            raise RuntimeError("Existing SDK failed verification; refusing to overwrite/use it")
    else:
        temporary = target.with_suffix(".part")
        shutil.copyfile(archive, temporary)
        temporary.replace(target)
    print("Pinned SpaceCore candidate verified. Not a compatibility or security certification.")


if __name__ == "__main__":
    main()
