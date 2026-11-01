# E41 — Room update-task ↔ private APK cluster reference retention (plan before code)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`.
Dedicated development branch: `phase/e41-update-task-cluster-ownership-20261029`; based on E40 exact-HEAD verified source `dc5aea86a01aee0594ad70cbae6367f6332e2596` ([E40 CI #40838110200](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40838110200) SUCCESS, 349 tests per cloud/candidate gate). Keep the E40 branch, parent E21 branch and `main` unchanged; open a Draft PR stacked on E40.

## Original product-roadmap alignment

Existing **8 product Phases / 32 steps / 4 real-phone acceptance milestones** remain unchanged. E41 is a small storage/recovery increment toward **Phase 4, steps 17–20** (verified base/split source, durable update queue, interruption/unknown-outcome retention). It is not a new product Phase and cannot finish Phase 4 or claim real account/session continuity.

## Pre-code reconciliation and hazard

- `RoomUpdateTaskStore` persists `UpdateTaskRow` and enforces revision/state CAS; `TaskCodec.decode` verifies row/payload consistency, `UpdateRunner` transitions through QUEUED→PREPARING→APPLYING→VERIFYING→REVIEW_REQUIRED/terminal with durable marker before engine mutation.
- E39 Room v5 `cluster_references` is authoritative; E40 Room v6 only connects `CLONE` references to clone metadata, with no automatic cluster deletion. Source snapshots, update journal and review/rollback owner references are **not yet linked** to the published private cluster registry.
- A naïve removal of an UPDATE_TASK reference on unknown apply outcome would permit future cleanup to lose the last evidence for recovery; a naïve reference held forever would leak storage. E41 must fail closed and separate TASK / REVIEW_REQUIRED / ROLLBACK roles.

## Implementation plan, one bounded acceptance slice

1. Preserve Room schema v6 and existing `RoomUpdateTaskStore` / `UpdateRunner` behavior. Do **not** change source import, engine-update application or user-facing success messages in E41.
2. Add `UpdateTaskClusterOwnershipRepository` tied to existing Room `update_tasks` and `cluster_references`. Public association must call `PrivateApkStore.open(clusterId, platformApi)` and validate canonical UUID, taskId/engine identity, exact task package/version/signers, and QUEUED state before any reference acquisition. Do not use arbitrary caller-supplied APK paths or accept synthetic test metadata as a production readback substitute.
3. In one Room transaction assert owner references are empty or **exactly one UPDATE_TASK owner** for that task+engine. Acquire the verified cluster's UPDATE_TASK reference idempotently; reject ambiguous/mismatched existing owners or a concurrently changed task. No cluster files are ever removed.
4. After an actual journal transition to REVIEW_REQUIRED, support an explicit **Room-only** promotion from UPDATE_TASK to REVIEW_REQUIRED ownership: insert/verify the review ref before releasing the task ref in one transaction; repeat calls are idempotent. REVIEW_REQUIRED and ROLLBACK references must not be discarded on an unknown engine outcome.
5. After a journal transition to a **terminal** state (SUCCEEDED, FAILED, CANCELLED), permit explicit release of **only** the UPDATE_TASK owner. Reject premature release during QUEUED/PREPARING/APPLYING/VERIFYING/DEFERRED/REVIEW_REQUIRED, and preserve unrelated CLONE/REVIEW_REQUIRED/ROLLBACK owners. E41 cannot auto-release a review hold without separately proven recovery.
6. Add Robolectric/native SQLite tests for canonical readback rejection; valid/idempotent association (synthetic test-only seam), wrong engine/task/package/version/signer, ambiguous owners, optimistic state guard, review promotion and repeated promotion, premature/terminal release, other-owner retention, database reopen, and rollback on injected SQL failure. Tests must not silently repair or delete ambiguous ownership.
7. Add fail-closed source/architecture audit and dedicated E41 cloud+opt-in candidate GitHub Actions workflow with pinned toolchain, `./scripts/check.sh`, `./scripts/check.sh --with-engine-candidate`, signature/permission/ABI checks, split-disabled/no-GC checks, test XML and sanitized artifacts. Do not mark E41 PASS until same-source checks succeed.

## Acceptance boundaries and forbidden claims

- E41 APIs are **explicit, offline and storage-only**. They do not publish clusters, execute update tasks, make background engine calls, activate guest code, import split APK into SpaceCore, or delete private files.
- Room remains the sole authoritative owner registry; E38's file ledger is advisory, never a deletion authorization.
- Never use test doubles to claim arbitrary third-party APK execution, separate kernel UID/account isolation, real social app compatibility, update session retention, Samsung ARM64 or release signing.
- No destructive migration, force push, main merge, production capability flag changes, secret/keystore/sample account commits, or assertion downgrades.
- Product readiness remains **28.00% weighted (~30% rounded)** until independent user-facing acceptance gates are met.
