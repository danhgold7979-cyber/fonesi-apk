# Manager-owned catalog generation/writer contract — adopted design, no runtime activation

Original scope P055/RM-13b/SEC-021/SUP-S03–05/08 and EXEC-ID-R1-01.
This contract is distinct from legacy Room schemas1–6 and from V2 profile proposals.
No legacy user_version/hash/schema is silently changed. A new reader cannot satisfy
legacy migration or authorize copying sessions/credentials/opaque launch authority.

## Existing reference design, not a new row-revision fiction
Frozen REBUILD_CATALOG_API26_RECIPE_AUDIT.md specifies a distinct manager catalogv1:
`catalog_meta(id=1,catalog_id,revision>=0)` and `manager_catalog` with exact composite
`(engine_key,clone_id)`, inert readiness UNVERIFIED and launch_allowed0. Its declared
consistency condition already requires all trusted writers atomically increasing
revision. We propose completing that protocol, not treating historical changedAt
as revision and not adding columns to legacy Roomv4.

## Generation and ABA invariants
- Catalog_id is a newly generated internal catalog-generation UUID, NEVER a
  transformation of engine_key/clone_id. It grants no execution/auth authority.
- Each first-party mutation of any catalog row increments the SINGLE catalog
  revision atomically in the same transaction. Delete/reinsert, style/name/group,
  migration projection and reconciliation all participate. Conservative invalidation
  of unrelated selections is acceptable; silent replay of old user intent is not.
- Refuse at Long.MAX_VALUE; do not wrap/reset. Emptying the catalog does not reset it.
- Every replacement/restore/import-generation operation invalidates all edit tokens
  and obtains a fresh catalog_id; never restore old catalog_id/revision as authority.
- An edit token also binds in-process owner/session epoch plus exact catalog key,
  generation and revision. Reopen/connection loss/process restart cancels tokens.
  Auth/lifecycle state is independently rechecked at commit, not encoded by UUID.
- Same-key delete/reinsert advances revision and invalidates old tokens even if
  stored fields are identical. Any confirmed rolled-back transaction advances neither; uncertain commit is reconciled.

## One legitimate conditional style transaction (future adapter)
1. Serialize through one manager-owned writer boundary. No arbitrary caller SQL.
2. Check foreground/auth/explicit user operation before queueing and again under
   transaction before mutation. Before COMMIT_ENTERED, cancellation/failure must attempt rollback and may report
   rolled-back only after rollback is confirmed. After entry, reconcile committed/
   readback versus INDETERMINATE; never promise unconditional rollback.
3. Acquire write transaction; validate exact supported schema and singleton shape.
   Require exactly id1, typeof(catalog_id)=text with strict canonical internal UUID,
   typeof(revision)=integer in0..Long.MAX_VALUE. No coercion of REAL/TEXT revision.
4. Match current catalog_id and revision to edit snapshot. Conditional revision
   increment must affect exactly1 row with overflow guard; otherwise STALE/BLOCKED.
5. Update ONLY existing composite-key row's validated accent/badge (no upsert).
   Exactly1 affected row required. Preserve key/package/token/other fields unchanged.
6. Read back exact intended values/key/new revision under the same transaction,
   recheck current cancellation/auth/lifecycle, then enter commit through the coordinator. Pre-entry failure attempts rollback
   and claims rollback only after confirmation; post-entry outcome is reconciled.
   A final check alone is NOT linearization: auth revocation/cancel and commit need
   one reviewed serialized coordinator/critical section defining their ordering.
   Revocation/cancel ordered before atomic commit entry must prevent it; after entry
   the outcome must be reconciled, never reported as guaranteed rollback. Android callback/thread behavior must prove this.
7. Commit/readback uncertainty requires fresh reconciliation; never blindly retry.
   Launcher update is after commit and separately reported, never guest mutation.

## All-writer coverage required before production enablement
Bootstrap; create metadata; delete; style; rename/group; imports/restores; migration
projection; engine reconciliation; cleanup/recovery must use the same transaction
protocol. Background reconciliation may change permitted metadata but cannot grant
user-session authorization or trigger protected guest operations. Unknown writer,
uncoordinated multiprocess access or nonparticipating restore => mutations BLOCKED.
Exact schema validation rejects unexpected triggers/objects; this is trusted private
app storage consistency, not a hostile same-UID/root filesystem-tamper guarantee.

## Proof gates and honest scope
Native SQLite owned-fixture tests can establish conditional revision+row atomicity,
rollback, stale/ABA, overflow, sibling preservation and busy behavior for SQL. They
cannot prove Android connection pooling, lifecycle/auth/cancel linearization,
process death, restore/file replacement or all application writer participation.
A fake boolean auth argument or mock database must not be substituted for those.
No production writer is enabled until concrete adapter and all-writer call inventory
are reviewed and tested. No active new schema/format or restore behavior is approved
by this draft. Legacy3→4 SQL fragment evidence remains a separate partial gate.

## Engineering disposition — 2026-11-01 13:05 UTC
Root adopts the referenced distinct inert manager-catalogv1 as first-party native
storage design, with UUID generation + global revision and no product scope reduction.
This is NOT activation, adoption of work-profile tradeoffs or authorization to mutate
an existing user database. Legacy sources stay retained; migration/restore compatibility
and actual Android/auth/lifecycle execution remain separate proof gates.

## Concrete source entry and commit ordering
The next bounded source consists of schema initialization plus revision-claim,
existing-row style update and readback SQL, exercised only in owned temporary-file
SQLite fixtures. They are genuine SQLite operations, not a substitute in-memory
storage engine. A fixture transaction owner tests the SQL primitives; it is not the
production Android broker/writer or proof that app-wide writers participate.

A future access coordinator owns token states ACTIVE, CANCELLED, COMMIT_ENTERED,
COMMITTED and INDETERMINATE. Entry to COMMIT_ENTERED is the atomic authorization/
cancellation ordering boundary; it is NOT a claim durable commit has succeeded.
Revocation/cancel ordered before that entry prevents entry and attempts rollback;
claim rollback only after confirmation. Once
commit has entered, an interruption cannot truthfully promise cancellation or
rollback: determine committed result by readback or retain INDETERMINATE. Do not
start any new effect on that token. The coordinator must not block Android's main
thread on database I/O; actual broker/lifecycle integration must establish ordering.
No boolean parameter or synthetic token in the fixture grants real authorization.

The SQL owner must BEGIN IMMEDIATE, guard exact encodingUTF8/application_id/version/
schema/singleton and strictly typed metadata, compare exact catalog UUID/revision,
claim revision with overflow guard, require exactly1 changed row, update exactly1
existing opaque composite-key row, verify exact style/key/revision readback, then
commit only through that coordinator. Every pre-COMMIT_ENTERED failure attempts rollback of row and revision;
only confirmed rollback is reported as such. Any exception/cancel after entry needs
committed/readback versus INDETERMINATE reconciliation, never unconditional rollback. Schema/encoding guard remains under the same write reservation.

SQL primitive test inventory: success/sibling preservation; missing/wrong namespace;
stale revision/generation; same-key delete/reinsert ABA; overflow; wrong metadata
storage class/singleton; unknown schema/encoding; busy; injected cancel after update
but before commit; readback mismatch rollback; reopen; no upsert or token/identity
change. Real SQLite fixtures establish transaction behavior, not Android grants,
process death, restore compatibility or all-writer enforcement.

All other writer categories remain UNIMPLEMENTED/DISABLED in the app until bound to
the same generation/revision protocol. Even style SQL has no active Android caller.
No repository method currently authorizes a production guest or metadata mutation.

Bootstrap fixtures use exact referenced DDL with no IF NOT EXISTS and exclusively
created owned temporary files only. Never initialize, repair, adopt or recreate an
existing unknown database; retain it and fail. No real auth coordinator is implemented
by the SQL fixture harness.
