# E40 — Clone metadata ↔ private APK cluster ownership: CI evidence and acceptance

Date: 2026-10-29. Repository: `danhgold7979-cyber/fonesi-apk`.
Branch: `phase/e40-clone-cluster-ownership-20261029` (stacked Draft PR #13, parent PR #12 unchanged).

## Verified code/source evidence

- Source SHA `b20e8313822fea14fc89604502727ea79a4207c8` — [E40 Actions #40835815869](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40835815869) completed **SUCCESS** for source audits, cloud build/tests/lint, opt-in candidate build/tests/lint, APK signing/permission audits and disabled-split/destructive-GC checks.
- CI artifact `12881500224` (`fonesi-E40-clone-cluster-ownership-TEST-ONLY`) was retrieved and inspected. Cloud and candidate reports each record **118 core + 44 APK runtime + 182 Android/Robolectric = 344 tests**, with zero failures, errors or skipped tests in each gate.
- Cloud APK 0.5.0-cloud: 11,925,304 bytes, SHA-256 `ee595bb9f4a783269789c1620627ce99457eef3a16b19e803890caad134937f5`; candidate APK 0.5.0-candidate: 18,041,856 bytes, SHA-256 `a18f54d50a66ff50146728ec490346e772a7c534c6450f4c682d052afcaf5c9f`.
- Lint warnings: cloud 17, candidate 27, no lint errors. Generated Room schema v6 reviewed from artifact: identity hash `a9406cbe3dec557bfcc5e499c7ca381c`; exact schema UTF-8 SHA-256 `5eba80cd1d64ac8e46a0aed5234c7967c0aa37d35e792427b9911130114bc52b`. It adds only nullable `clone_metadata.clusterId` and preserves all four other tables/indexes.

## Implemented behavior

- Room v5→v6 non-destructive nullable-column migration, retaining IDs, launch tokens, queue, engine reservations and E39 reference rows.
- Transactional CLONE reference acquisition, idempotent association and generation switching after public PrivateApkStore.open readback. Reject mismatched namespace/package, missing/ambiguous owner, or stale token without changing Room ownership.
- Under the existing operation lock, validate owner state **before** provider deletion; after verified absence, recheck and atomically delete clone metadata/release only its matching CLONE reference. No APK bytes or other owner types are deleted.
- Additional focused acceptance tests committed with the generated schema: unpublished/malformed cluster rejection, wrong clone/engine namespace, injected SQLite write failure rolling back the newly inserted reference, and preserving metadata/other owners on provider deletion failure. These additional tests require a new same-SHA CI gate before final E40 PASS.

## Final gate and limits

**Final status of this document: not yet PASS.** Require a fresh cloud/candidate E40 workflow successful on the commit that includes these additional tests and the generated schema v6, then update the milestone status from actual evidence. Do not infer acceptance from a prior SHA.

This remains a storage/clone-lifecycle technical increment in Phase 3, steps 13–16. It does not publish/activate/garbage-collect APK clusters, import split APK into SpaceCore, run a real guest Activity, isolate kernel UIDs/accounts or demonstrate Samsung/real-device compatibility. Product readiness remains 28.00% by the existing rubric (about 30% rounded); zero third-party applications accepted. Keep PR #13 Draft, `main` and parent branch unchanged.
