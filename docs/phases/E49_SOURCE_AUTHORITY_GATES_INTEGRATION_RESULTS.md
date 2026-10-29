# E49 — Original-source authority gates integrated: exact-source CI result

Date: 2026-10-29. Repository [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk), branch `phase/e49-source-authority-gates-integration-20261029`, [Draft PR #23](https://github.com/danhgold7979-cyber/fonesi-apk/pull/23) stacked on E48 Draft #22 and E45 Draft #19. Original eight-product-phase roadmap **Phase 4, steps 17–20**; E49 is a bounded technical integration, not completion of Phase 4.

## Exact-source acceptance

- **E49 implementation source SHA `23ea57d65fd562d5cc611040e2e8791f6462779c`** verified by [GitHub Actions #40851203055](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40851203055) **SUCCESS**, on the exact SHA. Cloud and opt-in candidate logs both report **407/407 tests**, 0 detected failures, successful builds, lint, APK signature/permissions checks.
- All E40–E45, E46 foreground, E47 manual confirmation, E48 recovery and E49 integration source audits passed. Pinned SDK36/JDK21/Gradle toolchain, Room v6, candidate single APK (`supportsSplits=false`), no production activation and no destructive private APK GC gates remain.
- GitHub Actions artifact **12886379874**, `fonesi-E49-source-authority-gates-integration-TEST-ONLY`, contains sanitized test receipts and Room schema v6. [Source workflow and artifacts](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40851203055).
- Independent source technical receipts: E45 390 tests per gate at [#40848683404](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40848683404); E46 394 at [#40849553940](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40849553940); E47 402 at [#40850268707](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40850268707); E48 395 at [#40850652184](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40850652184). Test counts are not app-completion percentages. Previous E44 batch/quarantine CI also passed.

## Code combined without overwriting parents

1. E45 preserves immutable signed private-cluster task source, atomic multi-target Room queue/UPDATE_TASK owners and durable `retainedSource` source-authority marker; normal legacy runner cannot use original APK paths for those tasks.
2. E46 `ForegroundAutoUpdater` checks `RoomLegacyUpdateSourcePolicy` before original-app supersession cancellation or deferred retry, preserving private retained/older owner-only tasks.
3. E47 `UpdateRequestService.confirm` rejects an original-app queue request that conflicts with an active private-source task *inside the existing Room transaction*, including mixed-target all-or-nothing behavior.
4. E48 `UpdateRunner.recoverLocked` checks the same source authority before snapshot cleanup/legacy engine readback; unknown retained APPLYING/VERIFYING become durable REVIEW_REQUIRED, and PREPARING/REVIEW_REQUIRED stay held instead of being falsely marked SUCCEEDED.

All three sets of tests and static audits are present in **one** E49 source commit. Separate E46/E47/E48 PRs were not merged, force-pushed or rewritten. Ordinary unassociated legacy-source behavior retains its test coverage.

## Limits and next step

E49 is **technical PASS**, not a private-cluster execution adapter or proof of an accepted production clone provider. The app still lacks arbitrary signed base/split import in the candidate provider, actual registered guest Activity/Service/Provider, independently separated kernel UID/account sessions, real social-app update/readback and Samsung/other-device tests. Release signing is not accepted. Product weighted rubric remains **28.00% (~30% rounded)**; real accepted third-party cloned apps **0**. `main` remains at `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` and Draft PR #23 stays unmerged. E50 is a separate evolving effort and must be independently checked before claiming progress.

This report-only follow-up HEAD has a new SHA; only the code SHA above has its complete E49 evidence until the HEAD's own CI succeeds.
