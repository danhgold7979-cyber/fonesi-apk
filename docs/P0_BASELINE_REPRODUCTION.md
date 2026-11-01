# Phase-0 baseline reproduction and artifact contract

**Source preparation only. No verified APK from this local batch exists yet.** T01/T02 are documentary-accepted; T03/T04 need exact-source integrated evidence and independent acceptance. Neither CI nor this document auto-accepts tasks.

## Existing environment only

Use an authorized clean checkout at the exact reviewed source commit, full JDK17, installed Android platform36 and Build Tools35.0.0, and installed `cmdline-tools/latest/bin/apkanalyzer`. No SDK installation, license acceptance, device, account, provider or release key is part of this recipe. `tools/check_toolchain.py --android` fails if required installed package identities/components are missing. Gradle SDK and JDK auto-downloads remain disabled; only the SHA-pinned official Gradle8.14.6 distribution and pinned AGP dependencies are fetched through the established build.

No implicit newest SDK/target migration. min26/compile36/target36, AGP8.13.2 and BT35.0.0 remain pinned. Read `TOOLCHAIN.md` and `FOUNDATION_LINT_POLICY.md` for provenance, security maintenance, the explicit target36 notice and limits.

## One integrated checkpoint

```sh
export EXPECTED_SOURCE_SHA="$(git rev-parse HEAD)"
python3 tools/run_host_checks.py
python3 tools/check_toolchain.py --android
python3 tools/run_compile_checks.py
python3 tools/record_baseline.py
```

Execute sequentially with fail-fast shell behavior (`set -euo pipefail` in Bash); do not continue after a failed command. No release task may use the finite debug informational policy. A missing or changed report fails closed. Host commands/counts are captured from actual process results, not hardcoded PASS. Scripts reject tracked source edits, mixed source SHAs, missing/changed APK, incomplete test aggregates and absent package/lint proof. The package verifier reads actual manifest, resource table and decoded XML and verifies a debug signature. Synthetic tests of those predicates do not constitute package proof.

Hosted workflow runs HOST first, then the installed-SDK build only after HOST succeeds. HOST evidence goes into `foundation-host-<sourceSHA>`; the final bundle `foundation-compile-only-<sourceSHA>` contains APK, host/compile/lint/baseline receipts, all three lint reports, source SHA, reproduction recipe and checklist. Normal upload runs only after all gates succeed. Current GitHub retention is14days, so this is not permanent archival storage: record the exact run/artifact URL and save a verified durable copy before T04 delivery acceptance or expiry. No release/upload outside these approved CI artifacts is implied.

## Interpret and compare

APK identity: `vn.fonesi.app`, versionCode1, versionName`0.1.0-foundation`, debug-only. Compare source SHA, APK SHA256/bytes and version before comparing later builds. Debug signing keys and ZIP timestamps can differ between runners; the contract requires reproducible checked semantics and per-build identity, not an unsupported claim of bit-identical independently signed APKs. Do not overwrite an older accepted receipt with a later artifact; each CI bundle is source-bound.

HOST proves synthetic logic/documentary predicates. COMPILE proves only the recorded build/lint/package/signature checks. ANDROID, PROVIDER, PHONE and eight-app compatibility remain NOT_RUN; provider is unavailable and guest controls disabled. Download is not authorization to install, log in, run a guest or activate a provider. No screenshot/UI isolation claim is derived from these receipts. Missing or expired artifact identity blocks T04 acceptance rather than inventing a download path.

Diagnostic aggregation: after a fresh build attempt, lint raw reports and all available read-only APK observations are collected independently. Any Gradle, lint-receipt or package gate failure keeps the aggregate nonzero; subsequent baseline recording and normal APK artifact delivery do not run. Old generated APK/receipts are removed first, so a failed build cannot reuse stale evidence. Diagnostic observations never imply installing or loading APK code.
