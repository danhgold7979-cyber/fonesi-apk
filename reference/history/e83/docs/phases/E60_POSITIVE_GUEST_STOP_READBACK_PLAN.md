# E60 — require positive guest-stop readback before clearing durable launch-pending (pre-code plan)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`, branch `phase/e60-positive-guest-stop-readback-20261029` stacked on E59 [Draft PR #33](https://github.com/danhgold7979-cyber/fonesi-apk/pull/33) reporting SHA `90ab636350c14230c87230d17af0dea0886bf4c2`. E59 **code/test source `7176ae9c8c9eaf92989ddef3e4882b06e3e9c367`** passed [CI #40883514023](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40883514023) (453 tests per cloud/candidate gate); E59 documentation [CI #40884715219](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40884715219) was still pending at pre-code planning. E60 is a technical increment under original **Product Phase 3 steps 14–16**, not another product phase.

## Actual source defect

In `ManagedGuestEngine.stop`, after `backend.stop(userId, packageName)`, the condition `backend.inspect(userId,packageName)?.running != true` accepts **null/missing guest readback** as success, clears durable `launchPending` and lets UI claim the engine confirmed stop. A returned guest package with a mismatching package identity, negative version or no signing digests may also be treated as a valid stopped record if `running=false`. E56/E59 protect host metadata identity; E60 must protect the SDK guest readback itself. The candidate backend is SDK-bound and may return null when the native package cannot be found. Null is not proof of a valid stopped guest session.

## Implementation and acceptance

1. Read current roadmap/rules/STATUS, E59 source/CI, `ManagedGuestEngine`, `GuestBackend`, JVM fixture and all caller paths. Commit this plan and an `EXECUTION_PLAN.md` entry before source edits.
2. Keep existing foreground/mutation checks and `backend.stop` dispatch. Require one **nonnull post-stop `GuestPackage`** with exact expected package identity, nonnegative version, nonempty signer set and `running == false` **before** calling `registry.pending(id,false)`. Any null/corrupt/running/exceptional SDK result fails closed, retains the durable pending marker and never claims stop success. Do not automatically retry a potentially interrupted native stop.
3. Add focused core/JVM tests: post-stop missing guest readback, mismatched package identity, invalid version/signers, failed stop while running and genuine stopped readback. Verify pending marker and stable registry ID under uncertain outcomes; no additional SDK mutation or false deletion. Keep existing create/update/launch tests.
4. Add `scripts/audit_e60_positive_guest_stop_readback.py` and a pinned E40–E60 workflow (cloud/candidate builds, test/lint, APK signature/permissions, no split/provider/GC activation) using explicit PR head SHA. Report exact test counts and CI URL before PASS. Do not loosen or skip failing tests.
5. Update `docs/STATUS.md`, `docs/EXECUTION_PLAN.md`, `docs/PRODUCT_PROGRESS.md` and E60 results after tests. Keep Draft PR and `main` unchanged.

## Limits

This is a **guest readback correctness guard**, not proof the native SpaceCore SDK actually stopped a social app or isolated its account. Guest Activity/Service/Provider/full-split import, UID/account isolation, physical Samsung/login testing and release signing remain unaccepted. `supportsSplits=false`, `importsSplitApks=false`, product estimate **28.00% weighted**, **0 real accepted clones**. Do not merge or release as finished.
