# E38 — private cluster reference lifecycle and retention safety (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`. E36 passed private APK cluster integrity CI; E37 passed conservative capability reporting. E38 must address a remaining storage-lifecycle gap without enabling provider split import or deleting any cluster whose ownership is uncertain.

## Pre-code facts

- `PrivateApkStore` publishes immutable UUID-addressed directories and has no garbage-collection API. That is safe against accidental deletion but allows orphaned generations to accumulate.
- `CloneMetadataRow` currently has no package-cluster ID. `UpdateTaskRow` persists task payloads and state/revision in Room, while source snapshots live in a separate update-snapshot root.
- A filesystem operation and a Room transaction cannot be assumed atomic together. A GC algorithm that reads references and then deletes a directory without a shared coordination boundary can race a new reference.
- Current SpaceCore integration is single-APK only and does not use `PrivateApkStore` as an install bridge. Do not wire cluster IDs into SpaceCore install as part of E38.

## Objective

Define durable ownership and retention semantics for private cluster generations before any garbage collection is implemented. Prevent deletion while a generation is referenced by clone metadata, active/review-required update tasks, pending provider work or rollback policy. Unknown references must retain data, not delete it.

## Implementation sequence

1. Add a `ClusterReference` model with `clusterId`, owner type (`CLONE`, `UPDATE_TASK`, `REVIEW_REQUIRED`, `ROLLBACK`), owner ID, engine/provider namespace, created timestamp and optional expiry only for explicitly expirable diagnostic references. No implicit TTL for clone or review-required owners.
2. Choose one durable source of truth for references. Prefer Room-backed rows with unique `(clusterId, ownerType, ownerId)` and indexes by cluster/owner. Document transaction semantics and schema migration from database version 4; export and verify the schema snapshot.
3. Keep cross-store mutation fail-safe: publish cluster first, then persist owner reference; if reference persistence fails, the cluster is an orphan but must remain. On owner release, remove the reference only after the corresponding owner state is durably deleted/terminal and provider state is confirmed safe. Never delete a cluster as part of the same best-effort callback that updates clone metadata.
4. Do not implement destructive GC until the shared operation lock covers every reference creation/release and collector operation, and a crash-recovery protocol exists for the interval between Room and filesystem changes. If that invariant cannot be proven, implement reference queries and a dry-run collector only.
5. Add recovery reconciliation that reports missing cluster directories, references to missing clusters and unreferenced generations. It must not repair by deleting data or by consulting the original installed app. Missing referenced data moves the owner to an explicit review/error state.
6. Add tests for duplicate reference idempotence, concurrent acquire/release, clone metadata association, active/review-required task retention, rollback retention, process-restart readback, orphan cluster preservation, missing referenced cluster, corrupted reference rows and collector dry-run output.
7. Preserve `PrivateApkStore` atomic publish/hash/signature checks. Add no provider install/launch calls and no `supportsSplits=true` changes.
8. Run Room migration/instrumentation or Robolectric tests, APK auditor/store tests, cloud/candidate builds, exact APK audits and a fail-closed architecture audit. Record exact source SHA and artifacts.

## Acceptance criteria

- Every persisted owner reference resolves to a canonical UUID cluster ID and explicit owner namespace.
- Active, `REVIEW_REQUIRED`, pending and rollback owners always retain their cluster generation.
- A cluster that has no reference is only classified as an orphan candidate; no automatic deletion occurs without proven global coordination.
- Process death between filesystem publication and Room reference insertion leaves an orphaned cluster, not a dangling owner or data loss.
- Process death between owner deletion and reference release is recoverable and retains extra data rather than deleting live data.
- Missing/corrupt references fail closed and are surfaced for review.
- No code is executed, no original-app fallback is used, and no SpaceCore split support is enabled.

## Explicit non-goals

E38 does not import split APKs into SpaceCore, launch a guest, register Android guest components, prove account isolation, or raise product readiness solely for storage bookkeeping.
