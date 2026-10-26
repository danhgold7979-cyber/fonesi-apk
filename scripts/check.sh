#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
case "${1:-}" in
    "")
        ./scripts/gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
        ;;
    --with-engine-probe)
        python3 scripts/fetch_engine_sdk.py
        python3 scripts/generate_probe_manifest.py --check
        ./scripts/gradle.sh -PincludeEngineProbe=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :engine-probe:assembleDebug :probe-payload:assembleDebug :engine-probe:lintDebug :probe-payload:lintDebug --console=plain
        python3 scripts/verify_probe_apks.py
        ;;
    *)
        echo 'Usage: scripts/check.sh [--with-engine-probe]' >&2
        exit 2
        ;;
esac
python3 scripts/verify_main_build.py
