# E42 — Atomic retained journal transitions: verified test results

Date: 2026-10-29. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk).
Branch `phase/e42-atomic-journal-owner-transitions-20261029`; stacked [Draft PR #15](https://github.com/danhgold7979-cyber/fonesi-apk/pull/15) on E41 Draft PR #14. Original product alignment: Phase 4, steps 17–20.

## Same-source CI acceptance

- Tested code SHA **`7f749ae4e54f01c23f11772430a992a050be36c0`** — [GitHub Actions #40841923812](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40841923812): **SUCCESS**. All steps passed, including E40/E41/E42 source audits, pinned Android SDK36/Gradle/JDK21, `./scripts/check.sh`, `./scripts/check.sh --with-engine-candidate`, signed APK/permissions/lint, provider single-APK and no-destructive-GC guards.
- Artifact `fonesi-E42-atomic-retained-journal-TEST-ONLY`, ID **12883786202**, was downloaded and inspected. Cloud and candidate JSON reports **each** show core 118, APK runtime 44, Android/Robolectric 204 = **366 passing tests**, 0 failures, 0 errors and 0 skipped tests.
- E42-specific native-SQLite Robolectric suite `RoomRetainedJournalTransitionTest`: **8/8 PASS**, covering review promotion, terminal release, no-reference compatibility, stale CAS/pause guard, injected SQLite insert/delete abort, ambiguous owner generations, reopened database and review hold after verified recovery.
- Room v6 generated schema SHA-256 **`5eba80cd1d64ac8e46a0aed5234c7967c0aa37d35e792427b9911130114bc52b`**: identical to the reviewed/committed v6 schema. No schema migration added.
- Cloud APK 0.5.0-cloud: 11,941,688 bytes, SHA-256 `d480e4b858b3b8153cd33f9e2dea53f4d88369f89521268ea9e0990673beea27`. Candidate APK 0.5.0-candidate: 18,046,460 bytes, SHA-256 `49ef670a2391b26feabaf096f82e65bf16c41244b3f6d1e0dbb76bcb2d5cdd55`. Lint warnings cloud 17/candidate 27, **zero lint errors**. These are debug/test artifacts, not signed release distribution.

## What the code actually changes

- `RoomUpdateTaskStore.replace` preserves exact successor/revision/pause CAS and commits a matching E41 `UPDATE_TASK` → `REVIEW_REQUIRED` reference promotion within the **same Room transaction** as a durable REVIEW_REQUIRED marker.
- Terminal transitions release only an existing matching `UPDATE_TASK` reference. `REVIEW_REQUIRED`, `ROLLBACK`, and `CLONE` references are retained. Any SQL failure, ambiguous reference count or cross-generation conflict rolls back the journal transition too.
- Unassociated legacy tasks do not gain fake private-cluster references; no APK is published, installed, activated, imported or deleted. E41 explicit post-journal methods retain separate tests for compatibility.

## Product limits and follow-up

This is a bounded **technical E42 PASS**, not Phase 4 acceptance. Cloud/candidate unit tests are not physical Android/Samsung account-session evidence. No actual third-party guest Activity, separate UID, Play Services, arbitrary split import, user account isolation, session-preserving update, real-device acceptance, destructive GC or release signing has been demonstrated. Original weighted product estimate remains **28.00%**, rounded ~30%, and accepted real social/e-commerce clones remain **0**.

The documentation-only follow-up commit has a new HEAD; inspect its own GitHub Actions run before claiming the reporting HEAD was tested. Keep Draft PR #15 and all parent PRs unmerged into `main`.
