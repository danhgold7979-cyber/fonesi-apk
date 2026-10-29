# E58 — serialize private APK cluster association with clone lifecycle operations (pre-code plan)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. New development branch `phase/e58-clone-cluster-association-lock-20261029` from E57 reporting head `f4d534a233c1c65ab6408a4aef962a44c49a672d` on [Draft PR #31](https://github.com/danhgold7979-cyber/fonesi-apk/pull/31). E57 code/test SHA `abd18c118e3e42fe8415142d3799caefc7cf1f08` passed [CI #40870539313](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40870539313). E57 report-head CI #40871698660 was still in progress at pre-code planning. Original **Product Phase 3 steps 14–16**, dependency on **Phase 4 reference safety**. E58 is NOT Product Phase 58.

## Observed gap

`CloneClusterOwnershipRepository.associateReadBackVerified` currently performs Room metadata and CLONE reference transitions transactionally, but takes no OS operation lease. E56 `AppsViewModel.stopClone` and E57 rename/restyle hold the shared lock. Association may race the stop's lock-time snapshot of `clusterId` and its actual provider dispatch, even though Room CAS protects the ownership transaction. A Room-only CAS cannot serialize with an external SDK side effect.

## Implementation plan and constraints

1. Verify E57 report SHA, E56/E57 stop and lock code, `CloneClusterOwnershipRepository`, all call sites and native SQLite tests; keep `main` untouched. Do not claim parent reporting PASS until its exact CI succeeds.
2. Require a **real injected shared `QueueLock` for production association**. Hold one OS lease through the audited cluster readback and atomic Room CLONE owner/metadata association; acquire fail-closed before mutating source metadata. Ensure package, engine identity, launch-token, cluster reference checks and transaction rollback remain unchanged. Do not add an unlocked public production overload. Existing internal test seam must either require the same lease or remain explicitly test-only and never back a production call.
3. Do **not** reacquire the operation lock in `preflightDeletion` or `deleteMetadataAfterProviderConfirmed`, because the existing delete caller already holds the lease; avoid deadlocks and preserve the provider-confirmed delete boundary. Avoid introducing private APK deletion, automatic publication or guest activation.
4. Add native SQLite/Robolectric tests for held lock refusal and no Room changes; lease release on thrown cluster/package mismatch; valid association and generation switch after release, and preservation of unrelated UPDATE_TASK/REVIEW_REQUIRED/ROLLBACK owners. Include concurrency/race proof that a cooperating stop lock prevents association until stop's critical section ends.
5. Add E58 source audit and pinned cloud/candidate workflow preserving E40–E57 audits, build/lint/tests, APK signatures/permissions, split-disabled and no-GC guards. Only mark technical PASS on exact source SHA after all gates succeed.
6. Record implementation/results in `docs/STATUS.md`, `docs/EXECUTION_PLAN.md`, `docs/PRODUCT_PROGRESS.md` and E58 results; retain 28.00% weighted product score until rubric evidence changes. Keep Draft PR; do not merge or release.

## Explicit exclusions

This does not prove full split APK engine support, guest Activity registration, separate Android UID/account sessions, actual third-party lifecycle, phone tests, or release signing. It is an owner/lifecycle race guard only. Production `supportsSplits=false` and `importsSplitApks=false`; **0 accepted real app clones**.
