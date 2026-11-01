# Dormant Android catalog page read — proposed source entry

This is a next original-scope P055/RM-13b/SUP-S08/SEC-021 prerequisite, not a
work-profile change or runtime authorization. ExistingCatalogRead and CatalogPage
are reused. No MainActivity caller, access implementation, bootstrap, migration,
write, repair, launch, new permission or dependency is introduced. A successful
internal read is not permission to deliver rows to UI.

## Entry, ownership and access
A package-private worker-only adapter accepts application Context, the exact
ExistingCatalogRead.Request identity and AccessBoundary, CancellationSignal, and
optional immutable CatalogPage.Cursor and a read budget of1..5000 milliseconds. Reject missing boundary/cancellation/context
and main-thread use before opening. The existing opener acquires its reservation
before observations and checks access before framework directory resolution. It
remains the sole file opener; no caller-supplied path or writable database is added.

The adapter owns the returned lease and closes it in finally. No raw database,
Cursor, mutable array or lease escapes. Before every independent query and after
all query cursors close, obtain the handle through lease.database() so owner,
access, cancellation, unlock, path identity, sidecars and sticky invalidation are
rechecked. Cancellation/deadline/access checks also bracket row iteration. A future
real broker must bind Request to current token/session and exact intended read;
this draft supplies no such broker. No synthetic positive access callback is added.

Capture SystemClock.elapsedRealtime() once before the opener and require a nonnegative
start; before observations/queries, on every row and after cleanup before return,
read it again, reject negative/backwards time, and expire when now-start >= budget.
Both nonnegative values with now>=start make subtraction safe. Invalid budget fails
before opening. This is an observed monotonic deadline, not a guarantee to interrupt
native filesystem/open/SQLite calls at the deadline. CancellationSignal is still
passed to query APIs; neither mechanism provides atomic UI delivery revocation.

## Exact format and bounded SQL inputs
Load only reviewed schema and three page/meta assets, bounded to4096 bytes each,
exact SHA256 and strict UTF8. Verify packaged bytes before deriving query text with
one reviewed terminal semicolon removed. Never execute assets as migration or accept
caller SQL. First/next query selection follows the immutable continuation presence;
bind signed Long.MIN_VALUE or cursor.lastAcceptedPosition through SQLiteQuery.bindLong,
not string interpolation/coercion or arithmetic.

Before metadata/page access, require exact distinct manager-catalog v1 format:
UTF8, application_id1178815793, user_version1, no ignored constraints/read_uncommitted,
only main and optional empty temp, no attached DB or TEMP user schema. Exact three
main sqlite_master objects, DDL bytes, composite PK index/BINARY collation and index
shape must match the frozen schema. Reuse/extract the current independently reviewed
writer's read-only format checks rather than invent a weaker parallel schema. Any
shared extraction must preserve the writer checks and be independently reviewed as
an affected production change. No metadata defaults, schema repair, ALTER or writes.

Schema/name/database-list projections stay bounded before Java transfer, including
malformed revision values. Unknown runtime PRAGMA/table-valued pragma support fails
closed, not guessed compatible. Canonical generation and strict integer revision
are checked by CatalogPage metadata plus exact source cardinality.

## Cursor transport and page sequence
1. After format verification, query the fixed full-cardinality10-column metadata
   asset; require exact expected column names/order/count, exactly one row and no
   second row. Copy actual storage types, close Cursor, then begin CatalogPage.
2. Query exact37-column first/next asset. Validate names/order/count. For each
   row, cancellation/access remains observed. Map FIELD_TYPE_NULL to null,
   FIELD_TYPE_INTEGER to Long via getLong only after type check, fixed typeof
   vocabulary TEXT to String only after type check, and bounded BLOB to byte[].
   FLOAT/unexpected TEXT/BLOB/type combinations fail, never Cursor coercion.
   At the129th projected ordinary row (after128 accepted), transport ONLY exact
   shape/names plus metadata, row-present and signed row-position; leave other cells
   null in the owned array and do not read payload/type/inert getters. The consumer's
   reviewed129-lookahead branch deliberately ignores them. This exception does not
   apply to earlier rows or an aggregate-stop candidate, which require the complete
   structurally valid raw projection before selecting STOP.
3. Projected payload length/oversize/type flags must be checked before requesting
   payload from Cursor. The SQL CASE itself provides the transfer bound; Java must
   enforce the exact reviewed per-column classes and declared limits too. No
   arbitrary CursorWindow/heap bound claim: framework/native allocation is external.
4. Feed stable owned snapshots to the already-reviewed pure consumer. Stop at its
   STOP_BEFORE_ROW; do not consume a further row or skip the pending row. The
  129th-row exception and aggregate-stop semantics remain exactly its contract.
5. Close the page Cursor successfully before endInput. Then obtain a fresh handle,
   recheck the exact format and perform a separate fresh10-column metadata query,
   again full cardinality and exact types, close it, and call finish.
6. Recheck lease/access/cancellation after all Cursors close. Close the lease before
   returning an immutable internal page. A close failure discards the page. No raw
   handle remains in the returned result. Future UI delivery still needs its own
   lifecycle/auth/cancellation linearization; no final-check race is called solved.

No long-lived read transaction may accidentally make the final metadata a repeat
of the initial SQLite snapshot. The opener supplies a fresh read-only handle; reject
unexpected calling-thread transactions, while process ownership/exclusive use is a
separate caller/opener invariant, not proved by inTransaction(). Do not claim these
checks defeat hostile same-UID external writers or guarantee all-writer participation.
All production trusted writers must adopt generation/revision discipline before
runtime activation. Same-generation/revision tampering is not repaired by this reader.

## Failure and uncertain resources
Return only generic typed statuses and no private key/path/payload/exception causes.
Missing source is distinct from a valid empty page. Wrong schema, stale generation,
malformed projection, byte/decoded operation budget, cancellation and unavailable
access all fail without publishing partial rows. No logging/default row/empty-on-error.
Cursor or lease close failure is RESOURCE_UNCERTAIN, never successful page delivery;
the adapter must explicitly signal quarantine to the lease. Add a package-private
owner-thread-only Lease.quarantineResources() that marks sticky invalidation and
resource uncertainty, without releasing or transferring the reservation. It must reject calls after confirmed close rather than poisoning a reservation
that might now belong to another lease. Invoke it before closing the owned lease. It
does not skip the attempt to close the DB. Subsequent successful DB close must retain
this quarantine and report RESOURCE_UNCERTAIN; duplicate close must do likewise.
Thus external Cursor-close uncertainty cannot be erased by the existing successful
DB-close path. This is an affected opener change requiring source/static review,
not a claim that its current implementation already handles external resources. Preserve the first causal
status only when cleanup is confirmed; uncertain cleanup takes precedence. Owner-thread
cleanup is mandatory even after parser failures. No retries/reopen/replay on uncertainty.

## Evidence before any activation
Source/static integration checks must prove no caller/permissions/open/write additions,
exact assets/pins/column maps, typed getters, cancellation-aware public query API,
resource closure and generic errors. Actual new JDK17/AGP/D8/lint/package proof is
required for changed Android code; it does not validate Cursor behavior.

Actual owned Android fixture proof remains required for malformed TEXT/BLOB/NUL,
CursorWindow refill, full metadata cardinality, cancellation during iteration,
close failures where testable, path/source replacement, file retention, locked user,
real auth/lifecycle integration and final delivery races. Optional AndroidX artifact
inspection remains separately paused after denial; no dependency download or device
operation is authorized by this document. No ANDROID/PROVIDER/PHONE or task uplift.

Public API references (read2026-11-01):
- https://developer.android.com/reference/android/database/Cursor
- https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase
- https://developer.android.com/reference/android/database/sqlite/SQLiteProgram
