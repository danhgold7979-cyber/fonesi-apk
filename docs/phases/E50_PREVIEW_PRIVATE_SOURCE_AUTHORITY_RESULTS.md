# E50 — Private-source conflict preview: exact-source test results

Date 2026-10-29. Repository [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk).
Development branch `phase/e50-preview-private-source-authority-20261029`, [Draft PR #24](https://github.com/danhgold7979-cyber/fonesi-apk/pull/24) stacked on E49 Draft PR #23. Original **Product Phase 4 steps 17–20**.

## Verified technical evidence

- Implementation SHA **`37ff68f4cd51582cababccaa01bfc77b505f9ea3`** matched the head SHA of [GitHub Actions E50 #40851899007](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40851899007), conclusion **SUCCESS**.
- Both cloud and candidate workflows logged **`PASS: 415 tests; build artifacts, lint and APK signature/permissions verified.`**, including prior E40–E49 audits and E50 source-audit check, unchanged Room schema v6, disabled split guest imports, and no destructive private-file GC.
- Sanitized test-only artifact ID **12887767238** `fonesi-E50-preview-private-source-authority-TEST-ONLY`, available on the run. It is **not** a deployable signed product release.
- Four new native-SQLite/Robolectric test methods verify protected marked private source displayed as blocked with unrelated package still actionable, old E41 owner-only UPDATE_TASK/REVIEW_REQUIRED references blocked without mutation, stale preview invalidated on a newly active private source, and terminal private history not blocking original-source preview.
- E49 parent integration has its own exact-source CI [#40851203055](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40851203055) (407 tests per gate) **SUCCESS**. E50 test evidence does not imply E49 was merged to main.

## Behavioral boundary

`UpdateRequestService.preview` consults durable `RoomLegacyUpdateSourcePolicy` and removes original-app actionable targets for nonterminal retained/private owners of the same engine/package. The UI displays `UpdateBlockReason.PRIVATE_SOURCE_ACTIVE` in Vietnamese and still permits a safe unrelated package. `confirm` retains independent E47 transaction-time revalidation to protect concurrent owner changes. Invalid/unreadable database task/ref reads fail closed. No runtime provider operation or split import is performed.

This is **technical CI PASS**, not a social-app account/guest Activity/phone acceptance. Weighted product readiness stays **28.00% (~30% rounded)**, real third-party app clones accepted **0**; no Phase 4 product completion, release or merge to `main`. Reporting SHA written after the implementation needs a separate exact-HEAD CI run.
