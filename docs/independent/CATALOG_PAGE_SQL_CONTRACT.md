# Bounded catalog page SQL — next original-scope read prerequisite

P055/RM-13b/SUP-S08/SEC-021 remain unaccepted. This entry proposes independently
authored SQL and real temporary-owned native SQLite fixtures, not an active Android
reader, production auth, legacy migration, schema adoption or provider architecture.
It builds on adopted nativecatalogv1 and the reviewed key decoder. Retained
REBUILD_CATALOG_QUERY_RECIPE.md is a design reference, never old code/test import.

## Output and interface
Three fixed parameterized SELECT assets: first page, continuation page, final metadata.
No dynamic identifiers/SQL, writes, bootstrap, attached DB, PRAGMA changes or fallback.
First page binds signed Long.MIN_VALUE and uses rowid >=; continuation binds the last
ACCEPTED rowid and uses rowid >. Never increment a cursor (MAX must not wrap). Bind as
SQLite INTEGER, not interpolated text. A cursor is ordering position, not identity.
Exact source names: catalog_page_first.sql, catalog_page_next.sql, catalog_read_meta.sql.

Page query takes at most129 ordered manager rows and LEFT JOINs them from singleton
metadata (id1), so a valid empty page has a metadata-only sentinel. Missing metadata
is no result and failure, not empty success. All rows carry generation/revision and
an explicit row-present flag. Metadata is never filtered to an expected revision in
SQL; the caller must observe changes rather than mistake them for empty pages.

For exact engine_key, clone_id, package_name, label and group_name, return separate
SQLite type, byte length/null flag and bytes ONLY when typeof(TEXT) and <=16384 bytes.
No oversized/nonTEXT raw payload crosses the SQL projection. Group NULL is distinct
from empty TEXT. Bound metadata catalog_id to36 bytes and project revision ONLY if
integer, with independent type/nonnegative validity facts; malformed large TEXT/BLOB
revision must not cross as a raw value. Do not select launch token, accounts, style,
cluster or any guest authority. Readiness must match exact UNVERIFIED TEXT bytes and
launch_allowed exact INTEGER0; projected booleans are inert-state validation, not
authority. Read-only SQL must not normalize or repair malformed stored bytes.

## Consumer contract (future Android adapter, NOT implemented by these assets)
Before querying, validate exact supported UTF8 schema/singleton/indices and no temp/
attached schema; source must already be authenticated and exclusively owned. Validate
every returned column type/flag/length consistency before decoding; unknown shapes
fail retained. Strict UTF8 REPORT + roundtrip applies to TEXT identities and display
fields, <=4096UTF16 per field; only group NULL may become empty display without write.
Package and label remain nonempty supported display fields; IDs remain opaque and
may be empty/whitespace. No package reconstruction, identity normalization or I/O.

Accept at most128 rows and256KiB raw projected field bytes per page; row129 is only
lookahead and is not published. Stop before an aggregate-over-budget row, retaining
the last ACCEPTED cursor; first-row budget failure fails the page. Final normalized
presentation bound8192UTF16 remains a later renderer gate, not a storage constraint.
A native cursor's own allocation/locking/cancel latency is not a strict heap/deadline
guarantee. The bound here is projected cells and consumer publication, not all SQLite
internal allocations. The SQL itself does not implement UTF8/aggregate/UI policy.

Each accepted row must have the same canonical generation and integer revision,
matching the incoming page cursor's snapshot. Positions strictly increase and exact
CatalogKey duplicates reject. Close page cursor before a fresh independent metadata
query, require exactly one valid matching snapshot, then recheck owner/request/access/
cancellation/deadline before delivery. A stale/changed/cancelled/failed page is never
partially published or combined with a newer revision. Any later-page mismatch requires
restart; no automatic replay of a protected mutation. All trusted writers must bump
revision atomically; unsupported external/uncoordinated writers remain an activation
block, not magically defeated by double reads. Empty/shrunk pages still final-check.

## Owned native SQLite fixture acceptance
- Actual SELECT assets on fresh owned v1 temp DB: empty,1/128/129 rows, ordered signed
  MIN/negative/zero/positive/MAX rowid and continuation excluding last row without+1.
- Exact empty/space/NUL/case and different-provider same-ID keys retained byte-for-byte.
- Type/null/length separation, invalid UTF8 TEXT and BLOB-in-TEXT-affinity preservation;
 16KiB boundary and oversized fields do not transfer raw values; malformed large
 metadata/revision is bounded, no mistaken valid/empty result.
- Metadata/readiness/launch invalidity and missing/extra metadata reject at fixture
 consumer guard, with raw database rows retained. A missing singleton is not empty.
- Change revision/generation after page fetch and before fresh final metadata read,
 including shrink toempty, detects stale. This tests SQL/owned fixture orchestration,
 NOT Android CursorWindow refill, real cancellation or all-writer enforcement.

Tests must distinguish direct SQL proof from any fixture consumer assertion. No use
of sqlite3 CLI (unavailable); Python native SQLite binding version recorded. New SQL
assets would become APK inputs and require exact verifier asset-map update/independent
review plus genuine compile at a later coherent integration, not old receipt reuse.

## Frozen column/type/byte projection interface
`length(CAST(field AS BLOB))` measures TEXT bytes, including embedded NUL; never use
SQLite length(TEXT). Payload is `CAST(field AS BLOB)` only inside a CASE guarded by
TEXT storage class and byte length <= bound. Apply identically to catalog_id (bound36)
and all five row fields (bound16384). Exact readiness compares its BLOB bytes to
X'554E5645524946494544', with TEXT type; no collation/trim/default comparison.

Page columns in exact order:
1. catalog_id_type (SQLite typeof text), catalog_id_is_null (INTEGER0/1),
   catalog_id_byte_length (INTEGER, -1 unless TEXT), catalog_id_over_budget (INTEGER0/1),
   catalog_id_bytes (bounded BLOB orNULL).
2. revision_type (typeof text), revision_valid (INTEGER0/1 for INTEGER>=0),
   catalog_revision (INTEGER only if original typeof INTEGER, otherwiseNULL).
3. row_present (INTEGER0/1 from joined row_position IS NOT NULL, never truthiness),
   row_position (signed INTEGER for an ordinary row, NULL only for empty sentinel).
4. For fields in order engine_key, clone_id, package_name, label, group_name: each
   FIELD_type (typeof text), FIELD_is_null (INTEGER0/1), FIELD_byte_length (INTEGER,-1
   unless TEXT), FIELD_over_budget (INTEGER0/1), FIELD_bytes (bounded BLOB orNULL).
5. readiness_inert and launch_inert (INTEGER0/1); empty sentinel row projections NULL.

The LEFT JOIN empty sentinel has row_present0 and all columns from row_position
onwards NULL; metadata still fully validated. TEXT empty BLOB differs from stored
NULL. NonTEXT values return type/null/-1/overbudget0/NULL, never their raw payload.
TEXT oversize returns type/null/exactbytecount/overbudget1/NULL. Strict consumers
reject inconsistent flags/types, not coerce them. SQLite evaluation internal memory
remains outside this transfer-bound claim.

Final metadata columns: meta_id_type (typeof text), meta_id (INTEGER-only bounded
projection), then the same eight metadata columns above. Query all metadata rows
with LIMIT2, no id1 filter, so fixture cardinality/type/id1 guard detects extra rows;
zero rows cannot pass. Page WHERE id1 does not replace that mandatory pre/final guard.
