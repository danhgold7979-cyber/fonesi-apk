# E57 — serialize clone rename/restyle with engine operation lock (pre-code)

Date 2026-10-29. Repo `danhgold7979-cyber/fonesi-apk`, branch `phase/e57-clone-presentation-operation-lock-20261029` stacked on E56 [Draft PR #30](https://github.com/danhgold7979-cyber/fonesi-apk/pull/30) reporting HEAD `5f8dbd2991f31cb9ba7ea3c8d6831c230d86d400`. E56 code/test `add204f2122defab4c71231b83d74f82ad7c7086` passed [CI #40867094635](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40867094635), **441 tests per cloud/candidate gate**; E56 reporting CI #40868129725 was in progress at planning. Original **Product Phase 3 steps 14–16** clone lifecycle, plus **Phase 6 step 25** metadata presentation. E57 is a technical slice, **not** Product Phase 57.

## Defect and evidence

E56 introduces a lock-time immutable clone identity readback before `AppsViewModel.stopClone` sends a provider stop. `CloneMetadataRepository.rename` and `restyle` write the same Room row's `changedAt`, `label`, `groupName`, `accentKey` and `badgeText` **without acquiring** the shared OS operation lock. A presentation edit can therefore mutate the row while an E56 stop is executing. These writers must coordinate with the same operation lock (also used for create, delete and the queue). Do **not** lock the entire UI or change the ability to rename/style a saved clone when engine unavailable. API is synchronous and already intended to run on an IO dispatcher.

## Plan and proof

1. Read current E56 source/CI, `docs/ROADMAP.md`, `docs/CODE_TEST_RULES.md`, `CloneMetadataRepository`, `AppsViewModel`, native SQLite tests and `FileQueueLock`. Commit this plan plus an E57 `docs/EXECUTION_PLAN.md` entry before code.
2. Add one narrow internal helper `mutatePresentation` to `CloneMetadataRepository`: acquire the injected OS `operationLock` fail-closed; perform exactly one existing Room `rename`/`restyle` mutation inside the lease; release it on success/failure. Preserve existing namespace/id guards and input normalization. Do **not** require a ready engine or call `mutateEngine` (which applies unrelated in-flight update restrictions), as offline presentation must remain usable.
3. Keep E56 stop lock/identity validator unchanged. Add Robolectric/native SQLite tests proving a held OS lock prevents rename/restyle and leaves Room unchanged, both operations succeed after release, wrong engine ID cannot mutate another namespace, and the lease releases after a failed mutation.
4. Add `scripts/audit_e57_clone_presentation_lock.py`, checking the lock scope and guarded writers, regression tests and Room v6/split-disabled. Add pinned E40–E57 dedicated cloud/candidate workflow; run same-source tests/build/lint, APK signatures/permissions and E40–E57 audits, no provider/split/GC activation.
5. After exact-source CI success, document results, current SHA, PR, product score and blockers. Keep Draft/keep main unchanged; never mark E57 PASS from writing code alone.

## Limits

This covers **only the cooperating rename/restyle writers**, not an atomic lock across external/uncoordinated Room modifications or `CloneClusterOwnershipRepository.associate` (which currently does not take the same OS lock). Do not claim full guest transaction safety or run actual third-party guest code. `supportsSplits=false`, `importsSplitApks=false`, **28.00%** weighted readiness, **0 accepted third-party clone apps**. No merge/release as complete product.
