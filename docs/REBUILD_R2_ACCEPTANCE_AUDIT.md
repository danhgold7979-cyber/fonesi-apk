# R2 detailed acceptance audit — 2026-10-30 21:30 UTC

Read-only audit against **all R2.1–R2.3 clauses** in `REBUILD_EXECUTION_PLAN.md`, not only the master row or test totals. Exact current implementation evidence: publishedf5a11ac7, run41130512434 SUCCESS, reviewed source at localb409f60. No new tests/code added by this audit. Independent reviewer inspected the same criteria.

## Verdict

R2 is substantially implemented and directly evidenced on Linux/JVM, but **not fully accepted under its current literal detailed test requirements**. Two bounded negative-test completeness gaps remain below. These are independent of R4/provider/device selection and can be closed locally/hosted without expanding capability scope. Do not mark R2 zero merely because a provider is unresolved, or mark it fully complete from passing counts.

## Criterion mapping

| Detailed criterion | Current evidence | Assessment |
|---|---|---|
| R2.1 new standalone/no old implementation copy; typed immutable provider/environment/scope/cluster claims | CapabilityPolicy/IdentityText/OperationProtocol; independent module source/history; defensive copies; no Android/provider dependency | Met for host contract. B1b scalar validation and B1c exact stronger-mode necessary condition are reviewed corrections. |
| R2.1 fail-closed provenance/dependency/isolation policy | KernelTest,6200 independent PolicyModeTest cases; corrected D3 denies unsupported modes even with ADV assertion | Met. Objects still are not attestation; synthetic non-ADV proof cases intentionally remain permitted. |
| R2.1 immutable operation UUID/provider/package/environment/scope/clone/cluster/peers, explicit states/recovery/readback | KernelTest, B1b tests, desired logical vectors cover reachable state/revision, consent drift, operation/revision/peer/private-storage mismatches | Met as pure state contract; constructing a value is not storage/authorization permission. |
| R2.1 actual installed compiler/runtime, failure exits/nonzero assertions, future Gradle module | check.sh realJDK21; exact CI; module/settings present | Canonical shell path met. Future Gradle task is not equivalent to the full current runner; see maintenance below. |
| R2.1 future signed A/B adversarial/lifecycle/crash/device test design | REBUILD_RUNTIME_TEST_DESIGN.md includes original untouched, cross-path/FD/IPC/provider/URI/permission, exact receipts and phone gate | Met as design only, not runtime acceptance. |
| R2.2 trusted private root/no symlinks/special locks/file identity, bounded checksummed v2, duplicate/create/reopen/CAS | OperationJournal; JournalTest root replacement,symlink/FIFO/directory rejection,correctly checksummed malformed input,identity/format/peer bounds | Met for inspected Linux trusted-root/cooperating-process boundary, not hostile filesystem/Android proof. |
| R2.2 force staging→atomic rename→directory force; no atomic fallback; uncertainty never authorizes replay | JournalTest pre/postrename injection, DispatcherTest publication faults/create/dispatch/verification/review, reopen diagnosis and no mutation after uncertainty | Met for declared fault seams; no claim power-loss/OEM fsync validation. |
| R2.2 cross-process contention and restart quarantine | Real second-JVM lock tests and dispatcher child killed after durable DISPATCHED; BUSY then REVIEW_REQUIRED on retry | Met on hostedLinux. |
| R2.2 **every illegal/terminal durable transition** | Current JournalTest covers selected pairs, but some replacement records fail their constructor before CAS runs | **Missing exhaustive valid-record CAS matrix**; see G1. Pure-state tests do not prove journal-boundary rejection for every pair. |
| R2.3 stable providerID/package lifetime lease/global UUID journal namespace, revision-independent unresolved barriers, unrelated progress | DispatcherTest nested and second-process contention,duplicate/rebound UUID,provider revision change,unrelated package progress | Met for shared-update scope only. Whole-app launch/create/delete/stop cooperation remains later adapter gate, explicitly excluded here. |
| R2.3 repeated live preflight consent/frozen-source/foreground/completeenumeration/READY/stopped/exactpeer generation | DispatcherTest three probes,flags/consent/package/peer coverage/newpeer/generation/authority-loss tests | Substantial, but **not every binding failure has callback-level no-mutation assertion**; see G2. |
| R2.3 durable DISPATCHED before mutation, independent readback, exceptions/faults retain uncertainty | DispatcherTest callback/readback and everypublicationpoint faults,unresolved replay barriers | Met within trusted synthetic callbacks. |
| R2.3 Linux CI exactsource, no Windows durability claim/source deletion/provider uplift | Actual run41130512434 and independent job source checks; oldsource retained, no app imports | Met. Workflow has separate Android/storage jobs; standalonekernel does not depend on Android SDK/provider. |

## G1 — precise test-only closure

For all25 ordered state pairs, construct both records with **their own valid reachable revision** (PREPARED0, DISPATCHED/CANCELLED1, VERIFIED/REVIEW_REQUIRED2), and create the source state durably through legal journal transitions in a fresh synthetic root. Four legal edges must persist/reopen exactly. The other21 must reach `compareAndSet`, reject, and leave durable bytes/source state unchanged. Include terminal self-pairs and cross-terminal pairs. Do not use an invalid replacement constructor as evidence that journal CAS rejected it. Existing examples doing that: DISPATCHED→CANCELLED revision2; PREPARED→VERIFIED revision1; terminal→PREPARED revision2.

## G2 — precise test-only closure

Add dispatcher-level probes supplying peer providerID/revision mismatch and capability-evidence providerID/revision/environment/cluster mismatches. Assert explicit rejection and **zero mutation/readback callbacks**, at each relevant preflight stage; third-stage loss after durableDISPATCHED must remain review-required/no replay. Existing pure policy mismatch tests establish `assess`, not necessarily dispatcher callback non-entry. Keep existing failedflags/consent/package/peer-set/enumeration/generation/fault tests; do not weaken checks or invent real authentication evidence.

## Nonblocking future-entrypoint maintenance

`rebuild-kernel/build.gradle.kts` still advertises `check` with only KernelTest/JournalTest/DispatcherTest. It does not run PolicyModeTest/IdentityTextTest or isolated historical oracles, unlike canonical check.sh and actual CI. R2.1 required a future Gradle definition, which exists; it did not require that unexecuted path as acceptance evidence. Before advertising it as equivalent, align/delegate it safely or explicitly mark it partial/unverified. This is not justification to erase Gradle/wrapper infrastructure or hide missing checks. No Gradle execution was attempted in this audit.

## Boundary of eventual R2 acceptance

Closing G1/G2 with independent review and exact-head actual CI would support the **bounded standalone shared-update kernel** gate, not full Android manager/runtime, storage migration, engine/provider, whole-app concurrency or hostile-guest/device isolation. R3/R4/R5/R6/R7 retain their own requirements; no source deletion equivalence follows automatically.

## Closure and narrow acceptance — 2026-10-30 21:46 UTC

Initial verdict above records the pre-closure audit; it is superseded for G1/G2 by approved test-only commit66aee97. Independent reviewer verified all25 source/target records construct validly before actualCAS invocation (4legal,21reject with unchangedbytes/state), and all18 dispatcher binding/stagecases assert zero mutation/readback, exactretainedstate, duplicate/no-replay barriers. Allfiveproduction source hashes remain unchanged.

Published dc19cdd57b41a97ce3787589815a104b136a41dc, exacttree035f9df60a07c5579ae169a65df0861920c2db4a; run41133142565 completedSUCCESS. Actual hosted159journal/227dispatcher checks and fullkernelpolicy/identity/history/parity suites pass, including installedJDK17.0.21. Root authorized narrow acceptance after reviewedgreen; **R2 is now ACCEPTED under its detailed standaloneLinux/JVM/shared-update scope**. This does not accept Androidfilesystem/durability/auth, provider/readbackauthenticity, whole-app launch/create/delete concurrency, actualhostileguestisolation or source-replacement equivalence. The optionalfutureGradleentrypoint remains explicitly partial/unverified, not the canonical acceptance runner. No product percentage is derived.
