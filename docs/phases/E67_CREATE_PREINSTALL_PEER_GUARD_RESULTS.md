# E67 — revalidated native guest create pre-install peers and source

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`; technical E67 under original **Product Phase 3 steps 13–16** with Phase 4 shared-package code safety. Branch `phase/e67-create-preinstall-peer-guard-20261029`, [Draft PR #41](https://github.com/danhgold7979-cyber/fonesi-apk/pull/41) stacked on E66 [Draft PR #40](https://github.com/danhgold7979-cyber/fonesi-apk/pull/40). [Pre-code plan](E67_CREATE_PREINSTALL_PEER_GUARD_PLAN.md) was committed before source.

## Implementation

- `ManagedGuestEngine.create` captures the exact existing shared-package guest rows before reservation, then after creating the native user and **before** importing APK code, re-verifies the prepared private snapshot, immutable reserved guest ID/native user, and absence of a preexisting package for that new user.
- It rejects disappearing, appearing or changed peer rows, non-READY peers and any native peer whose version/signers/stopped state no longer match the target. Native uncertainty after creating a user preserves the RESERVED row and frozen snapshot for reconciliation, with no destructive rollback or original-app fallback. Existing E63 peer checks, E66 update checks and foreground-expiry-after-first-native-mutation behavior remain.
- Six JVM backend-double tests verify a running peer, changed signer, disappearing peer, new RESERVED peer, invalidated snapshot and unexpected package on reserved user all block further SDK install without uninstall. Added E67 static safety audit and pinned dual-flavor workflow.

## Exact-source technical verification

**E67 technical PASS** on code/test SHA `2d631994601594e7848e3ab0c2662f8fa5af0204`: [GitHub Actions #40900943479](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40900943479) **SUCCESS**, exact source checkout, E40–E67 audits, pinned Android/SDK/JDK, **486 tests in each cloud and candidate gate**, build/lint, APK signatures/permissions and no provider/split/garbage-collection activation. [Test-only artifact #12909418608](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40900943479/artifacts/12909418608). E66 code/test SHA `979ef76bb1b228b28841575f1cfa88d5778d5231` passed [CI #40899947204](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40899947204) (480 tests/gate). Its separate reporting SHA `654f25c8909d627a306e2eec28b75611d0b34b75` passed [CI #40901211794](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40901211794). This E67 documentation/integration commit is a **different SHA** and requires independent CI.

All tests use JVM/Robolectric or other controlled fixtures; there is **no accepted third-party guest Activity/UID/account isolation runtime proof**. Post-createUser pre-install checks cannot form an atomic transaction inside a closed-source SDK against uncoordinated external mutations.

## Product blockers

Production full split APK provider is absent (`supportsSplits=false`, `importsSplitApks=false`), guest Activity/Service/Provider, real independent Android UID/account sessions, Samsung/non-Samsung/social app tests, update data retention and release signing unaccepted. Weighted readiness stays **28.00%**, accepted real third-party clone apps **0**. Keep Draft PRs, `main` unchanged.
