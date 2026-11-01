# E36 — private immutable package-cluster store hardening (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`. Pre-code inspection found that the repository already has `ApkSetAuditor` and `PrivateApkStore`; E36 must harden and validate this existing implementation rather than create a duplicate store. This phase handles signed APK bytes and metadata only. It does not execute guest code or enable production split support.

## Existing implementation inspected

- `apk-runtime/src/main/kotlin/vn/fonesi/apk/ApkSetAuditor.kt` validates APK signatures, package/version/signer identity, split names/dependencies, manifest size, source paths and hashes.
- `apk-runtime/src/main/kotlin/vn/fonesi/apk/PrivateApkStore.kt` copies an audited cluster into a unique staging directory, re-hashes copied bytes, re-audits private copies, writes a bounded index, syncs files/directories, atomically moves the staging directory and reopens the published cluster for readback.
- `apk-runtime/src/test/kotlin/vn/fonesi/apk/PrivateApkStoreTest.kt` already covers exact signed bytes, split retention, restart readback, coexistence of versions, invalid source, modified bytes, missing/extra files, symlink/path traversal, malformed/replayed indexes, replaced root and simultaneous publish.

## E36 objective

Turn this existing implementation into a verified storage contract with an explicit report of what is and is not guaranteed. Avoid adding an unnecessary abstraction or enabling the current engine's split support.

## Implementation sequence

1. Review the full store and tests line-by-line against `docs/engine/PACKAGE_CLUSTER_FORMAT.md` and `docs/engine/ACCEPTANCE_MATRIX.md`. Record each invariant as covered by a named test or a specific gap.
2. Add a deterministic cluster identity/digest receipt to the public store result only if needed to bind the envelope to the exact sorted member metadata. Do not treat a metadata digest as a signature or a guest-install receipt. Keep old published clusters readable or define a versioned index migration before changing index schema.
3. Verify index parsing has strict bounds for every string/count/size and rejects malformed UTF-8/fields, duplicate signers/members, invalid package identifiers and unsupported schema versions before large allocations.
4. Test source mutation during copy, readback after process-like store recreation, duplicate/concurrent publish of identical input, publish failure at each boundary, cleanup of owned staging without following symlinks, and atomic-move-unavailable behavior. The expected outcome is no partial generation visible and no previously published generation altered.
5. Test that garbage collection is absent or reference-safe. Do not add deletion of published clusters in E36 unless a durable reference registry exists for clone instances, active tasks, review-required rows and rollback generations.
6. Keep file operations in the app-private code root separate from guest account data and update snapshots. Reject root replacement and symlink parents on every operation.
7. Run `:apk-runtime:test`, core tests, source audit, cloud/candidate build and exact APK audits. Preserve `importsSplitApks=false`, `RuntimeBindings.supportsSplits=false` and all production acceptance flags.
8. Record exact tested SHA, CI workflow, test totals, any index compatibility changes and explicit non-claims in `docs/phases/E36_PRIVATE_CLUSTER_STORE_RESULTS.md`.

## Acceptance criteria

- Every accepted cluster has exactly one verified base and a complete, validated split dependency graph.
- Published bytes exactly match the audited source generation; the private copy passes signature/manifest/split audit again.
- Publication is atomic or fails without exposing a partial cluster; existing published generations remain unchanged after a failed new publish.
- `open()` revalidates root, index, file set, file sizes/hashes and APK metadata; no original-app fallback is allowed.
- Symlinks, path traversal, malformed index, unknown schema, source mutation and incomplete clusters fail closed.
- No guest code execution, install, launch, provider initialization or account/session data is performed by this phase.
- CI green is recorded for the exact tested tree; product readiness does not increase solely because storage tests pass.

## Not claimed

E36 does not prove that SpaceCore can import the stored split cluster, does not register guest components, does not launch third-party apps, does not prove account isolation, and does not enable `importsSplitApks`.
