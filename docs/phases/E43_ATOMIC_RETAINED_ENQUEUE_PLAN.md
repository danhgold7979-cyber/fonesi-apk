# E43 — Atomic verified private-cluster task enqueue and owner acquisition (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`. Dedicated branch: `phase/e43-atomic-retained-enqueue-20261029`, based on E42 documentation HEAD `1f13bfa70e12631baf650f4478e57244c7de168b` (code SHA `7f749ae4e54f01c23f11772430a992a050be36c0` passed [E42 CI #40841923812](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40841923812), 366 tests per cloud/candidate gate). Reporting HEAD has a separate pending same-source run; neither `main` nor parents E40–E42 are merged.

## Product alignment and established gap

**Phase 4, roadmap steps 17–20** of original 8 Phases / 32 steps. E43 is not a Phase or a public feature release. E41 can explicitly acquire a verified UPDATE_TASK source reference only after a QUEUED task exists. E42 makes review/terminal owner transitions atomic with journal CAS. The enqueue → source-owner association still comprises separate transactions, leaving a safe but inconsistent window after unexpected process termination. E43 removes that window without touching the engine/update runner or changing approved provider capabilities.

## Safe implementation steps

1. Add a Room-only `PrivateClusterRetainedEnqueuer` for **one** `UpdateTarget`/scope. Public entrypoint must validate canonical cluster UUID and re-open an already published cluster with `PrivateApkStore.open(clusterId, platformApi)` before DB mutation. It must reject wrong requested package/version/signers, wrong engine namespace and invalid clone scope; never use caller APK paths for snapshots or source authority.
2. Under a single Room transaction compose `UpdateQueue(RoomUpdateTaskStore(db))` with the **verified cluster's private source paths**, then `UpdateTaskClusterOwnershipRepository.associateReadBackVerified` for that queued task. A failure in either operation must roll back both task insertion and owner acquisition; no partial task owner. No separate provider install, source publish or implicit launch occurs.
3. Repeated enqueue with identical source/scope/clone IDs must return the same queued task with exactly one UPDATE_TASK reference (idempotent). A task already in progress, corrupt/ambiguous owner references or another cluster generation must fail closed without changing the existing task/owner. Existing CLONE/REVIEW_REQUIRED/ROLLBACK owners are never deleted or rewritten.
4. Tests under Robolectric/native SQLite: valid association and database reopen; idempotent duplicate; wrong package/version/signers and namespace; unpublished/malformed private-cluster ID; conflicting generation; in-progress task; SQL trigger abort on reference insert atomically rolling back the queue row; no external app/update execution. Use synthetic test-only cluster objects only at an internal seam; public API always requires cryptographic store readback.
5. Keep Room v6 and existing legacy queue unchanged. Update source audits and a dedicated E43 GitHub Actions cloud+opt-in candidate workflow with pinned toolchain, full tests/build/lint/APK signature/permissions/ABI and no-split/no-GC checks. Commit code and test before claiming PASS; check exact workflow SHA/artifact afterward.
6. Publish results and progress only from real test evidence. Product score remains 28.00% (~30% rounded) without runtime and phone-account acceptance. Draft PR stacked on E42, no merge/force push.

## Hard exclusion

Do not automatically activate a private cluster, reroute the existing UI or provider's update engine, import split APKs into SpaceCore, discard old account data, touch source-app credentials, enable garbage collection, create a fake clone, or declare Phase 4/real guest session update complete. `main` remains unchanged.
