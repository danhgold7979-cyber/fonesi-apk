# E38 — private cluster reference ledger results

Date: 2026-10-29. **E38 ledger CI PASS** on exact source SHA `0551bac37b519c39983a9f424937e133cc7f7ee0`. Workflow: [#40743707502](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40743707502). Test-only artifact: [fonesi-E38-cluster-reference-lifecycle-TEST-ONLY](https://github.com/danhgold7979-cyber/fonesi-apk/actions/artifacts/12839909773), SHA-256 `cccbe1a222ae5532e93710a35a25b251a732d20b4723fb4609685ac0da7090ff`, retained until 2026-11-04.

## CI evidence

- Reference-ledger source audit and Python compilation: PASS.
- Cloud/core/APK/app gate: PASS, 332 tests; build, lint and exact APK signature/permission audit passed.
- Opt-in candidate gate: PASS, 332 tests; build, lint and exact APK audit passed.
- Explicit boundary assertion: PASS; the reference ledger is non-destructive and SpaceCore split import remains disabled.

## Implemented

- Added `PrivateApkClusterReferenceLedger` with reference owner types `CLONE`, `UPDATE_TASK`, `REVIEW_REQUIRED` and `ROLLBACK`.
- Reference acquire/release operations are idempotent by cluster/owner/provider identity, serialized within the process and by an OS file lock, and persisted via bounded binary index rewrite with fsync and atomic move.
- The ledger validates canonical UUID cluster IDs, rejects missing/unsafe cluster directories, rejects corrupt/trailing/duplicate index records, bounds record/index sizes and checks the root before opening the lock.
- Added owner lookup, cluster lookup and a dry-run `unreferencedClusterIds()` report that only considers UUID directories with a valid-shaped cluster index.
- Tests cover idempotent acquire/release, durable readback through a new ledger instance, owner lookup, missing cluster rejection, concurrent acquisition, corrupted index fail-closed behavior, root replacement and orphan candidate detection.

## Explicit integration gap

The ledger is currently a standalone storage API. It is **not yet wired into Room clone metadata or update-task transitions**, so it is not yet an end-to-end ownership source of truth. No garbage collection/deletion method exists by design. An unreferenced candidate is only a report, never authorization to delete. The next phase must integrate ownership references transactionally/recoverably before any collector is considered.

## Acceptance boundary

E38 does not connect private cluster storage to SpaceCore install, enable split import, launch guest code, register guest components or prove account isolation. `supportsSplits=false`, `importsSplitApks=false`, `real_guest_activity_registered=false`, `kernel_uid_isolation=false` and `production_clone_accepted=false` remain unchanged.

## Product progress and next

Product estimate remains **28.00% weighted / ~30% rounded**. E38 is a storage-safety primitive, not a shipped guest capability. Next: **E39 — Room-backed cluster reference integration** with a versioned migration, owner-state reconciliation, crash-safe reference acquisition/release and tests proving active/review-required/rollback owners retain their cluster. Destructive garbage collection remains prohibited until the integration has a proven coordination boundary.
