# E41 — Update-task/private-cluster retention: verified results

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`.
Branch: `phase/e41-update-task-cluster-ownership-20261029`; stacked Draft PR [#14](https://github.com/danhgold7979-cyber/fonesi-apk/pull/14), parent E40 Draft PR #13. Main unchanged.

## Exact-source acceptance evidence

- Commit SHA `d8c0a44584f1821e5ce91fa7a6a9f8875c0783ff`, [E41 workflow #40840049529](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40840049529): **SUCCESS** on the same commit.
- Workflow job `task-cluster-ownership`: source/architecture audits, pinned Android SDK36/JDK21 bootstrap, `./scripts/check.sh`, `./scripts/check.sh --with-engine-candidate`, and no-split/no-GC boundary audit all **SUCCESS**.
- CI log reports **358 tests passed** in each cloud and candidate gate (0 reported failing steps). All APK build, lint, signature, permissions and candidate ABI guard steps completed successfully. Do not infer Android guest execution from Robolectric/JVM tests.
- Artifact: `fonesi-E41-update-task-cluster-owner-TEST-ONLY`, ID `12882636857`. Artifact upload succeeded.
- Room schema stays at **v6**; E41 introduces no database migration.

## Code and tested invariants

- Explicit `UpdateTaskClusterOwnershipRepository`: `PrivateApkStore.open` re-verifies a signed private source cluster before an UPDATE_TASK reference is acquired.
- Task ID/engine namespace, queued state, immutable source package/version/signers and owner uniqueness must match; incorrect, ambiguous or unpublished images fail closed.
- After a durable REVIEW_REQUIRED journal marker, a transaction promotes the matching UPDATE_TASK reference to REVIEW_REQUIRED without dropping the source on failure or conflicting generations.
- Terminal task states alone authorize explicit UPDATE_TASK release; REVIEW_REQUIRED and ROLLBACK owners remain retained. Additional tests guard conflicting review generation and injected SQLite delete failure, preserving source and journal.
- Production queue/update runner is **not yet wired** to these explicit reference APIs; this milestone is a bounded Room retention contract, not automatic source lifecycle orchestration.

## Limits and next work

E41 does not publish or activate clusters, import split APKs into the candidate, execute a real third-party Activity, isolate independent Android UIDs/accounts, verify update-session preservation on phones, delete clusters, or sign a release APK. Product Phase 4 is not complete. Next work must have a new pre-code plan and its own test gate; do not merge Draft PR #14 into main solely on this CI evidence.

Product readiness remains **28.00%** under `docs/PRODUCT_PROGRESS.md` (~30% rounded).
