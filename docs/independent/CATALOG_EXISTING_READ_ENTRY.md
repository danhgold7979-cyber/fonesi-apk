# Existing catalog read entry — proposed next source boundary

This is a source design, not an active storage path. Original P055/RM-13b/SUP-S08
retention requirements and the adopted distinct nativecatalogv1 apply. No legacy
Room compatibility, work-profile approval or production access gate is inferred.

## Narrow outcome
A future package-private worker-only entry may inspect the fixed credential-protected
manager catalog path and, only for a pre-existing eligible file, return an exclusively
owned READONLY SQLite handle inside a closeable lease. It returns no rows, no empty
catalog success and no writable handle. The lease is NOT a validated catalog snapshot
until exact encoding/schema/metadata and bounded reader validation finish. The current
style writer rejects read-only handles; this entry cannot activate it. No MainActivity
call, file creation, fresh bootstrap, upgrade, migration, repair, chmod/chown or delete.

Input: application Context, CancellationSignal, the caller-owned immutable source
request identity and a future real source-read access boundary. The same identity
object must reach both checks; it is not created or converted into authority here. No caller pathname/URI, requested mode, arbitrary SQL or `authorized=true`.
The boundary must check current owner/session/foreground epoch for this read request
before ANY framework directory getter/path/stat observation and before lease transfer;
it is an interface only, no implementation
or fabricated grant. Cancellation before/after open closes owned resources and does
not publish a handle. A future UI reader must independently recheck access before
publishing any rows. This design does not establish auth/lifecycle linearization.

## Path and ownership sequence
1. Reject main thread/missing inputs. Acquire the single-process exclusive coordinator
   reservation below before first observation; cancellation/access check precedes
   obtaining application Context. Revalidate that actual application Context is
   nonnull, not device-protected, and current user unlocked before resolving any path.
   Then obtain its framework no-backup directory. The framework
   getter may ensure its private parent exists; no catalog file is created by us.
   That directory-side-effect needs actual runtime authorization when later activated.
2. Fixed leaf `manager-catalog-v1.sqlite`; canonical child must be the exact direct
   child of canonical framework parent. Trusted framework ancestor aliases may
   canonicalize: lstat the original final parent component and canonical parent,
   require both directories (no final-component symlink), same dev/inode/ownership.
   Recheck original-to-canonical mapping and both identities before transfer. No
   external/custom directory or symlink-leaf fallback.
3. lstat parent and leaf; no symlink following. Obtain Process.myUid and actual
   effective UID/real/effective GID; use CatalogOwnership only as the ownership/write
   condition, not complete path authority. Require matching directory/regular types;
   record dev/inode for both. Missing leaf is MISSING, never an empty valid catalog.
4. Reject zero-size files and any fixed leaf `-journal`, `-wal` or `-shm` sidecar
   before open; retain all files. Each sidecar uses lstat: only ENOENT means absent;
   permission/I/O/other errors fail retained, and a symlink also counts as present.
   Do not invent a new whole-file validity/size cap.
   Existing reader budgets (128 rows, 256KiB page, 16KiB field, 4096UTF16) apply to
   later bounded queries, not this file. Native open memory/latency is not a hard
   bounded-allocation guarantee; failure/cancellation must not become empty success.
5. Open existing path READONLY|NO_LOCALIZED_COLLATORS using the public four-argument
   SQLiteDatabase.openDatabase. NEVER CREATE_IF_NECESSARY, SQLiteOpenHelper, fallback
   readwrite, WAL enabling or default destructive error handler. Custom corruption
   handler throws a generic SQLiteDatabaseCorruptException and never logs a path,
   deletes or returns to retry. This policy is source intent, not OEM runtime proof.
6. Recheck read-only/open handle, cancellation/access, parent/leaf dev/inode/type/
   ownership/canonical identity and sidecar absence before transfer. Failure closes
   the handle and retains files; close failure is RESOURCE_UNCERTAIN, not success.
   All exceptions are generic typed failures without private paths/keys in messages.

The trusted single-process/private-UID boundary is explicit. lstat/recheck is not an
atomic open-by-inode or defense against hostile same-UID code/root/symlink races.
If required exclusivity cannot be established by the future app-owned coordinator,
this entry is unavailable. No retry, same-name replacement adoption or blind replay.
Closeable lease binds owner thread and the observed file identity; callers must not
share it or retain a handle beyond the lease. A positive open is not schema acceptance.

## Concrete coordinator and lease lifetime
New package-private app-owned read-entry coordinator is a single static one-per-process
nonblocking reservation (no main-thread waiting, no per-call independent lock). It
returns BUSY if occupied; absence/unavailability has no positive fallback. Acquire
before the first access/path observation, transfer exactly once to a successful lease,
and hold through confirmed SQLite handle close. No raw handle is passed to access
callbacks. Access checks receive exactly the same immutable Request identity object;
a real future broker must associate that object with its actual current session and
epoch. Constructing a Request is NOT a grant. No broker implementation is added.

Before successful transfer, opener owns handle+reservation and closes/releases on
all failures. After transfer, only returned lease owns both. Lease handle access and
close are owner-worker-thread only; wrong-thread/after-close access throws a generic
error without returning a handle or releasing reservation. Duplicate close after a confirmed close on the
owning thread is idempotent; after uncertain close it repeats RESOURCE_UNCERTAIN. A wrong-thread close refuses and leaves owner responsible
for cleanup, never releases another thread's live resource. Any SQLite close failure
quarantines the reservation for the process lifetime (no reuse/retry) and reports
RESOURCE_UNCERTAIN. No unconditional finally-release after uncertain close.

This reservation coordinates ONLY this new read-entry path within this process. It
does not magically govern arbitrary SQLite handles, the dormant writer interface,
other processes, backups or future imports. Every future runtime participant must
use a common reviewed coordinator before enablement; currently there are no callers
or broker. The public app manifest has only the default-process MainActivity; adding
another process/writer invalidates this bounded integration assumption. No hostile
same-UID exclusion or cross-process locking is claimed.

## Source prerequisites still to resolve before implementing opener
- Android methods/min26 signatures and exact three known sidecar observations; no
  whole-file byte-equality or absolute latency guarantee.
- Actual broker request/session association remains unimplemented; the same identity
  interface is mandatory, never substituted by a boolean/no-op in runtime.
- Actual Android lease lifetime/revocation/close behavior requires runtime proof;
  source enforces owner-thread and quarantine semantics above.
- Mandatory exact-schema/encoding guard before rows; share only safe validation logic,
  not transaction mutation paths. Read consistency needs generation/revision rechecks.
- Static/owned fixtures plus genuine compile entry; actual framework file-mode,
  SQLite corruption callback, pooling, recovery and cancellation tests remain NOT_RUN.

No opener source is authorized by a positive pure ownership fixture alone. This
proposal deliberately exposes the remaining entry prerequisites instead of treating
native SQLite HOST results as actual Android file/runtime evidence.

## Implementation precision for bounded source entry
Observed cancellation at pre/post checks prevents transfer; a racing signal after
the final check is NOT atomic revocation/transfer linearization. That integration
remains unavailable. Lease stores application Context (not Activity), both parent
paths, fixed leaf and observed identities; each future handle access rechecks access,
unlocked storage, paths and cancellation. Raw read-only handle use is restricted to
trusted future reader code that must not retain it; Java cannot revoke an escaped
reference, so no universal capability/hostile-caller enforcement is claimed.
If native open throws without returning a handle, conservatively quarantine the
reservation and return RESOURCE_UNCERTAIN; do not claim unobserved native cleanup.
No exception text includes source paths or row contents. SDK logging, if any, is a
platform behavior not controlled or certified by this source-level exception policy.

Primary API availability refreshed 2026-11-01 (not runtime observations):
- https://developer.android.com/reference/android/system/Os (lstat API21)
- https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase (four-argument open API11)
- https://developer.android.com/reference/android/database/DatabaseErrorHandler (API11)
- https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir() (API21)

## Failed lease access
Lease operations expose a package-private typed Failure with generic text and a
status; construction remains internal. Access callbacks/platform RuntimeExceptions
are translated without preserving their message/cause. Observed cancellation becomes
CANCELLED. Any owner-thread accessor failure invalidates further accesses on that
lease; it does NOT automatically close it. The trusted owner must close in finally,
including after denial/cancellation. Wrong-thread access fails without transferring
or releasing the owner's resource. Close remains permitted after invalidation; an
uncertain close remains quarantined and repeats its typed uncertain failure.
