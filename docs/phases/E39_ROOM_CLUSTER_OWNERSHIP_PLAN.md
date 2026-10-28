# E39 — Room-backed cluster ownership and reconciliation (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`. E38 added and tested a durable, non-destructive file ledger, but it is not connected to clone metadata or update-task state. E39 must establish one authoritative owner model before any destructive collection is considered.

## Decision before implementation

Use **Room as the authoritative owner registry** for app-level clone/task ownership. Treat the E38 file ledger as a low-level index/dry-run aid only until an explicit reconciliation contract is implemented; do not let two independent stores authorize deletion. No garbage collection is allowed in E39.

## Pre-code inspection

- Room database is version 4 with migrations 1→2, 2→3 and 3→4; schemas are exported under `app/schemas/vn.fonesi.app.storage.FonesiDatabase/`.
- `CloneMetadataRow` is keyed by `(engineKey, cloneId)` and `UpdateTaskRow` persists task ID, package, state, revision and serialized payload.
- `RoomUpdateTaskStore` performs revision/state-checked transitions; `CloneMetadataRepository` serializes provider mutations under `QueueLock`.
- The file ledger has atomic writes, process/file locks and dry-run orphan discovery, but no app-level owner source or destructive collection.

## Implementation sequence

1. Add a Room `cluster_references` entity keyed by `(clusterId, ownerType, ownerId, engineKey)` with indexes for `clusterId` and `(ownerType, ownerId, engineKey)`. Owner types: `CLONE`, `UPDATE_TASK`, `REVIEW_REQUIRED`, `ROLLBACK`. Validate UUID cluster IDs, nonblank bounded owner identity and timestamps in a repository layer.
2. Bump database schema 4→5 with an explicit `MIGRATION_4_5` that creates the table/indexes only; do not alter or delete existing clone/task rows. Register the migration in `FonesiDatabase.open` and export the Room v5 schema from the verified build.
3. Add a `ClusterReferenceRepository` using Room transactions for idempotent acquire/release, owner lookup, cluster lookup and reconciliation snapshots. Repeated acquire for the same owner+cluster must not create duplicates; one owner may legitimately reference multiple generations (for update/rollback).
4. Define authoritative ownership semantics: Room rows authorize retention; the E38 file ledger alone never authorizes deletion. Acquire references only after a published cluster has passed store readback. If Room insertion fails, retain the published cluster as an orphan candidate. Release only after the owner state transition/deletion is durable and the provider has confirmed the relevant state is safe.
5. Add a reconciliation report that compares Room references with published cluster IDs and E38 ledger entries. It may propose missing/stale references, but E39 must not automatically delete or repair ambiguous ownership. Missing referenced data produces a review/error result.
6. Add tests for Room migration 4→5 preserving prior rows, idempotent references, multiple owners/generations, clone/update/review/rollback retention semantics, process-reopen readback, orphan preservation after Room write failure, missing cluster references, corrupt E38 ledger, and concurrent operations under the same operation lock.
7. Keep clone creation/update flows unchanged until the private cluster store is deliberately integrated as a source of record. Do not route `PrivateApkStore.source()` into SpaceCore installation in E39. Do not enable split support or add GC.
8. Run Room migration tests, APK auditor/store/ledger tests, core/app tests, cloud and candidate build/audits, and all required source-claim audits. Capture the generated Room schema v5 as a CI artifact and commit the exact generated schema only after confirming it matches the code.

## Acceptance criteria

- Room v5 migration preserves existing clone metadata, update journal and queue state.
- Room is the authoritative source for owner references; E38's file ledger is never used as sole deletion authority.
- Active, `REVIEW_REQUIRED`, pending and rollback owners retain their referenced generations.
- Crash between file publication and Room reference insertion leaves an orphan, not a dangling owner or data loss.
- Missing/corrupt references fail closed and are surfaced for review.
- No cluster deletion or automatic garbage collection exists.
- No provider installation/launch behavior changes; `supportsSplits=false` and all production acceptance flags remain false.

## Not claimed

E39 does not import split APKs into SpaceCore, execute guest code, register real guest components, prove account isolation or raise product readiness solely for ownership bookkeeping.
