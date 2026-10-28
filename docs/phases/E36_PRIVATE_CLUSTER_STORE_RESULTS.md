# E36 — private package-cluster store integrity results

Date: 2026-10-29. **E36 CI PASS** on exact workflow source SHA `0fb45f7439176cdd9e62b1f330217ca9558a8ff0`. Workflow: [#40738045402](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40738045402). Test-only artifact: [fonesi-E36-private-cluster-store-TEST-ONLY](https://github.com/danhgold7979-cyber/fonesi-apk/actions/artifacts/12838342373), SHA-256 `9d25e49950ca18a56661492c23bc41321e77edaed4d93cdeb0f732e3a8bcc340`, retained until 2026-11-04.

## CI evidence

- Source audit compilation: PASS.
- APK cluster auditor, private store, core and app tests: PASS; the cloud gate reported 323 tests.
- Opt-in candidate build, tests, lint and exact APK audit: PASS; the candidate gate reported 323 tests.
- Explicit assertion that `SpaceCoreBackend` still requires one APK and `RuntimeBindings.supportsSplits=false`: PASS.
- Cloud/candidate build artifacts, lint and APK signature/permission audits passed.

## E36 implementation

- Added `PrivateApkCluster.clusterDigest`, a deterministic SHA-256 identity over package/version/signer, sorted base/split metadata and member hashes. It is independent of the random store ID and absolute file paths. This digest is not a signature and not a guest install/launch receipt.
- Added tests for digest stability across readback and reordered source paths, and digest changes when cluster membership or version changes.
- Retained existing validation/publication safeguards: cryptographic APK signature verification, package/version/signer equality, split dependency checks, bounded index, private-copy re-audit, per-file hash readback, symlink/path traversal rejection, atomic directory move, unique immutable generations and no original-app fallback.
- No guest code was executed and no provider split capability was enabled.

## Related CI note

A separate E22 emulator workflow on commit `1cb2fc74cda3...` failed at **“Install only ephemeral Android 34 AOSP emulator image”** before its test step. This is an emulator provisioning failure, not a failure of the E36 store workflow. The E21/E23/E24/E25 emulator runs on that commit completed successfully; E22 should be rerun after checking the emulator provisioning logs. Do not represent that E22 run as green.

## Acceptance boundary

E36 proves storage/integrity behavior for signed owned test APK clusters. It does **not** prove that SpaceCore can install or execute those clusters, register guest components, isolate third-party accounts, preserve social sessions or support 16 KB native pages. `supportsSplits=false`, `importsSplitApks=false`, `real_guest_activity_registered=false`, `kernel_uid_isolation=false`, and `production_clone_accepted=false` remain unchanged.

## Product progress and next phase

The product estimate remains **28.00% weighted / ~30% rounded**. E36 is infrastructure, not a user-facing clone acceptance milestone. Next: **E37 — provider capability model and truthful UI/planner gating**, with no engine launch or split execution. E37 must make unsupported/unknown provider capabilities fail closed and ensure the UI never reports a guest as installed/running without provider readback.
