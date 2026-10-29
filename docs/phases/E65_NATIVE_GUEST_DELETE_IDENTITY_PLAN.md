# E65 — native guest deletion pre-dispatch identity continuity (pre-code plan)

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e65-native-guest-delete-identity-20261029` from E64 reporting HEAD `3da8eddd267b0118281985167d4adc1a1f7567e7`. Original **Product Phase 3 steps 14–16**, with Phase 4 data-retention implications. E64 source/test SHA `a54c034d21711b7d15d301ebe67a5466c19a3d7d` passed exact-source [CI #40895219310](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40895219310) **SUCCESS (470 tests per cloud/candidate gate)**. E64 reporting HEAD CI was pending at planning; E64 [Draft PR #38](https://github.com/danhgold7979-cyber/fonesi-apk/pull/38) stays unmerged. E65 is a technical increment, not Product Phase 65.

## Identified gap in current source

`ManagedGuestEngine.delete` obtains a native record via `record(row)`, checks `running == false` and foreground authorization, writes durable DELETING, then `backend.uninstall`. If the native SDK package changes version/signers or becomes running **after** that initial inspection and before the uninstall dispatch, the code may remove a different/busy guest image. Likewise, an initially missing native package could newly appear before the DELETING transition, leaving ambiguous state. E59's metadata operation lease does not establish native SDK package identity continuity. An exact native readback immediately **before** the durable transition/dispatched uninstall is required; it cannot eliminate every SDK race without a native atomic protocol, which remains a release caveat.

## Ordered implementation and safety criteria

1. Commit this plan and an EXECUTION_PLAN entry before modifying source.
2. In `ManagedGuestEngine.delete`, after the existing running/unknown and foreground checks but **before** `registry.state(DELETING)`, read `backend.inspect` again. If the original native record was present, require nonnull, same package, same version, exact same complete signer set and explicit `running=false`; otherwise reject with original registry READY/RESERVED state intact, no SDK uninstall. If original native package was missing, require second readback also missing; preserve existing safe no-native delete/retirement without fabricating successful uninstallation. Keep final post-uninstall **absence** confirmation, never reclaim or reuse the native user ID.
3. Add core JVM regressions that mutate only the backend-double native package between initial inspection and dispatch (through foreground authorization seam): version drift, signer drift, newly running guest and a formerly absent guest appearing. Assert no SDK uninstall and no DELETING mutation. Exercise valid exact deletion and absent-package retirement, plus existing pending/uncertain guards.
4. Add E65 static safety audit and dedicated pinned exact-source E40–E65 cloud/candidate test/build/lint/signature/permission workflow, disallow split/provider/garbage collection activation.
5. Record actual source SHA/test totals/CI result in E65 results and STATUS/EXECUTION_PLAN/PRODUCT_PROGRESS; technical PASS **only** after exact code SHA CI success. Keep stacked Draft PR and leave `main` unchanged. Separate reporting SHA CI required.

## Excluded product acceptance

No third-party guest component execution, full split import, independent Android UID/account sessions, Samsung or other device, real social-app login/update retention, or release signing. Candidate `supportsSplits=false`/`importsSplitApks=false`; no user data or account secrets tested. Weighted completion stays **28.00%**, real third-party clone apps accepted **0**.
