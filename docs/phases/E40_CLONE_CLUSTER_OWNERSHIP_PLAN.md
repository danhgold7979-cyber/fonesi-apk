# E40 — Clone metadata ↔ private APK cluster ownership (plan before code)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`. Implement on the dedicated branch `phase/e40-clone-cluster-ownership-20261029`, based on `phase/e21-android-split-code-proof-20261028@1da1a1ea42c5f44c95bf9a3b0778d5bba0a23162`; keep both the parent branch and `main` unchanged.

## Branch isolation and scope boundary

E40 has its own stacked feature branch and Draft PR targeting the existing cumulative E21 branch. Do not add unrelated E41+ work to this branch. Keep the parent E21 branch/PR #12 and `main` unchanged. The branch is split by coherent acceptance boundary, not by creating one branch per file.

## Product-roadmap alignment

This is a technical increment under the existing **8 product phases / 32 steps**, primarily Phase 3 (steps 13–16: durable clone identity and lifecycle), with a storage-safety dependency for Phase 4. E40 is not a new product Phase and does not change the master roadmap.

E39 passed its cloud/candidate CI gate at workflow [#40803931491](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40803931491), with 334 tests in each gate, build/lint/signature audits and explicit checks that split import and destructive garbage collection remain disabled. Its Room owner registry is still standalone: clone metadata has no cluster ID and clone deletion does not yet release a corresponding Room owner reference.

## Scope

1. Add nullable `clusterId` to `clone_metadata` and a non-destructive Room migration 5→6. Existing clones, update tasks, queue pause state, engine instances and E39 references must survive unchanged.
2. Add a repository operation that associates an existing clone with a previously published and read-back-verified `PrivateApkCluster`. Validate canonical cluster UUID, engine/clone identity and package match. Within one Room transaction, keep `clone_metadata.clusterId` and its `CLONE` reference consistent.
3. Make association idempotent. Re-association may switch the clone to a different already-verified generation, but only if the old association and old owner reference agree. Add the new owner reference and update metadata before releasing the old owner reference; any inconsistency fails closed and rolls back the Room transaction.
4. Integrate clone deletion with ownership: only after the engine reports the clone absent, delete clone metadata and release the matching `CLONE` reference in one Room transaction. If metadata and owner references disagree, preserve the metadata and surface an error. Other owner types (update/review/rollback) remain untouched.
5. Add regression coverage for migration 5→6, preserving old rows, valid association, duplicate association, switching generation, wrong package, missing clone, inconsistent reference state, stale delete token, and reference release only after provider deletion confirmation.
6. Run source plan/audit checks, all core/APK/app tests, cloud and opt-in candidate builds, lint, signature/permission audits and the E40 architecture boundary checks. Commit generated Room schema 6 only after CI exports it and its shape matches the entity/migration.

## Additional pre-code safety audit (E40 deletion path)

The initial deletion integration calls the provider first and only discovers a pre-existing inconsistent CLONE reference in the later Room transaction. That retains metadata after failure but may already have removed the provider-side clone. Before irreversible provider deletion, while the existing operation lock is held, preflight the launch token and the exact zero-or-one matching CLONE reference. After readback confirms provider absence, repeat the same validation within the atomic metadata/reference deletion transaction. A corrupt or ambiguous owner must prevent the provider deletion attempt. Test this with a realistic CloneEngine double whose stored clone remains present on inconsistent owner input. This is a safety correction to step 4, not authorization to delete private APKs.

## Safety constraints

- Do not publish or activate clusters automatically from this increment; callers must pass a `PrivateApkCluster` returned by a successful `PrivateApkStore.publish/open` readback.
- Do not route private clusters into SpaceCore install/update yet. Existing create/update provider flows remain unchanged until a separate phase handles snapshot, update-task, rollback and crash recovery ownership together.
- No cluster deletion, garbage collection, automatic cleanup or source-app fallback.
- Keep `supportsSplits=false`, `importsSplitApks=false`, `real_guest_activity_registered=false`, `kernel_uid_isolation=false` and `production_clone_accepted=false`.
- No secrets, real APKs, user account data, or build artifacts in Git.

## Acceptance criteria

- Room 5→6 migration preserves all existing data and schema validation passes.
- A clone with a cluster ID has exactly one matching `CLONE` owner reference for its `engineKey/cloneId`; a clone without a cluster ID has none.
- Association, re-association and metadata deletion/ref release are atomic at the Room layer and tested after database reopen.
- Wrong package, stale token or mismatched references fail closed without deleting metadata or cluster files.
- Cloud and candidate gates pass on the same source SHA. The result is a storage/lifecycle improvement only; it is not proof that third-party APK splits can run or that product Phase 3 is fully accepted.
