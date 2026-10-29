# E66 — exact pre-dispatch shared update guest identity results

Date: 2026-10-29. Repo `danhgold7979-cyber/fonesi-apk`; branch `phase/e66-shared-update-dispatch-revalidation-20261029`, [Draft PR #40](https://github.com/danhgold7979-cyber/fonesi-apk/pull/40) stacked on E65 [Draft PR #39](https://github.com/danhgold7979-cyber/fonesi-apk/pull/39). Original **Product Phase 4 steps 17–20** with Phase 3 clone-ID/lifecycle safety prerequisites. [E66 pre-code plan](E66_SHARED_UPDATE_DISPATCH_REVALIDATION_PLAN.md) committed before source changes.

## Implementation

- `ManagedGuestEngine.update` captures full same-package guest rows (stable ID, native user, lifecycle/pending) after checking shared update scope, stopped state, version monotonicity and signers.
- After foreground mutation authorization, before `backend.install`, it rejects changed/added/missing guest rows and re-reads all native guest records. Exact package/version/signers/running status must still match all original `CloneRecord` values, including the selected native user identity. Existing E63 READY-only and post-install all-peer confirmation remain in place.
- Five JVM interleaving tests cover changed SDK version, signer set, newly running guest, newly RESERVED peer and user-ID reassignment; all assert no new SDK install/uninstall.

## Technical verification

**E66 PASS at exact code/test SHA `979ef76bb1b228b28841575f1cfa88d5778d5231`**: [GitHub Actions #40899947204](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40899947204) **SUCCESS**, exact source checkout, E40–E66 audits, **480 tests per cloud/candidate test gate**, pinned SDK/JDK/Gradle build, lint, APK signature/permissions and no split/provider/GC activation. [Test-only artifact #12909771288](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40899947204/artifacts/12909771288). E65 report SHA `7e46c5a5863e1b820a2fb221d32b365dd52689c3` separately passed [CI #40898925395](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40898925395) (success). This reporting-only SHA requires separate exact-HEAD CI.

JVM doubles and signed build proof do not show a real guest app/account runtime. SDK changes outside the operation lock can still happen after a non-atomic readback; verified production backend and device testing remain open.

## Remaining acceptance blockers

Full production split import (`supportsSplits=false`/`importsSplitApks=false`), Android guest Activity/Service/Provider, independent UID/account isolation, Samsung/non-Samsung/social app sessions, crash/update retention and release signing unaccepted. **Weighted readiness 28.00%, 0 accepted third-party clone apps.** PR stays Draft; `main` unchanged.
