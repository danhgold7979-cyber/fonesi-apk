# E58 — serialized private APK cluster association: results

Date: 2026-10-29. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Development branch `phase/e58-clone-cluster-association-lock-20261029` stacked on E57 reporting SHA `f4d534a233c1c65ab6408a4aef962a44c49a672d`. Original **Product Phase 3 steps 14–16**, plus **Phase 4 cluster owner/queue safety**.

## Baseline

E57 reporting [CI #40871698660](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40871698660) **SUCCESS** on source `f4d534a233c1c65ab6408a4aef962a44c49a672d`. E57 code/test [CI #40870539313](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40870539313) **SUCCESS**. E58 planning began at `c27912cd31f5a2dfe60fcf8739f1fee4a89fe0fb`; implementation changes are distinct from the verified E57 baseline.

## Implementation (code committed, E58 CI pending)

- `CloneClusterOwnershipRepository` now **requires a caller-provided real shared QueueLock**. Public `associate` holds it around **both** `PrivateApkStore.open` signature/hash readback and the Room transaction that changes CLONE owner references and metadata. Internal test-only `associateReadBackVerified` also acquires the lock; the locked transaction is private.
- `CloneMetadataRepository.delete` injects its already-shared `operationLock` into the repository. The deletion preflight/finalization methods do **not reacquire** the lock; the caller already holds it across provider stop/delete and readback. Existing token/engine/package/cluster/reference invariants and non-destructive Room schema v6 remain.
- Four new native SQLite/Robolectric regressions: a held stop lease refuses association without modifying Room; lease refusal precedes unpublished private-store readback; validation failure releases the lease and generation switching preserves UPDATE_TASK/REVIEW_REQUIRED/ROLLBACK owners; a concurrent synthetic stop lease prevents association until released.
- E40 test fixture now injects a **real FileQueueLock**, not a fake. Existing migration/rollback/idempotence tests remain. A dedicated E58 static audit and pinned cloud/candidate CI workflow run E40–E58 safety, JVM/SQLite, build, lint, APK signature/permission and no-split/no-provider/no-GC checks.

## Exact-source acceptance

**E58 technical PASS** on exact checked-out code/test SHA `07fc3262eb78f9bc1b4fa27069cbce596ad6b244` — [GitHub Actions #40881081227](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40881081227) **SUCCESS**, [test-only artifact #12900163664](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40881081227/artifacts/12900163664). Explicit SHA check verified checkout of the branch head rather than GitHub's synthetic PR merge commit. `./scripts/check.sh` (cloud) and `./scripts/check.sh --with-engine-candidate` both succeeded with **449 tests per gate**: core **140**, APK runtime **44**, Android/Robolectric **265**; **0 failures, 0 errors, 0 skipped**. Pinned toolchain, build/lint, APK signatures/permissions, E40–E58 source safety, and split-disabled/no-provider/no-GC guards passed. CI receipts `cloud-verification.json` and `candidate-verification.json` were inspected from the generated artifact. This is technical source acceptance, not guest runtime or product phase acceptance.

## Still unaccepted

No published split cluster is imported into a production guest engine by E58. `supportsSplits=false`, `importsSplitApks=false`, no accepted real third-party guest Activity, kernel UID/account isolation, session update, physical phone test or signed release. **Product readiness remains 28.00% weighted (~30% rounded); 0 accepted real app clones.** Keep cumulative PR Draft and `main` untouched.
