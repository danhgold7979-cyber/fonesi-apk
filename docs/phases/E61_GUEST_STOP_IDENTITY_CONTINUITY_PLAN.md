# E61 — preserve native guest package identity across stop (pre-code plan)

Date: 2026-10-29. Exact repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e61-guest-stop-identity-continuity-20261029` stacked on E60 [Draft PR #34](https://github.com/danhgold7979-cyber/fonesi-apk/pull/34), source SHA `635cc06fa491d06907609f438a073c8ca54ae460`. Parent E60 CI [#40885876272](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40885876272) was running during planning; **do not mark E60/E61 PASS based on pending parent**. Original Product **Phase 3 steps 14–16**; E61 is not a product Phase.

## Verified source gap

E60 requires a non-null stopped `GuestPackage` after `backend.stop`, with the correct package name, nonnegative version and a nonempty signer set. It does not verify **continuity of exact guest version/signing identity** through that native operation. A stopped but different-version or differently-signed guest record could therefore clear durable launch-pending and be treated as confirmed. This is a separate source identity safety requirement from E60's non-null check. The source-backed registry stores package/user identity, not expected package version/signers, so obtain a positive verified native package snapshot before invoking stop, then compare the exact version/signers after stop.

## Planned implementation and test gates

1. Confirm current E60 source, parent CI and source tree; preserve E60's post-stop positive readback, launch-pending semantics, foreground authorization, engine namespace and no automatic retry.
2. In `ManagedGuestEngine.stop`, preflight a **nonnull, valid native guest record for the exact reserved user/package** before `backend.stop`; do not treat absent or invalid pre-stop record as authority to mutate the SDK. After stop, require the existing positive stopped readback to match the **same package, version, and entire signer set** observed before stop. Only then clear `registry.pending(row.id, false)`. A missing, changed or corrupt post-stop record must retain pending and fail closed.
3. Write focused core/JVM regressions for missing/corrupt pre-stop inspection (no native stop), guest version replacement after stop, signer replacement after stop and normal same-identity successful stop. Keep all E60 regressions and existing create/update/launch behavior; no synthetic success fixtures.
4. Add E61 static audit and pinned exact-HEAD cloud/candidate GitHub Actions running E40–E61 audits, JVM/SQLite tests, build/lint, APK signature/permission and no split/provider/GC checks. Any CI failure is a blocker, not PASS; correct source/tests without weakening criteria and rerun.
5. Record test counts and exact source SHA in E61 results and update STATUS, EXECUTION_PLAN and PRODUCT_PROGRESS only after verification. Keep Draft PR and `main` unchanged.

## Product boundary

No full split APK production import or arbitrary guest Android Activity/Service/Provider support, independently isolated OS UID/accounts, real app/session tests on Samsung, or release signing is proven. Keep `supportsSplits=false`, `importsSplitApks=false`. Weighted product readiness remains **28.00%**, **0 real third-party clones accepted**.
