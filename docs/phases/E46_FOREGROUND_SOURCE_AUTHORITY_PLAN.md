# E46 — Guard foreground auto-update against private-source authority (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`.
Development branch: `phase/e46-foreground-source-authority-20261029`, based on E45 integration source `64e0a285da84360e3fdea57135367d5b9bd6f8b5`.
Product alignment: original **Phase 4, roadmap steps 17–20**; E46 is a bounded technical safety increment, not a ninth phase.
E45 exact-source cloud/candidate CI #40848683404 was **in progress** when this plan was written; do not treat E45 or E46 as PASS before independent success.

## Problem and dependency

`ForegroundAutoUpdater.reconcileSafeQueuedWork` compares every QUEUED/DEFERRED job to the current *original installed-app* `SourceCatalog`, and can cancel a newer-source authority task as SUPERSEDED_SOURCE. `retrySafeDeferred` can independently requeue one using a plan derived from original-app paths. These operations must not mutate an E43–E45 readback-verified retained private source, or a pre-E44 legacy task with an existing E41 UPDATE_TASK/REVIEW_REQUIRED reference and no durable marker. E45 `RoomLegacyUpdateSourcePolicy` already establishes the fail-closed source predicate for the production runner; reuse it rather than inventing a weaker rule.

## Ordered implementation

1. Reconfirm the base/source, E45 PR/CI, and existing `ForegroundAutoUpdater` and Robolectric tests. Do not edit E45 while its CI is running, and do not merge `main`.
2. Add a private, read-only, fail-closed `mayManageOriginalSource(task)` check to **both** foreground automatic reconciliation paths before considering original-app version, scope or retry. Use `RoomLegacyUpdateSourcePolicy`; exceptions must skip mutation (do not auto-cancel or retry on uncertain Room state). Do not alter manual user-confirmation semantics or mark private tasks as completed.
3. Add native SQLite/Robolectric regression tests verifying retained QUEUED tasks survive original package version changes; legacy marker=false tasks with E41 UPDATE_TASK/REVIEW_REQUIRED refs survive as well; ordinary legacy queued tasks still cancel on confirmed source supersession; uncertain states remain un-replayed.
4. Pin a dedicated E46 source audit and GitHub Actions workflow: prior E40–E45 audits; `./scripts/check.sh` and `./scripts/check.sh --with-engine-candidate`; unit/Robolectric, build, lint, APK signature/permissions, unchanged Room v6, split disabled, no destructive GC.
5. Commit a small reviewable code+test increment. Verify exact E46 SHA workflow success (plus parent E45 if applicable), inspect test reports; document results and update `docs/STATUS.md`/`docs/PRODUCT_PROGRESS.md` based on evidence only.

## Acceptance boundaries and blockers

- A retained task or an older owner-associated legacy task cannot be cancelled or requeued by the **foreground original-source** version-change comparison. Missing/corrupt owner readback fails closed.
- Legacy/original-source tasks with no retained authority still follow the existing cancellation/retry behavior.
- No new database migration; Room remains v6. No private APK install/provider activation, no split import, no new execution flow or file deletion.
- No proof of guest Activity, real independent UID/account session, third-party app update, Samsung phone test or production release; weighted product score stays **28.00%**, accepted third-party clones **0**. No merge of draft PR stack until its gates are met.
