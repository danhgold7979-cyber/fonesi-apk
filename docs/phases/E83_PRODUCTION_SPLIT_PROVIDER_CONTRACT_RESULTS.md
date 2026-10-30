# E83 — production split-provider contract and fail-closed boundary results

Date: 2026-10-30. Repository: danhgold7979-cyber/fonesi-apk. Product mapping: Phase 1 steps 5–8, preparing Phase 3 step 15 and Phase 4 update safety.

## Exact-source verification

- Final E83 source/test SHA: 58d52f4aa05c8c8d5256e7125b522815418b1e02.
- Full exact-source workflow: [#41083456690](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41083456690), SUCCESS, 21/21 job steps.
- Authorization subgate: [#41083456286](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41083456286), SUCCESS.
- TEST-ONLY artifact: [#12992820772](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41083456690/artifacts/12992820772).
- Cloud: 521 tests, 0 failures, 0 errors, 0 skipped. Candidate: 521 tests, 0 failures, 0 errors, 0 skipped.
- Source-engine: 63 JVM tests, 0 failures/errors/skips; native host 18 tests and code-layout 7 tests passed. Synthetic payload: 14 JVM tests, 0 failures/errors/skips.
- Cloud/candidate build, lint, signed APK audit, source AAR and Activity/Application preflight passed.
- Source-engine AAR was built. Its ELF load segments report 16 KB alignment, but Android native execution remains false and the AAR is not integrated as a production provider.
- AOSP API 34 host-stub lifecycle passed. The receipt is bound to the exact source SHA and signed APK hashes.
- E78 parent artifact #12952618461 was downloaded, artifact metadata and archive SHA-256 were checked, then the exact E78 source/APK-bound receipts were validated and included in the E82 artifact.

## Implemented

1. GuestApkClusterMemberIdentity retains member SHA-256, byte length, signer set, feature flag, splitRequired, required/provided split types, isolatedSplits, configForSplit and uses-split dependencies.
2. GuestApkClusterIdentity recomputes the length-prefixed fonesi-package-cluster-v1 digest and rejects opaque or stale digests. Validation mirrors ApkSetAuditor's package/split grammar, package length, signer consistency, required split-type coverage, config/dependency graph and cycle rules.
3. PrivateApkCluster maps the full AuditedApkSet metadata and measured private-file byte lengths into the shared core identity. PrivateApkStore.clusterDigest delegates to the shared algorithm. A regression independently recomputes the previous store digest and proves byte-for-byte compatibility, plus input-order/readback stability and identity equality after open.
4. EngineCapabilities enforces the same proof dependency chain as GuestBackendCapabilities: split code execution requires accepted cluster import; atomic update requires both accepted import and code-execution proof.
5. Added negative and positive identity, readback, member length, metadata tampering, config/dependency, signer, split-type and capability-chain tests.
6. Added a pinned E78 parent artifact verifier and changed E82 CI to download the exact artifact, verify run/source/archive SHA/APK hashes and explicit false guest/UID claims, then bundle the validated receipts.
7. SpaceCore remains a one-APK adapter. ManagedGuestEngine does not advertise split import until it orchestrates a typed provider. RuntimeBindings.supportsSplits remains false.

## Exact limits and non-claims

The E82 receipt proves a framework-created non-exported host-stub Activity, host Context/Window and host process/UID 10142. It does not prove guest PackageManager registration, guest ActivityThread registration, a separate kernel UID, third-party application execution, social-account isolation, production split import, or a physical Samsung device. The test payloads are synthetic and test-only.

E83 is therefore a PASS for the typed, fail-closed provider contract and its exact-source tests only. It is not a production split-capable provider or product acceptance. The current candidate remains single-APK and product readiness remains 28.00%, with 0 accepted real third-party clones.

## Final exact-source receipt

The E82 receipt on SHA 58d52f4 confirms framework onCreate/onResume, non-null host Context/Window, one-shot factory authorization and a non-exported host stub in host package/process/UID 10142. It explicitly records guest component registration=false, guest UID isolation=false, third-party execution=false, social accounts=false, production clone=false and physical device=false. The E78 parent receipt remains a separate synthetic same-UID scoped-data proof; it does not prove PackageManager/ActivityThread guest registration or kernel UID isolation.
