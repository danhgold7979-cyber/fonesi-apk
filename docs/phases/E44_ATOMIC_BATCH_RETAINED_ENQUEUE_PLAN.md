# E44 — Atomic multi-target retained private APK enqueue (pre-code plan)

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`. Development branch: `phase/e44-atomic-batch-retained-enqueue-20261029`, stacked on E43 reporting HEAD `785cb956ce3f193c8e5f1b05dfc569470522b265` and [Draft PR #16](https://github.com/danhgold7979-cyber/fonesi-apk/pull/16). E43 bounded source `0579d7ebee2ef829347d77d6b3169255f1d8e92c` passed [CI #40843854486](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40843854486) with 374 tests per cloud/candidate gate. Its reporting HEAD has a distinct run and must be checked separately.

## Product alignment and gap

Original **Phase 4, steps 17–20** (source integrity, persistent queue, selected/all update scope and interruption-safe state); not Phase 8 or a new product roadmap. E43 atomically enqueues/retains **one** already published signed private source. A multi-selection/all request needs a batch-wide transaction so that a failure after the first target cannot leave half the tasks/owners committed. The existing UI/normal UpdateRequestService does not yet acquire published private-cluster sources; it must NOT be silently switched to this API before a verified handoff exists.

## Planned implementation, tests and acceptance

1. Preserve E43 single-target public path and its cryptographic `PrivateApkStore.open` readback. Add an explicit opt-in `enqueueBatch(engineKey, targets, scope, store, platformApi)` where each request pairs an exact `UpdateTarget` with a canonical published private-cluster ID; support multiple targets/sources without selecting by package alone.
2. Read back **every** source with `PrivateApkStore.open` before any Room mutation; require per-target exact package/version/signer match, valid engine/clone scope, and no duplicate update dedup identity. Only use the verified private source paths, never original caller APK paths. The internal verified seam is for controlled tests after source authentication; no public unverified path may acquire ownership.
3. Commit the **entire batch** under one outer Room transaction, composing existing E43 per-task atomic queue+UPDATE_TASK owner operations. A later task's active/owner/generation conflict or SQLite abort rolls back all *new* tasks/references from this call. Existing owners, unrelated journal tasks and CLONE/ROLLBACK/REVIEW_REQUIRED references are not overwritten.
4. Add native-SQLite Robolectric regression tests for multi-source success and DB reopen; idempotent replay; second-owner SQL failure/rollback and clean retry; second-task generation conflict after first would enqueue; duplicate dedup identity; second-source mismatch before write; same package with two PER_CLONE targets; unpublished/malformed store; in-progress task. Use synthetic audited metadata **only** at an internal test seam. Public API always verifies actual signed private APK bytes.
5. Add E44 source audit plus dedicated cloud/candidate workflow. Preserve Room v6 schema, pinned JDK/SDK, build/lint/signature/permission APK audits, E40–E43 audits, split-disabled candidate and no destructive GC. Do not weaken any tests or assume runtime acceptance from these checks.
6. Commit implementation and run the exact-source GitHub Actions gates; record the test count/artifact/log evidence before marking E44 technical PASS. Update docs/STATUS.md, docs/PRODUCT_PROGRESS.md and E44 results from evidence only. Keep `main` and parent Draft PRs untouched.

## Explicitly excluded

No automatic connection to the existing update UI or engine runner, no provider install/launch/update, no real guest Activity, no split import into candidate provider, no UID/account-session proof, no phone test, no APK deletion or release signing. **Product completion remains 28.00% (~30% rounded)** with zero accepted third-party cloned apps; E44 only closes a narrower batch consistency risk.
