# Manager-owned catalog generation/writer contract — draft, no activation

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
  stored fields are identical. Any failed/rolled-back transaction advances neither.

## One legitimate conditional style transaction (future adapter)
1. Serialize through one manager-owned writer boundary. No arbitrary caller SQL.
2. Check foreground/auth/explicit user operation before queueing and again under
   transaction before mutation. Cancellation before commit rolls back; once commit
   succeeds report committed/readback or indeterminate, never pretend rollback.
3. Acquire write transaction; validate exact supported schema and singleton shape.
   Require exactly id1, typeof(catalog_id)=text with strict canonical internal UUID,
   typeof(revision)=integer in0..Long.MAX_VALUE. No coercion of REAL/TEXT revision.
4. Match current catalog_id and revision to edit snapshot. Conditional revision
   increment must affect exactly1 row with overflow guard; otherwise STALE/BLOCKED.
5. Update ONLY existing composite-key row's validated accent/badge (no upsert).
   Exactly1 affected row required. Preserve key/package/token/other fields unchanged.
6. Read back exact intended values/key/new revision under the same transaction,
   recheck current cancellation/auth/lifecycle, then commit. Failure rolls back both.
   A final check alone is NOT linearization: auth revocation/cancel and commit need
   one reviewed serialized coordinator/critical section defining their ordering.
   Revocation/cancel ordered before commit must prevent it; after a committed result
   it cannot pretend rollback. Android callback/thread behavior must prove this.
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

## Remaining decision
Confirm whether the referenced distinct inert manager-catalogv1 is adopted as the
fresh native storage format, versus direct legacy Room-compatible ownership. Either
can meet original product scope, but mixing their schemas/metadata is forbidden.
This is an engineering schema choice requiring reviewed migration/retention design,
not consent to reduce clone counts or activate work profiles. Exact-key decoder
work is independent and can proceed while this choice is reviewed.
