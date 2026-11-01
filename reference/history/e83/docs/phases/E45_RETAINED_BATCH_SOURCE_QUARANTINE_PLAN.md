# E45 — Integrate atomic retained batch with durable source-authority quarantine (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`. Development branch `phase/e45-retained-batch-source-quarantine-20261029`, based on **verified** E44 batch-report HEAD `cd2089568bbc20d886ff0f16ca218c0d872df7e6`, [E44 batch code & report CI #40845568917 / #40846706698](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40846706698). Reference the separate [E44 source-authority Draft PR #18](https://github.com/danhgold7979-cyber/fonesi-apk/pull/18), which was being re-tested on fix SHA `b5bd4d8c63f3ebf081f65ea095ef3bbdff93eb23` [CI #40847390931](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40847390931); **do not assume PR #18 PASS before its workflow is complete**. Pre-existing E44 batch is [Draft PR #17](https://github.com/danhgold7979-cyber/fonesi-apk/pull/17). E45 is one integration milestone, *not* a new product phase.

## Roadmap, problem and acceptance boundary

Original **Phase 4 steps 17–20**, updating verified sources in a durable queue. E44 batch code can atomically enqueue/retain verified private APKs, but without a durable source-authority marker and runner no-fallback guard from the parallel E44 quarantine work, the old UpdateRunner could consult a current original-app `SourceCatalog` instead. Never interpret private-store retention as permission to use the legacy update path. No accepted provider for arbitrary split/real third-party guest sessions exists.

## Plan, dependencies and code changes

1. Preserve the fully verified E44 batch source/API/test on `cd2089568bbc20d886ff0f16ca218c0d872df7e6`. Import narrowly scoped source-authority changes from PR #18: immutable `UpdateTask.retainedSource`, backward-compatible format-1 `TaskCodec` field, dedup collision prevention, queued ownership marker + Room reference transaction, `RoomLegacyUpdateSourcePolicy`, `UpdateRunner` no-fallback gate and `LocalServices` wiring. Import its tests and static audit; keep source references and candidate single APK/no-GC guards.
2. Resolve the overlap in `PrivateClusterRetainedEnqueuer.kt` **by hand**: keep E44 batch and its full readback-before-transaction behavior, plus E44 quarantine's `retainedSource=true` on the common single-target enqueue and queued-state marker guard. Do not copy either file blindly and erase the other's functionality.
3. Add E45 integration-specific native SQLite tests: every batch task is durably `retainedSource=true`, has a matching UPDATE_TASK owner, is denied original SourceCatalog by Room policy before and after DB reopen; mixed retained/legacy dedup fails closed and rolls back the entire new batch. Preserve all E43, both E44 suites without skipped or weakened assertions.
4. Add dedicated E45 audit and workflow running E40–E44 batch and source-authority audits, pinned cloud/candidate builds, unit tests, Robolectric, lint, APK signing and permission checks and no-provider/split/GC safety gate. Room schema must remain v6, no altered migrations, no app/runner automatic private update.
5. Commit source and tests, check **exact SHA** CI and artifact before technical PASS. Any failed CI is a blocker, not permission to drop tests. Publish STATUS/PRODUCT_PROGRESS/results only from verified evidence; preserve Draft PR review and stack on PR #17. Do not merge old Draft PRs into `main`.
6. E45 does not claim actual guest Activity, separate kernel UID/account isolation, full split provider, real social-app updates, Samsung acceptance or release signing. Product weighted rubric remains **28.00%**; accepted real apps **0**.

## Guard against parallel work

PR #18 and PR #17 are both named E44 but are independent technical paths; this E45 branch reconciles them without rewriting either history. Use explicit SHA/branch reads before copying PR #18 files; if PR #18 moves, re-review diff and rerun tests. Avoid force pushes and do not automatically close other PRs.
