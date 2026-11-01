# E55 — engine-scoped queue controls and read-only foreign journal history (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`. Development branch: `phase/e55-engine-scoped-queue-controls-20261029`, based on E54 reporting source `7d5983ef739e803c698f1ef1d2ffbe53e03fe550`; stack Draft PR on E54 [#28](https://github.com/danhgold7979-cyber/fonesi-apk/pull/28). E54 technical **code/test PASS** at `6b03fbca63d0e4bc83e96296f568e9aad13eec51` ([CI #40861102493](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40861102493), SUCCESS). Newer E54 report-only commits have distinct source SHAs and require independent CI. Original **Product Phase 4, steps 18–20**; E55 is not a new product Phase.

## Defect and source of truth

The durable Room journal is shared across engine namespaces. E52 protects runner execution/recovery and E54 protects source-authority selection. However, `UpdateQueue.cancel(id)` and `retryDeferred(id)` search the global journal only by task UUID, so a request from today's engine can cancel or requeue another engine's still-active/retained task. `AppsViewModel.cancelTask`/`retryDeferred` invoke those unscoped APIs; the task UI shows every engine's saved journal and currently enables action buttons for foreign tasks. This violates engine identity and may strand historic private APK ownership. Global pause is a deliberate shared queue control and stays global.

## Pre-code implementation sequence

1. Confirm E54 latest HEAD, Draft PR, exact code CI and product rubric. Review `UpdateQueue`, `AppsViewModel`, `MainActivity`, `ManagementCards`, core queue regressions, native Room store and applicable audits. Commit this plan and `docs/EXECUTION_PLAN.md` before Kotlin source edits.
2. Change the public core control methods to `cancel(engineKey, taskId)` and `retryDeferred(engineKey, taskId)`. Require a nonblank expected engine key and require `current.engineKey == engineKey` **before** reading task eligibility or issuing a CAS. Return false for a missing/foreign task and never change it. Do not leave an unscoped public overload.
3. Wire the ViewModel to pass `services.engineKey` explicitly. Show foreign-engine historical task rows for diagnosis, but disable cancel/retry buttons and explain that they are read-only from the active engine. Keep same-engine task controls and all global pause semantics unchanged.
4. Update core call sites and add regressions for foreign QUEUED cancellation refusal, foreign DEFERRED retry refusal, same-engine successful control after foreign denial, and unknown/review/active tasks never being modified. Add a native SQLite Room integration test for cross-engine journal survival and reopening where feasible.
5. Add `scripts/audit_e55_engine_scoped_queue_controls.py` asserting the enforced namespace guard, required regression presence, and UI wiring. Add a dedicated pinned E40–E55 cloud/candidate GitHub workflow (SDK36, JDK21, Gradle, tests, lint, signed APK/permissions, no split/provider/GC). Correct actual failures, re-run exact-SHA CI; record counts/links in results before declaring E55 technical PASS.
6. Update `docs/STATUS.md`, `docs/PRODUCT_PROGRESS.md`, E55 results and Draft PR without changing the formal weighted product rubric absent accepted app/runtime evidence. Do not merge to `main` or publish a finished clone app.

## Acceptance criteria and exclusions

- An engine cannot cancel, requeue or silently consume any foreign `UpdateTask` using only its UUID, even when the foreign task is QUEUED or DEFERRED.
- Foreign rows stay visible as historical read-only diagnostics, and their engine namespace is distinguishable; legitimate same-engine controls still work.
- No change in persisted Room v6 schema, global pause semantics, journal ownership transitions, retained-source no-fallback, or same-package E54 scheduling barriers.
- No claim that original/social guest APK installation, complete split import, real guest Activity, independent kernel UID/account isolation, session retention, physical phone test or release APK is complete. Keep `supportsSplits=false`, `importsSplitApks=false` and **28.00%** weighted product estimate with **0** accepted real third-party clones.
