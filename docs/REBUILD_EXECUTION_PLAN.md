# Rebuild execution ledger

## R2.1 — before implementation: standalone capability and operation kernel

Inputs: all-ref recovery PASS; original product scope; E35 hostile-guest model; E83 independent import/execution/atomic-update claims; E44–E67 authority/identity/uncertain-outcome protections. Old code will not be copied into the module.

Implementation location: `rebuild-kernel/`, pure Java 21/JVM, no Android SDK, downloaded libraries, SDK AAR, network or account access. Kotlin/Android adapters can call Java later. Existing product modules remain untouched until replacement gates pass.

1. Immutable provider identity and capability assessment. A typed evidence record binds provider ID/revision, environment and scope to a set of observed claims. Decision requires exact provider/environment; fail closed for unknown isolation, missing evidence, synthetic-only evidence on production requests, or incomplete dependency chain. Evidence objects are not cryptographic attestations and must be supplied only after external verification.
2. Operations bind operation UUID, provider identity, clone UUID, cluster SHA-256 and immutable peer set. Explicit states PREPARED → DISPATCHED → VERIFIED or REVIEW_REQUIRED; pre-dispatch cancellation permitted, never after dispatch. On restart any dispatched-but-unverified outcome becomes REVIEW_REQUIRED, never auto-retry. Only exact provider/clone/cluster/peer readback can verify.
3. Local tests compile production and test sources through installed JDK compiler module (`java -m jdk.compiler/com.sun.tools.javac.Main`) and execute an explicit test runner. Nonzero assertions/check count and any failure must fail process. No dependency/network/bootstrap needed. Add Gradle Java module build definition for future normal CI, without altering existing root build yet.
4. Tests cover evidence provenance mismatch, synthetic vs real scope, unknown/same-UID isolation without adversarial proof, missing split import/execution/update prerequisite, defensive collection copying, invalid identities/digests, dispatch/cancel/verify ordering, post-dispatch identity drift and restart quarantine. No fake backend accepted as production.
5. Document synthetic runtime test design for future R4: signed controlled guest A/B; original data unchanged; adversarial cross-path/FD/IPC/provider/URI/permission cases; lifecycle/restart/update/crash receipts; exact source/artifact/environment binding; physical-device gate remains separate.

Acceptance: new JVM suite executes with real compiler/runtime, zero failed tests; no native/provider operations, no capability uplift in existing app, no source deletions, no push. This is R2 foundation only, not engine completion.

## Following packages (plan before implementation)

R2.2 persistence journal serialization/checksum and CAS protocol with corruption/duplicate-operation tests; R3 storage/schema and source-reference ownership; R4 provider decision and runtime harness; R5 UI integration. Each requires a separate detailed pre-code entry. Do not invent a provider or lower E35 threat requirements to unblock R4.

### R2.1 toolchain correction before retry

Local compiler is Java 21.0.12.1 but runtime image lacks `lib/ct.sym`; `javac --release 21` fails with “release version 21 not supported” and reports no supported release image. Use the actual Java 21 system modules with explicit `-source 21 -target 21`; this does not lower language/bytecode target or suppress warnings. Local evidence is Java21 host compile, not Android API compatibility. Future Android integration still needs its SDK/desugaring build gate.

### R2.1 independent review corrections before code changes

Reviewer found lifecycle evidence lacked signed-import/private-readback dependencies, and shared verification trusted a declared peer set rather than independently observed per-peer records. Fix: enforce lifecycle import/readback prerequisites; replace one-clone receipt with operation-bound exact map of UUID→provider/cluster/private-storage observations. Require every affected peer, reject missing/extra/stale peer and mismatched operation ID. Add negative tests, retaining all existing assertions.

Further review: bind capability evidence/request to exact APK-cluster SHA-256 (one app result is not universal compatibility); globally reject claimed isolation with UNKNOWN level; bind readback to dispatched revision as well as operation UUID. Authorization, READY/stopped peers and durable CAS remain mandatory external dispatcher obligations, not implied by Identity equality.

## R2.2 — before implementation: durable operation journal / CAS

Prerequisite R2.1 committed f76c2f9; local 64 checks passed. New code stays inside independent rebuild-kernel, no production DB/provider wiring.

Design: caller supplies existing trusted private directory; reject symlink root/journal/lock. One OS file lock serializes cooperating processes. UUID-named journal files contain a bounded deterministic binary envelope with SHA-256 corruption checksum, full identity/state/revision and exact peer set. Checksum detects corruption, not hostile rewriting or receipt authenticity. CREATE accepts PREPARED revision0 only. CAS requires full expected record equality and legal revision+state transition; cannot write VERIFIED directly from PREPARED or change identity. Persist unique staging file, force bytes, atomic rename, force directory. Never fall back to non-atomic replacement. If commit result is uncertain, reread before any provider action; IOException after rename cannot authorize repeat dispatch.

Dispatch contract: caller commits DISPATCHED successfully before external SDK call; failure/uncertainty means do not dispatch. Restart reconciliation only converts durable DISPATCHED to REVIEW_REQUIRED. No guest mutation, snapshots or sensitive account content in this journal. UUID/namespace/cluster references are retained; no cleanup of user data.

Tests: roundtrip/reopen, create duplicate, stale CAS, every illegal/terminal transition, identity drift, corrupted/truncated/trailing/oversized envelope, symlink target/lock/root rejection, busy lock, atomic replacement/reopen, readback after prior uncertain state. Cross-process contention and injected pre/post-rename durability failure must be tested before integration acceptance. Linux filesystem tests do not establish Android filesystem behavior; SQLite/migration adapter remains R3.

### R2.2 review corrections before final verification

A post-rename fsync failure means bytes visible on reread may still not be durable. Therefore **never dispatch after an uncertain commit**, even if reread shows DISPATCHED; persist REVIEW_REQUIRED with a successful durable CAS or stop pending reconciliation. Reread is diagnosis, not authorization. Also reject an existing non-regular lock target before open, preventing FIFO blocking; test FIFO/directory lock rejection. Trusted-private-root/cooperating-process boundary remains explicit; this is not an attacker-controlled filesystem sandbox.

Review hardening: enforce currently reachable state/revision pairs (PREPARED0, DISPATCHED/CANCELLED1, VERIFIED/REVIEW_REQUIRED2); pin root directory file identity and reject replacement; add correctly checksummed malformed-envelope tests so decoding is exercised beyond checksum rejection. Journal CAS remains storage-only and cannot prove readback or serialize distinct overlapping operations; a trusted dispatcher with a full-lifetime execution lease is required before any real provider integration.

## R2.3 — before code: trusted shared-update dispatcher

Scope deliberately limited to shared-package update orchestration, not guest creation/launch or a production provider. Reuse new kernel/journal only; no old app implementation removed yet.

1. Lease key = stable provider namespace ID + signed package name (not provider revision, so upgrades cannot bypass unresolved prior work). Separate package directories allow unrelated work to proceed. One OS lease spans preflight, durable dispatch, callback mutation and independent per-peer readback. Persist directory creation before any dispatch.
2. Under that lease, inspect all operation journals in scope. Any DISPATCHED or REVIEW_REQUIRED blocks new mutations; terminal history stays. Duplicate operation UUID never replays. Invalid/malformed journal blocks scope, not silently skipped. Unrelated packages remain independent.
3. Trusted preflight callback supplies exact consent/operation identity, frozen-source verification, foreground/unlocked authorization and all affected peer identities with READY/stopped state. Revalidate before durable dispatch and once more immediately before external entry; drift fails closed. Capability assessment requires exact cluster/provider/environment and atomic-update proof for the requested synthetic/third-party scope.
4. Durable DISPATCHED must commit before mutation. Any publication exception means **no mutation**. After external entry, any exception/missing/stale readback leaves REVIEW_REQUIRED or unresolved DISPATCHED if storage is unavailable. Never blind retry, cancel or delete guest data. Independent per-peer readback (not the mutation return) is required to persist VERIFIED.
5. Tests use only synthetic callbacks: no-mutation on every preflight/authority failure; retained uncertainty; same-package different operation refusal while lease held; provider-revision changes cannot bypass unresolved work; unrelated package progresses; exact peer readback; duplicate replay refusal; exceptions around entry. No claim of authenticity or adversarial runtime isolation from callback tests.
6. Add a Linux Java21 CI workflow for standalone checks (no Android SDK/network dependency beyond trusted JDK action). Windows filesystem durability semantics are not assumed; do not claim Windows journal acceptance. Run local checks; CI remains NOT RUN until root publishes and actual workflow completes.

Source removal decision: R2 kernel is not a tested replacement for the Android manager/runtime. Keep legacy implementation clearly labeled as historical reference until R3/R5 replacement scaffold and migration/permission boundaries are defined and tested; remove only demonstrably superseded pieces in a later separate reviewable commit. Backup availability alone is insufficient to delete unique regression protections.

### R2.3 review correction before integration

Reviewer found package was only request/lease metadata, not durable identity; the same operation UUID could be rebound into another package directory. Fix: bind package/environment/evidence scope into Identity; keep one root-level UUID journal namespace for global dedup; filter unresolved history by durable provider namespace/package while per-package lease remains held. Journal envelope becomes version2; unbound version1 records fail closed (no fabricated binding or silent deletion). No production app uses this journal yet. All mutating adapters, including eventual launch/create/stop/delete, must use the same lifetime lease policy; current class covers shared-update only and cannot claim whole-app race safety.

Further dispatcher review: trusted probe must positively attest complete package enumeration (not merely agree with selected peers); all shared-code peer generations must match. Add those checks and unselected/new peer regressions. Replace materialized journal listing with streaming inspection under metadata lease, keeping memory bounded without deleting unresolved history or imposing a clone cap; indexed archival for long-lived history remains a later scalability task. Add dispatcher-level publish fault seams/tests and real second-process scope contention before checkpoint.

## R3.1 — before code: SQLite legacy-preservation and quarantine contract

Environment inventory and official Android documentation are in REBUILD_ANDROID_COMPATIBILITY.md. No SDK/Gradle available locally; no implicit SDK license acceptance. Smallest independent deliverable: new Python/SQLite reference contract and tests, **not** Android production adapter.

Inputs: exact historical Room schema1–6 JSON at E82 source `2fac016e3b28e4d9f21390c221cad1df7a382c86`, retained only as schema/regression oracle with SHA256 manifest. No old implementation copied. All databases used by this task are synthetic temporary fixtures, never user/device data.

1. Read source with SQLite URI mode=ro, query_only and stable read transaction. Check version, Room hash and actual columns/PK/indexes/table allowlist/integrity. Unknown schema, malformed ownership structure or unexpected schema objects fail closed; do not delete/repair source.
2. Preserve full typed table rows as deterministic canonical snapshot and fingerprint. Keep original tokens, queue payloads/revisions, instance reservations, pause and cluster ownership unchanged in history; no interpretation as active provider state.
3. Populate a **separate empty** target catalog in one SQLite transaction. All imported instances remain UNVERIFIED/read-only, all tasks quarantined with dispatch_allowed=0, all ownership retained and no garbage collector. Unknown providers/owner kinds remain quarantined rather than mapped to current engine. No guest creation, account data import or automatic dispatch.
4. Fault injection during schema/row/receipt stages must roll back entire target, preserving source bytes. Existing/nonempty target is refused. Test source versions1–6, reopen/readback, duplicate/foreign identities, Unicode/null/BLOB preservation, corrupted schema/hash, unknown future version, unknown ownership, partial failure and lock contention. Bounded harness memory budget fails closed; future streaming/native implementation must remove fixture-scale limitations without deleting records.
5. Add isolated Python unittest job to standalone CI; distinguish test count from JVM checks. Android Room migration registration/identity hash, installed-app upgrades and real-data migration remain NOT RUN. Never say “migration complete” from fixtures.

R3.1 validation hardening before checkpoint: bound SQLite cell length with the connection limit as well as total canonical snapshot budget, reject partial substitutes for required indexes, and exercise malformed metadata/oversized snapshots. These are synthetic reference limits, not claims of production streaming scalability. Local check runner must reject zero/skipped test runs; CI job uses standard-library SQLite only.

R3.1 independent review correction: an existing TEMP table can shadow unqualified target writes even when main is empty. Reject TEMP schema objects and attached databases before transaction, qualify target DDL/DML with main, and add shadow/attachment regressions. Source schema uses table_xinfo to reject hidden/generated columns, validates defaults (normalizing absent/NULL), foreign keys and required-index order/collation/partial flags; test each failure without repairing source.

R3.1 final review adjustments: explicit SQL COMMIT/ROLLBACK and safe journaling/synchronization checks now pass autocommit-mode and denied-COMMIT tests. Add an uncertain-target error preserving the original failure when commit visibility or rollback cannot be confirmed; reopening a valid receipt still never authorizes dispatch. Reject ignored CHECK constraints and unsupported auxiliary-table/index/table-option shapes. State guarantee precisely: validated schema structure, not a general SQL-equivalence or hostile-database certificate.

## R5.1 — pre-code: independent Java17 manager presentation contracts

Prerequisite: exact-head hosted run41110829297 passed fresh Android compile/lint/APK gates. Read original PLAN product requirements and frozen rebuild master: names/groups, accent-insensitive search, visible selection, truthful unknown/provider status; preserve no original-session copying and no fake clone success. Java17 native scaffold is a bounded implementation choice, not removal of original UI/shortcut/access requirements.

Scope: new platform-independent metadata presentation model inside fresh Android source. Immutable entry identity includes UUID, provider namespace and package; display name/group are metadata only. Status is UNVERIFIED/QUARANTINED/UNAVAILABLE, never READY/running. Model has IDLE/LOADING/LOADED/FAILED phases. No fake seeded entries, provider calls, persistence, mutation, network, launch or migration. Every unsupported action remains disabled regardless of selection.

Ordering: refresh request returns monotonic generation; starting refresh clears rows/selection so old metadata cannot be acted upon. Only matching latest pending generation may publish or fail, exactly once; superseded/interrupted/repeated completions rejected. Validation happens before publication; duplicates, null/invalid identities/status rejected with no partial result. Caller must explicitly fail a malformed current request. UI filter/group changes prune hidden selections; select-visible affects only current visible immutable UUIDs; explicit set-selected is idempotent, never implicit toggle. Selection commands bind view revision to reject stale row clicks after filter/refresh changes. Unknown UUID rejected. Snapshots defensively immutable. Empty LOADED differs from unavailable IDLE/FAILED; neither implies a provider exists.

Tests before acceptance: defensive copies, duplicate/invalid metadata, Unicode/Vietnamese accent search including đ, locale independence, group exactness, hidden selection pruning, select-visible, duplicate clicks, stale revision, out-of-order refresh/failure, double completion, failure clears rows, overflow fail-closed and all unsupported actions remain false. Synchronize model transitions for consistent snapshot publication; no claim of durable journal or auth authority. Fresh Activity initially renders honest empty/unavailable state only; no user database read.

Plain-JDK harness tests the exact Android model source without Android dependencies. Hosted installed JDK17 compiles/runs with --release17; local limited JDK21 compiler can exercise behavior only, clearly not Java17 compatibility proof. Existing Android compile/lint/APK gate checks actual D8/source/API compilation. No Java21 kernel import or new libraries. Native view behavior, rotation, insets, TalkBack, storage adapter/auth/provider/shortcut/device acceptance remain pending; no old-source deletion.

R5.1 independent review corrections before checkpoint: bind filter changes to expected view revision as well as selection, so queued older filter criteria cannot replace newer criteria. A stale filter returns current unchanged snapshot. Extend overflow-before-write regressions across publish/fail/filter/select/selectVisible, preserving phase/count/query/selection on failure. Concurrent duplicate refresh completion has exactly one winner. All108 presentation checks pass locally; Java17/Android hosted evidence pending.

R5.1 remaining adapter boundary: strings/lists currently have no ingestion budget and matching is synchronous. Before loading external metadata, define bounded adapter input/streaming strategy and cache normalized search text without inventing a product clone-count cap. Provider/package strings are untrusted display references, not validated runtime identities. Current Activity supplies no adapter or entries. Additional tests cover filtering during pending refresh, pre-filter selection events, stale completion after failed/replaced refresh, and no-op select-visible revision stability.
