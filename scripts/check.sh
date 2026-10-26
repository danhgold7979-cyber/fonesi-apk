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
    --with-engine-candidate)
        python3 scripts/fetch_engine_sdk.py
        python3 scripts/generate_candidate_manifest.py --check
        ./scripts/gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
        python3 scripts/verify_main_build.py
        mkdir -p app/build/outputs/cloud
        cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/cloud/app-debug.apk
        cp app/build/reports/cloud-verification.json app/build/outputs/cloud/verification.json
        ./scripts/gradle.sh -PengineCandidate=true :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
        python3 scripts/verify_candidate_build.py
        mkdir -p app/build/outputs/candidate
        cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/candidate/fonesi-0.4.0-candidate.apk
        cp app/build/reports/candidate-verification.json app/build/outputs/candidate/verification.json
        exit 0
        ;;
    *)
        echo 'Usage: scripts/check.sh [--with-engine-probe|--with-engine-candidate]' >&2
        exit 2
        ;;
esac
python3 scripts/verify_main_build.py
