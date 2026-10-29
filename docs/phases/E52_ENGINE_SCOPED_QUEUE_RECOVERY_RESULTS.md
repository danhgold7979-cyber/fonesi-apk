# E52 — engine-scoped shared queue execution and recovery results

Date 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. Original **Product Phase 4, roadmap steps 18–20**, not a new product Phase.

## Exact source and CI

- Branch `phase/e52-engine-scoped-queue-recovery-20261029`; [Draft PR #26](https://github.com/danhgold7979-cyber/fonesi-apk/pull/26), stacked on [E51 Draft PR #25](https://github.com/danhgold7979-cyber/fonesi-apk/pull/25) source `4c7c3a24e7201278d4fc5ce72120c881d21ddf40`, which passed [CI #40853007668](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40853007668) with **419 tests in each cloud/candidate gate**.
- E52 pre-code plan committed as `ddd45ed49d59ad5783af87f989df5ea3149f9b1d`, [plan](E52_ENGINE_SCOPED_QUEUE_RECOVERY_PLAN.md). Code commit **`59bf73522b3a86c1caca57af8613bb2e7b915b2b`**.
- E52 [GitHub Actions #40853790848](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40853790848) **completed SUCCESS on exact code SHA**. Both cloud and candidate gates reported **423 tests**, 0 failed workflow steps, full build/lint/signature/permission APK audits, pinned candidate SDK and source checks E40–E52. Source/provider safety checks verified no automatic split import, guest activation or destructive APK cluster GC.
- [Artifact #12889195111](https://github.com/danhgold7979-cyber/fonesi-apk/actions/artifacts/12889195111) holds test-only Room v6 schema and sanitized verification/test receipts, not a production release APK.

## Implementation and regression scope

`core/src/main/kotlin/vn/fonesi/core/update/UpdateRunner.kt`: the task list is filtered to `engineKey` before selecting queued work and checking any active/review same-package predecessor. `recoverLocked` also filters to that engine before invoking source authority, discarding snapshots or changing journals. Cross-engine task state, uncertain outcome, journal revision and snapshots are preserved rather than falsely producing ENGINE_CHANGED or recovered success. Global queue lock/pause remain shared.

`core/src/test/kotlin/vn/fonesi/core/update/UpdateRunnerTest.kt`: five negative JVM regressions replace the unsafe expectation of rewriting foreign tasks. They exercise foreign-only QUEUED; foreign QUEUED ahead of local same-package; foreign PREPARING/snapshot across recovery and local processing; foreign APPLYING with matching local version readback; foreign REVIEW_REQUIRED not blocking local. The existing E51 retained source collision guard remains unchanged.

`scripts/audit_e52_engine_scoped_queue_recovery.py` and `.github/workflows/e52-engine-scoped-queue-recovery.yml` provide E52 pin/source gates with E40–E51 audits.

## Acceptance limits

**Only bounded E52 technical PASS.** Weighted readiness stays **28.00% (~30% rounded)**; **0** real accepted third-party apps. Room v6, `supportsSplits=false`, `importsSplitApks=false`; no production guest Activity/Service or split provider; no independent Android UID/account session proof; no validated physical Samsung/other handset update retention, no release signing and no actual clone APK acceptance. `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` unchanged; PR #26 remains Draft. The later report-only commit has a distinct HEAD and must run its own CI before that SHA can be marked tested.

The parallel prototype branch `phase/e51-engine-scoped-queue-recovery-20261029` is superseded; do not merge both. Continue the original Phase 4/engine eligibility work only after planning and validating any production provider/feature without fabricating guest execution.
