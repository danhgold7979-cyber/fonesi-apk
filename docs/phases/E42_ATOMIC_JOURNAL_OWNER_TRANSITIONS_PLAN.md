# E42 — Atomic Room journal transitions and retained-cluster owners (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`. Dedicated branch: `phase/e42-atomic-journal-owner-transitions-20261029`, stacked on E41 reporting source `50a1003dea55dec2ac23d4f5e38779593bccb01c`. Product alignment: **Phase 4, steps 17–20**, not a ninth Phase. E41 code source `d8c0a44584f1821e5ce91fa7a6a9f8875c0783ff` passed [CI #40840049529](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40840049529), 358 tests in each cloud/candidate gate, zero failures/errors/skips. E41's subsequent documentation-only HEAD has a separate verification run; never claim E42 PASS from E41 CI.

## Verified baseline and precise gap

- `RoomUpdateTaskStore.replace` uses a transactional full-payload state/revision CAS, and `UpdateRunner` relies on it as its durable write-ahead marker.
- E41's `UpdateTaskClusterOwnershipRepository` validates a signed private source and creates `UPDATE_TASK` owner references explicitly. Its separately called `promoteAfterReview` and `releaseAfterTerminal` are sound at the Room level but a crash *between* journal transition and reference transfer may leave a stale UPDATE_TASK owner (or need later reconciliation).
- Some existing tasks have no private-cluster association. They must continue through the current journal process unchanged; neither invent a cluster nor treat original-app paths as verified private content.
- All owner types are stored in Room v6; E38 file-ledger is not an authority for deletion. `main` and preceding Draft PR branches remain unchanged.

## E42 implementation in small verified steps

1. Add optional `atomicOwnerTransitions` behavior in `RoomUpdateTaskStore`, enabled **by default for production**, with `false` only in tests intentionally exercising E41's explicit post-journal reference APIs. Never change `UpdateTaskStore` interface or its `requireSuccessorOf`/revision/pause semantics.
2. Within the **same existing Room transaction** and *only after successful exact CAS*, examine the task's `UPDATE_TASK` and `REVIEW_REQUIRED` references scoped to `taskId + engineKey`. Require at most one of each; reject inconsistent generations. On transition into `REVIEW_REQUIRED`, if an UPDATE_TASK reference exists, insert/verify the matching REVIEW_REQUIRED reference then release UPDATE_TASK. If neither exists, preserve legacy no-cluster behavior; do not synthesize an owner.
3. On transition into any terminal state, release only an existing matching UPDATE_TASK reference (if any) after the durable journal write. Preserve REVIEW_REQUIRED/ROLLBACK/CLONE owners without exception, including REVIEW_REQUIRED→SUCCEEDED recovery. Contradictory review generation or ambiguous owner count must roll back the journal update, not silently discard evidence.
4. Keep ordinary nonterminal transitions free of owner changes and never alter the remote provider's `update`, `install`, `launch`, split capability or private APK bytes.
5. Add Robolectric native-SQLite tests: REVIEW_REQUIRED marker+owner move are atomic; terminal release is atomic; no-ref legacy tasks work; stale CAS doesn't change refs; injected SQL INSERT/DELETE failure rolls back **both** journal and owner changes; conflicting generations/multiple owners fail closed; other owner types survive DB reopen. Existing E41 explicit-method tests remain strict and use a disabled *test-only* option where needed to isolate their separate post-journal API behavior.
6. Add a fail-closed E42 source audit plus a dedicated E42 GitHub Actions workflow using the pinned SDK36/JDK21 environment and `./scripts/check.sh`, `./scripts/check.sh --with-engine-candidate`, signed APK/lint/permission/ABI/single-APK/no-GC checks. Capture test XML and a generated Room v6 schema artifact for independent comparison. Commit logic and tests before claiming a PASS.
7. Update STATUS/PRODUCT_PROGRESS/results only from exact-SHA CI. Product score stays **28.00%** unless true product acceptance is independently proven.

## Acceptance and hard exclusions

- Each successful retained task state transition is atomic with the matching reference transfer/release, and any failure rolls back journal and owner mutation together; stale revisions cannot move references.
- No source association is introduced by this change: only E41-verified/private-cluster owners are affected. No split APK import or real guest launch, background account use, UID sandboxing, session continuity, automatic cluster GC, firmware/device acceptance, release APK or completed Phase is claimed.
- Do not force push, merge Draft PRs, suppress CI/tests, fabricate APK fixtures as evidence of third-party app execution, or delete any clone/session data.
