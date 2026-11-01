# E56 — fail-closed clone stop identity revalidation under the operation lock (pre-code)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`. Technical branch `phase/e56-stop-clone-identity-revalidation-20261029` based on E55 report HEAD `f2c6d9aa65b4d91a04541cd051daa1e46def7d80`. Stack Draft PR on E55 [#29](https://github.com/danhgold7979-cyber/fonesi-apk/pull/29), keep E54 [#28](https://github.com/danhgold7979-cyber/fonesi-apk/pull/28) and `main` unchanged. E55 technical code/test SHA `19455ad0aa1b4b5cdf6867b3248bd5819f0fda34` passed [CI #40864637583](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40864637583) with 436 tests in each cloud/candidate gate. E55 report HEAD has its own CI and must not inherit the code SHA's result.

## Original roadmap and defect

This slice belongs to **Product Phase 3, steps 14–16** (stable clone identity, lifecycle and restart reconciliation), with a Phase 4 update-in-flight safety dependency. It is technical E56, not Product Phase 56.

`AppsViewModel.stopClone(token)` resolves `currentClone(token)` *before* acquiring the operation lock, then checks only whether the row found by the token still has the same `cloneId`. A changed package, cluster generation or metadata identity that preserves the ID could pass this narrow check. The method then calls `StoppableCloneEngine.stop(cloneId)` without confirming that the engine's live record still names the expected package. Such races must fail closed rather than risk stopping a different guest. `ManagedGuestEngine.stop` has a provider inspect receipt, but the UI must not dispatch an identity-mismatched stop.

## Plan before changing code

1. Recheck the exact E55 parent SHA, Draft PR/Actions, `docs/ROADMAP.md`, `docs/CODE_TEST_RULES.md`, `AppsViewModel.stopClone/currentClone`, `CloneMetadataRow`, Room DAO, `ManagedGuestEngine.stop` and existing tests. Commit this plan and the E56 entry in `docs/EXECUTION_PLAN.md` first.
2. Add a narrow internal `CloneStopIdentityGuard` that fails closed unless the row read back under the operation lock matches the expected metadata identity and launch token, the active engineKey, and the engine's unique live clone record (ID plus package). Fail for missing/duplicate engine records, stale token, changed package/cluster or namespace and missing target. Do not seed a clone or infer one from the source app.
3. Invoke the guard inside `AppsViewModel.stopClone` **after** acquiring `operationLock` and **before** the existing journal blocker and `StoppableCloneEngine.stop`. Preserve the existing conservative update-in-flight/review prohibition (including foreign legacy records); don't widen execution eligibility or drop the OS operation lock.
4. Write focused JVM policy tests for exact match, stale token/engine/package/cluster, duplicate/missing live records; add native SQLite test that updates a test-only metadata row between capture and locked readback and proves refusal without metadata loss. Do not pretend that this is an Android guest stop test.
5. Add a dedicated source audit `scripts/audit_e56_stop_identity_revalidation.py` and pinned E40–E56 cloud/candidate workflow with build/lint/APK signature and permissions, Room v6, split-disabled/no provider-activation/no-GC guards. Fix actual test/CI failures without weakening acceptance criteria.
6. Record exact code SHA, CI receipt and tests in E56 results; update STATUS, PRODUCT_PROGRESS and PR. Product readiness stays **28.00%**, accepted real third-party clones **0** unless new runtime acceptance evidence exists.

## Acceptance and safety boundary

- Stale metadata/token and an engine record with a wrong package, duplicate ID or missing clone can never reach `StoppableCloneEngine.stop`.
- Valid current identity can still reach the existing stopped-state receipt code; the guard itself neither changes Room schema nor mutates guests.
- Global lock, queue review barriers, per-engine queue controls, retained APK references and stable shortcut tokens remain intact.
- No claim of production guest Activity, split APK import, OS UID/account isolation, Samsung/physical-device lifecycle verification or release signing. Keep `supportsSplits=false`, `importsSplitApks=false`; do not merge/release as finished clone product.
