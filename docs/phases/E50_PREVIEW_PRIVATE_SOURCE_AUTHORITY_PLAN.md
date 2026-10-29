# E50 — Surface private-source update authority conflicts in preview (pre-code plan)

Date 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`, development branch `phase/e50-preview-private-source-authority-20261029`, based on E49 source `23ea57d65fd562d5cc611040e2e8791f6462779c`. The parent E49 [CI #40851203055](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40851203055) was pending at plan creation; its acceptance is independent. This milestone belongs to the original **Product Phase 4, steps 17–20**, NOT a new product phase.

## Problem and dependencies

E47 prevents original-installed-app update requests from being enqueued when active readback-retained private source owners exist, but its `UpdateRequestService.preview` is built entirely from original `SourceCatalog` and may describe such a protected package as ready to update. The user can be offered a confirmation that will fail, and an auto preview can report inaccurate eligible targets. The existing transaction-time E47 conflict check MUST remain as the authoritative no-race execution guard; preview is advisory and cannot authorize an update.

## Implementation and test order

1. Validate current E49 branch/commit/PR and the E45–E49 source gates; read `UpdatePlanner.kt`, `UpdateRequestService.kt`, `UpdatePreviewDialog.kt` and native SQLite tests. Keep parent branches untouched.
2. Add dedicated `UpdateBlockReason.PRIVATE_SOURCE_ACTIVE` and a clear Vietnamese preview label. Do not invent compatibility support or turn a pending task into success.
3. Under the preview's existing queue lock, read durable active tasks. For any current engine/package where a nonterminal task has `retainedSource=true` or E41 UPDATE_TASK/REVIEW_REQUIRED Room owner, refuse the original-app target and display a blocked reason for every selected clone of that package. Reuse `RoomLegacyUpdateSourcePolicy`, fail closed if Room task/owner records cannot be read. Keep ordinary legacy queues and unrelated packages actionable.
4. Do not weaken E47 `confirm` transaction. Preview re-capture must invalidate stale consent if an active private conflict appears later; confirmation of another safe package may still proceed.
5. Add negative native-SQLite/Robolectric test cases: marked private target, pre-marker owner-only cases, unrelated package still confirmable, stale preview invalidation, terminal marker no longer blocks. Preserve existing tests and no destructive migration. Add E50 source audit and full cloud/candidate workflow with prior E40–E49 audits, APK/lint/permissions/signature, Room v6, no split/provider/GC.
6. Commit and push reviewable source/test, verify exact SHA workflow and all test reports before technical PASS; update results and `docs/STATUS.md`, `docs/PRODUCT_PROGRESS.md` and execution plan only from evidence. Keep Draft PR and main untouched.

## Scope/acceptance

An active retained/private source is visible as blocked in preview and absent from original-app actionable targets, without affecting unrelated targets. Historical/terminal tasks do not block by authority alone. Previews cannot replace transaction-time revalidation and do not import/activate an APK. No proof of actual guest Activity, full split provider, separate Android UID/social login sessions, phone tests or release signing; weighted product readiness **28.00%**, real third-party app acceptance **0**.
