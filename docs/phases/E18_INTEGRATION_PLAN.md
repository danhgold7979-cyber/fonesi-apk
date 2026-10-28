# E18 — End-to-end source integration and candidate APK artifact audit (plan before code)

Date: 2026-10-28. Separate branch `phase/e18-integration-apk-audit-20261028` starts at `phase/e17-synthetic-application-attach-20261028@d706505a52f266b4cf70fec968456e747c04750b`. E17 runtime CI is independently in progress at time of planning, so an E18 build does not automatically certify its Application stage. Keep `main@c055df6` intact.

## Master plan reconciliation

Reviewed `docs/ROADMAP.md` (8 phases and 32 tasks), `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, `scripts/check.sh`, `scripts/build_phone_bundle.py`, `scripts/verify_candidate_build.py`, source-native/guest loader and E11–E17 proof boundaries. E11–E16 AOSP34 CI has actual green synthetic proof; E17 source and proof pending. Phases 1/2/5 remain incomplete for real guest Application/Activity/PackageManager/Binder and base+split social APKs. The existing `0.5.0-candidate` uses a pinned closed-source SpaceCore SDK, limited to complete single ARM64 APK on API26–34/4 KB, and is not validated on a physical phone.

**Reason for integration check:** source has evolved through many nested PRs; individual probes passing does not show the user-facing `app` cloud and SDK candidate still build, package, and maintain their safe permission/engine guard policies. This stage ties the cumulative source history into one coherent checked commit and makes **test-only artifacts** accessible via GitHub Actions, not a release.

## Implementation tasks

1. Create a distinct GitHub Actions workflow that runs `./scripts/check.sh --with-engine-candidate` from branch HEAD, not stale `main`. This runs exact pinned SDK download/hash, current core/APK-runtime/Robolectric tests, cloud/candidate builds, APK manifest/signature/permissions and lint.
2. Run the separately pinned source-engine native check `python3 scripts/check_source_engine.py`, with ARM64 ELF 16 KB/JNI audits and JVM signed private APK tests. Keep source runtime opt-in, never bundle it into production candidate by accident.
3. Build and audit the user's deferred phone test ZIP using `python3 scripts/build_phone_bundle.py`. Require exact 7 entries and `checksums.json` match, forbid unexpected keystore/private data/code fixtures in the archive. Keep candidate and payload signed under the same *debug* signer, never call it release-signed.
4. Build and static-audit current E17 synthetic native-runtime-probe on x86_64; its **runtime** remains separately tested by dedicated E17 emulator workflow. Uploaded artifacts must carry a SHA-bound CI run and short retention; never mislabel the synthetic probe as Fonesi product APK.
5. Add `docs/phases/E18_INTEGRATION_RESULTS.md` with path/job/test counts, boundary and source references **only after** GitHub CI evidence is available. Update `docs/STATUS.md` preamble with concise current proof summary and links while preserving historical E9 reports.
6. Retain all master requirements that cannot be completed in CI: real guest Context/Application/Activity/lifecycle/PackageManager/Binder, arbitrary base/split/isolatedSplits, Android permissions/notifications, Facebook/Messenger/Zalo/TikTok/Threads/X/Shopee/YouTube, Samsung ARM64/other vendor, account separation, updates preserving sessions, physical performance, distinct release signing, and background scheduling.

## Acceptance

- All tests required by native and candidate CI exit 0; no red tests, ignored assertions or manual substitution of prebuilt APKs.
- Report actual count from structured test output rather than guessing and do not add duplicate same-suite runs as independent tests.
- ZIP is generated from this exact branch build and cryptographic hashes verified before upload.
- Mark `CI_INTEGRATION_PASS` only when workflow is green; explicitly label `ANDROID_GUEST_PRODUCT_UNVERIFIED`. If build fails, inspect log and document a targeted correction before code changes.

## Security and deployment boundary

No real phone installation, release publishing, real credentials, test signing key export or cloning of personal accounts. `main` and existing guest data remain unchanged; E18 test artifacts are internal debug candidates with limited capabilities, not the complete product.
