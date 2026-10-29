# E61 — exact native guest identity continuity across stop

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`; branch `phase/e61-guest-stop-identity-continuity-20261029`, stacked on E60 source `635cc06fa491d06907609f438a073c8ca54ae460`. Original **Product Phase 3 steps 14–16**; E61 is a technical increment.

## Implementation

- Before stop dispatch, `ManagedGuestEngine.stop` now requires a nonnull, valid SDK guest package record for the exact native user/package (package identity, nonnegative version, nonempty signers). A missing/invalid record causes no SDK stop attempt.
- After the SDK's stop call, a positive nonnull stopped guest readback must carry **exactly the same package, version and complete signer set** as before stop, before durable `launchPending` is cleared. Unknown/changed/corrupt results preserve the pending marker and are never blind-retried.
- Core/JVM fixtures test missing or corrupt preflight, wrong package, negative version, empty signers, changed post-stop version, changed post-stop signing identity, running backend and valid positive success.
- Strengthened E60 source audit for the stricter E61 checks; added an E61 audit and exact-HEAD pinned cloud/candidate GitHub Actions workflow running E40–E61 audits, tests/build/lint, APK signatures/permissions and split-disabled/provider/GC guards.

## Verification

**E61 CI verification pending.** Code commits exist but E61 is **not technical PASS** until the dedicated workflow completes successfully on its exact source SHA and test/permission receipts are read. At planning time E60 parent [workflow #40885876272](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40885876272) was also in progress, so its status must be independently confirmed. Never equate compilation/SDK mock with actual guest-stop acceptance.

## Release blockers

No accepted full split APK engine, real guest Activity/Service/Provider, independent Android UID/account sessions, social-app device proof or release signing. Candidate `supportsSplits=false` and `importsSplitApks=false`; **28.00% weighted product readiness, 0 real accepted third-party clones**. Keep cumulative PR Draft; do not merge to main or ship as finished.
