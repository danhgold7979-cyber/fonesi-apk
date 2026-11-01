# E44 — Atomic batch retained private APK enqueue: same-source CI results

Date: 2026-10-29. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Branch `phase/e44-atomic-batch-retained-enqueue-20261029`; stacked [Draft PR #17](https://github.com/danhgold7979-cyber/fonesi-apk/pull/17) on E43 PR #16. This is a bounded technical slice of original **Phase 4, steps 17–20**, not a new product Phase.

## Verified technical acceptance (code SHA)

- Exact verified implementation SHA: **`9d7270b207a6dd82e50befa484850c5360bd740f`** — [E44 GitHub Actions #40845568917](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40845568917) **SUCCESS**. Its single job passed E40–E44 source audits, pinned toolchain bootstrap, cloud and candidate test/build/lint/signature/permission gates, explicit single-APK/split-disabled and no-destructive-GC checks, plus artifact upload.
- CI logs: **382/382 tests per cloud gate and 382/382 per candidate gate**, **0 test failures** (check.sh refuses zero-test and test failures). Both gates printed `PASS: 382 tests; build artifacts, lint and APK signature/permissions verified.`
- Artifact: `fonesi-E44-atomic-batch-retained-enqueue-TEST-ONLY`, GitHub Actions artifact **12885610783** (sanitized test receipts and Room schema v6). [View workflow artifacts](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40845568917). Tests are cloud/native-SQLite Robolectric and JVM tests; they are not Android phone, social-app/account, or signed release acceptance.
- New E44 native-SQLite `PrivateClusterRetainedBatchEnqueuerTest`: **8 focused test methods**. Validates two-target batch success/readback after database reopen; duplicate idempotence; second-owner SQL abort with all new writes rolled back and clean retry; second-generation conflict after first would enqueue; second-source mismatch; duplicate dedup rejection; separate PER_CLONE records sharing a verified cluster; unpublished or malformed ID; in-progress task rollback.
- No Room migration: `FonesiDatabase` remains v6. Candidate continues `supportsSplits=false` and enforces one APK. Neither new code nor existing task API invokes a guest engine or deletes a private cluster.

## Exactly what changed

`RetainedClusterTarget` pairs each update target with its explicit immutable private-cluster ID; the public `PrivateClusterRetainedEnqueuer.enqueueBatch` reopens and cryptographically audits **all** published clusters before the first Room mutation. It rejects mismatched package/version/signers, invalid clone scope, noncanonical cluster IDs, duplicate task identities and conflicting active tasks/cluster generations. Prepared sources use verified private APK paths rather than caller paths. The outer Room transaction groups all existing E43 single-target task+owner operations, ensuring any later failure rolls back all *new* rows in the batch.

The source-readback bypass is internal/test-only and does **not** demonstrate APK signatures from synthetic fake files. The public API uses `PrivateApkStore.open` on real signed private bytes. No UI or normal `UpdateRequestService` automatic connection is made; no production provider update is performed. Ownership and task states must still be independently reverified before any future real mutation.

## Product blockers and next step

Original product rubric stays **28.00% (~30% rounded)**; required real app clones accepted: **0**. Full base/split provider import, real guest Activity/framework services, separate UID/account isolation, third-party login/session updates, Samsung/device acceptance and release signing are not proven. E45 should be scoped by a new plan and should not silently route legacy UI requests through an unaudited source. Preserve Draft PRs and do not merge to `main` without product acceptance.

The documentation/clarifying-comment follow-up commit has its own SHA; its CI must be checked separately before stating that the new reporting HEAD passed.
