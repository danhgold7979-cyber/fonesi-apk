# E54 — unblocked queue selection results

Date: 2026-10-29. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Product scope: **Phase 4, steps 18–20**. E54 is a technical milestone, not a new product Phase.

## Implementation

- `UpdateRunner.runNext` first filters QUEUED candidates using the existing engine namespace, same-package predecessor and REVIEW_REQUIRED barriers.
- It then selects the first candidate for which `mayUseLegacySource` succeeds. This allows unrelated original-source work to proceed when an earlier private retained-source task is quarantined.
- If no candidate qualifies because all otherwise eligible work is authority-blocked, the runner returns `PolicyBlocked`; it does not report success or silently fall back to a private APK source.
- Added three focused JVM regressions for retained-source head-of-line starvation, older owner-only denial, and a distinct-version same-package task that must not bypass its protected predecessor. Updated the foreground regression to assert unrelated package work proceeds while retained work remains queued.
- Updated E44/E45/E52 source audits to enforce the revised policy-filtered selection invariant; added the E54 audit and dedicated pinned cloud/candidate workflow.

## Verification — PASS

**Exact tested source SHA:** `6b03fbca63d0e4bc83e96296f568e9aad13eec51`.

**GitHub Actions:** [run #40861102493](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40861102493) — **SUCCESS**.

- E40–E54 static safety audits: PASS.
- Cloud test/build/lint/APK signature and permission gate: PASS.
- Candidate test/build/lint/APK signature and permission gate: PASS.
- Split/provider activation/garbage-collection guard: PASS.
- Test-only artifact: [artifact #12892006579](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40861102493/artifacts/12892006579).

The initial attempts exposed outdated static audit assertions and a foreground test that assumed a blocked queue head must block unrelated work. Those checks were updated to match E54's intended invariant; the final run above passed on the exact source SHA.

## Boundaries that remain open

No production private-source update adapter, full third-party split import, guest Activity registration, separate kernel UID/account session, physical phone update test or release signing is established by this queue-selection change. Keep `supportsSplits=false` and `importsSplitApks=false`. Product readiness remains **28.00%**, with **0** accepted real third-party clone apps. Keep cumulative technical PRs Draft and do not merge/release as a finished clone product.
