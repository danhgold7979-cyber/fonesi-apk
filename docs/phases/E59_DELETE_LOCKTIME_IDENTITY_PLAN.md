# E59 — clone deletion must revalidate full saved identity after acquiring OS lease (pre-code plan)

Date 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`; branch `phase/e59-delete-locktime-identity-20261029` stacked on E58 [Draft PR #32](https://github.com/danhgold7979-cyber/fonesi-apk/pull/32) reporting head `3cb0494ae80dd1d49566f1fbcc211b4efbfc380a`. E58 **code/test SHA `07fc3262eb78f9bc1b4fa27069cbce596ad6b244` PASS** at [CI #40881081227](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40881081227) (**449 tests each cloud/candidate gate**). E58 reporting CI #40882615722 is pending at plan creation. **Original Product Phase 3 steps 14–16** clone lifecycle and data safety; E59 is not Product Phase 59.

## Observed defect

`CloneMetadataRepository.delete` reads `saved = db.clones().find(engineKey,id)` *before* acquiring the shared OS `operationLock` in `mutateEngine`. Inside the lease it checks only the current `launchToken == saved.launchToken`, so a cooperating rename/restyle or E58 cluster generation switch that completes **between** the initial read and lock acquisition may preserve the same token but change the clone's entire saved identity. Deletion might then invoke the external provider using stale user-intent metadata. E56 stop already uses a full lock-time identity readback; deletion must be at least as conservative. The existing provider confirmation and Room owner-reference transaction remain separately required.

## Implementation

1. Re-check exact E58 branch/CI and E56/E58 stop/association/Room contracts before editing. Record this plan and an `EXECUTION_PLAN.md` entry before source code; leave main untouched.
2. Under the `mutateEngine` lease, compare **the entire lock-time `CloneMetadataRow` to `saved`** before provider readiness/list/delete, not only launch token; still require explicit expected launch token when supplied. A changed label, style, cluster ID, package, launch token or generation must refuse the operation before any engine deletion. Do not adjust the initial token/namespace query or change the ability to delete a stable saved clone.
3. Use a controlled `QueueLock` interleaving fixture backed by a **real FileQueueLock** to simulate a second writer taking/releasing the same OS lease between initial metadata snapshot and deletion lease acquisition. Cover presentation rename/restyle and cluster-generation identity changes with native SQLite; verify failure leaves engine guest and Room owner/metadata untouched, including after database reopen. Cover stable deletion success and no false provider-confirmed metadata loss.
4. Add E59 source audit with exact ordering and regressions, and pinned E40–E59 cloud/candidate workflow (SDK/JDK/Gradle, build/test/lint/APK signatures/permissions, no production split/provider/GC activation). Require same-source SHA and all gates PASS before technical acceptance.
5. Update `docs/STATUS.md`, `docs/PRODUCT_PROGRESS.md`, `docs/EXECUTION_PLAN.md`, E59 results and Draft PR from actual CI. Keep product **28.00% weighted (~30% rounded)**, **0 accepted real third-party clones**, and `main` unchanged.

## Safety and exclusions

This is a **pre-dispatch identity-race guard**, not an independent guest deletion engine or OS account sandbox. Keep E58 private-cluster association lock, E56 stop readback, Room v6 migration and existing journal hold barriers. Do not add APK data deletion, release APK artifacts, real account testing, or advertise full split support (`supportsSplits=false`, `importsSplitApks=false`). External uncoordinated Room writes and unexpected provider outcomes still require independent reconciliation.
