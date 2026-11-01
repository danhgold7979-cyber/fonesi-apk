# Android catalog transaction adapter — source/compile entry, no runtime activation

Prerequisites: adopted nativecatalogv1 generation plan, reviewed CatalogKey/StyleValue,
reviewed four SQL primitives and owned nativeSQLite evidence. App still has no
storage caller/access broker/provider. This adds callable implementation, NOT a
fake claim that a production authorized writer already exists.

## Exact source boundary
New package-private `vn.fonesi.app.storage.OwnedCatalogWriter` accepts an exclusively
owned writable SQLiteDatabase handle, validated CatalogKey and StyleValue, exact
internal catalog UUID/revision snapshot, CancellationSignal and a commit-boundary
interface. It opens/creates/deletes NO file/database, performs no bootstrap/migration,
changes no PRAGMAs, invokes no UI/provider and has NO call site in MainActivity.
No permissions/components/branding/layout changes. Reject main-thread use, nulls,
read-only/closed handles, pre-existing transactions and malformed snapshots before
writes. Handle exclusivity is an explicit caller ownership precondition; inTransaction
only describes the calling thread and cannot prove no other thread owns the handle.
Actual private-file ownership/opening is still a separate future adapter.

Core Java sources become an explicit app source input (no new library/dependency).
SQL files are packaged as read-only assets from the existing storage/sql directory;
exact packaged bytes must be verified against source in COMPILE artifact checks.
This materially changes compile inputs, so a real new APK compile/lint/static asset
verification is required. No existing APK PASS is transferred.

## Transaction implementation
- Worker-thread `beginTransactionNonExclusive()` is Android's public IMMEDIATE mode
  (API11); no yield/nesting and no nonpublic APIs.
- Under that same transaction guard UTF8 encoding, application_id1178815793,
  user_version1, expected exact main sqlite_master/table/index definitions, no user
  TEMP/attached schemas, and exactly one strictly typed canonical UUID/nonnegative
  long revision metadata row. Bound schema/metadata projections before Java transfer;
  unknown shapes/extra objects/unsupported PRAGMA results fail closed.
- Use SQLiteStatement bindBlob/bindLong/bindString/bindNull and executeUpdateDelete
  to claim revision and update exactly one existing key. Never upsert. Before byte
  allocation, raw badge is limited to4096UTF16, then16KiB UTF8 bytes as an operation
  budget only. Over-budget => BUDGET_EXCEEDED, not corruptstyle/trim/rewrite. Use the
  already validated raw badge (including null) and exact accent, not display F/trim.
- Read back exact key/style bytes/types and new metadata revision within transaction.
  Blob-bound read query uses public rawQueryWithFactory/SQLiteQuery binding and
  SQLiteCursor; no interpolation or lossy String selection arguments for identity.
  Verify exact asset bytes; derive the single reviewed query without its terminal
  semicolon (rawQuery forbids it), reject embedded/multiple statements. Close every
  Cursor/SQLiteStatement on all paths before commit/rollback; close failures cannot
  produce false success or confirmed rollback.
- Check cancellation before reservation and between stages. Before COMMIT_ENTERED,
  failure ends the unsuccessful transaction and reports rollback only when confirmed.
  Failed rollback => INDETERMINATE and close the exclusively owned handle.

## Auth/cancel/commit boundary — interface only, no manufactured grants
No `authorized=true` parameter and no always-allow broker implementation. A future
real broker's package-private CommitBoundary receives the exact immutable Snapshot,
CatalogKey and StyleValue at both staging checks and commit, plus a one-shot
CommitAction. It must bind authorization to that exact request, not an unrelated
closure or same-name row. It
must synchronously invoke it on the transaction-owning worker only after atomically
checking current owner/session/foreground/auth epoch and cancellation, or decline.
The action itself rejects wrong-thread, delayed, duplicate and reentrant calls. On
method return the action is invalidated, so retained callbacks cannot commit later.
There is NO current CommitBoundary implementation/caller in app runtime. Its existence
is not proof of authentication or full writer integration.

COMMIT_ENTERED occurs just before setTransactionSuccessful/endTransaction. This is
not durable success. After entry, any exception is INDETERMINATE with handle closure
and no blind replay; never claim rollback. Once commit returns successfully report
COMMITTED. Cancellation arriving after entry cannot retroactively promise rollback.
Broker integration must establish callback ordering without blocking main-thread
callbacks on database I/O. No broker/user credential prompt is added here.

## Verification and remaining gates
Source/negative contract tests: no runtime caller/file open/permissions/new APIs;
exact app source/assets wiring; packaged missing/altered/duplicate SQL assets rejected;
receipt binds source+asset hashes; all existing manifest/backup/lint/signature gates
remain strict. Reuse nativeSQLite SQL proof, test only impacted receipt fixtures.
Actual AGP/D8/lint/APK run required after independent source review; installedSDKonly,
no new licenses. Actual Android SQLite/Cursor pooling/cancel/revocation/process-death,
real broker authentication, exclusivehandle/privatefileownership and app-wide writer
coverage remain NOT_RUN. No full T13/T25 or V2 acceptance.

Primary API references (source availability, not runtime proof):
https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase
https://developer.android.com/reference/android/database/sqlite/SQLiteStatement
https://developer.android.com/reference/android/database/sqlite/SQLiteCursor

## Compile dependency transition
Once core source is added to app, CatalogKey.java and StyleValue.java cease being
HOST-only exempt paths. The compile gate must treat their changes as APK-affecting;
previous detached-source docs are historical at9b760a81/8b372a22, not current claims.
MainActivity remains byte-identical and forbidden dispatch/persistence calls remain
asserted. Shell source allowlist adds ONLY the exact package-private adapter file,
not arbitrary new classes. Source caller scan confirms no runtime adapter invocation.

## Bounded database-list refinement
Use a bounded name-only SELECT from public `pragma_database_list()` instead of raw
PRAGMA database_list, avoiding arbitrary alias/path transfer. SQLite introduced
PRAGMA functions in3.16.0 (https://sqlite.org/releaselog/3_16_0.html); reference AOSP
API26 uses3.18.2. Unknown/omitted function or unexpected rows fail closed with no
unbounded fallback. This source availability is not proof for every Android/OEM.
Revision projection is CASE integer-only before CursorWindow transfer; retain its
type flag and reject all other classes. Tracked resource closers mark uncertainty
even when another exception suppresses close failure, so no confirmed rollback is
returned after a failed resource close.
