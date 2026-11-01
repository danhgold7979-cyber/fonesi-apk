# E51 — Atomic private-source enqueue conflict with active original-app tasks (pre-code plan)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. Branch `phase/e51-retained-enqueue-original-source-conflict-20261029`, based on E50 implementation SHA `37ff68f4cd51582cababccaa01bfc77b505f9ea3`, whose exact-source [CI #40851899007](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40851899007) was running when the plan was committed. Original **Product Phase 4, roadmap steps 17–20**; E51 is not a new product Phase.

## Source ownership conflict not yet covered

E47's `UpdateRequestService.confirm` blocks *original-app enqueue after an active retained/private task*. But the reverse order is still possible: `PrivateClusterRetainedEnqueuer.enqueueReadBackVerified` calls `UpdateQueue.enqueue` under a Room transaction without first inspecting active legacy/original-app tasks for the same engine/package. If the original task is for a different version (and therefore a different dedup key), the private source is retained alongside an existing original-source task. Both can survive in one queue and the original task might run first. This violates source-authority consistency even though the existing per-dedup conflict guard works.

## Ordered implementation

1. Verify exact E50 branch/CI, E47 original-confirmation gate and existing E43/E44 private retained enqueue code, existing native SQLite tests and Room owner transitions. Do not change the E50/49/48 source branches while CI is running.
2. Inside the **same Room transaction** as `PrivateClusterRetainedEnqueuer.enqueueReadBackVerified`, before any new task insert, reject another same-engine/package **nonterminal task with `retainedSource == false`**. Treat old E41 owner-associated pre-marker tasks conservatively; do not reinterpret their source authority. Any corrupt task decoding or database read must abort. This is a source-kind invariant, not a blanket ban on valid independent per-clone retained tasks.
3. The batch API must inherit the same rule via its existing nested transactional composition: if a later target conflicts, earlier newly inserted task/owner rows roll back. Preserve pre-verified `store.open` signature/version readback, owner ID immutability and dedup behavior.
4. Add native SQLite/Robolectric regressions for original-app task at a different version, batch conflict on a later item (all-or-nothing), old pre-marker owner source, terminal legacy task no longer blocking private enqueue, and separate PER_CLONE tasks sharing a legitimate retained cluster. No fabricated APK signatures or runtime provider claims.
5. Add E51 deterministic source audit and pinned cloud/candidate GitHub Action running E40–E51 audits, core, Room/native-SQLite tests, lint, APK signature/permissions and no-split/no-GC gates. Commit code/test, verify exact SHA/CI with logs and sanitized artifacts before technical PASS. Update E51 results, STATUS, PRODUCT_PROGRESS and EXECUTION_PLAN on actual evidence.
6. Keep Draft PR/branches unmerged to `main`; weighted product score **28.00%**, real third-party cloned apps accepted **0**. Guest Activity, real independent account/UID sessions, split APK import and device acceptance remain blocked.

## Acceptance

For a given engine/package, original-app work cannot be added after active private retained work (E47), nor private retained work after an active original-app task (E51), including unequal versions and differing dedup identities. Ordinary separate per-clone retained tasks and terminal history remain supported. Room remains v6; no provider, installer, shortcut or destructive GC is invoked.
