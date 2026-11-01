# Private catalog ownership decision — source prerequisite, not an opener

Original P055 / RM-13b / SUP-S08 require retained unknown/malformed storage and no
destructive fallback. The adopted native catalog v1 still needs a trusted private
file boundary; a schema-valid arbitrary file is not sufficient. This contract
covers ONLY a new pure decision function over caller-observed facts. It neither
observes nor changes a filesystem, grants authentication, opens SQLite, creates a
catalog, nor enables the existing writer. No old implementation is imported.

## Reference inputs and deliberate boundary
Retained `reference/history/local-rebuild/docs/REBUILD_CATALOG_API26_RECIPE_AUDIT.md`
and `REBUILD_CATALOG_DIRECTORY_MODE_PLAN.md` are design references only. New source
is authored independently. Current Android public APIs provide lstat/UID/GID facts;
this function has no Android dependency and no authority to assert those facts.
Future Android adapter must obtain all observations from the fixed framework
credential-protected no-backup path, reject locked/device-protected storage,
validate parent/canonical path and leaf type without following a symlink, and
recheck device/inode around opening. No arbitrary caller path or UI input.

## Exact decision
Inputs: expected role DIRECTORY or REGULAR_FILE; observed role DIRECTORY,
REGULAR_FILE, SYMLINK or OTHER; low permission/special mode bits 0..07777;
observed owner UID/GID; current app UID and real/effective process GID/effective UID.
Unknown/null roles, negative IDs/mode or mode bits outside 07777 reject. The adapter
must separately derive observed role from lstat type bits, never label a symlink by
its target. Pure inputs are observations, NOT capability tokens.

Always require owner UID == app UID and effective UID == app UID, expected role
matching observed role, and no world-write bit (0002). Group-write (0020) is always
rejected for regular files. A directory may have group-write ONLY when its GID,
real process GID and effective process GID all equal app UID. This permits the
reference framework own-primary-group directory case without permitting cache,
foreign or supplemental groups. Non-group-writable paths do not require GID equality.
Read/execute/special bits do not grant write authority here and are not rewritten;
this predicate is only the documented ownership/write-permission condition, not
complete confidentiality, SELinux/ACL or sandbox attestation. Do not claim its
positive result alone means a path is safe or can be opened. No chmod/chown/umask,
repair, deletion, permissive retry or fallback is implemented.

Return a typed decision with specific rejection reason, not a general authorization
boolean. Future callers must combine it with path/type/identity/sidecar, source
encoding/schema and access/lifecycle gates. Host inputs cannot prove observed OEM
facts; all Android path/open/race/power-loss proof remains NOT_RUN.

## Tests and integration boundary
Own direct Java tests enumerate expected role/type/UID/GID/mode combinations:
0700/0750/0771 own-group directories, foreign/cache group rejection when writable,
world-write rejection, regular0600/0644 versus0660/0666, symlink/type mismatch,
unknown/negative/out-of-range inputs, and absence of mutation/Android/I/O APIs.
No synthetic positive fixture is labeled an actual private-file observation.
Current core directory is now an APK source input, so adding the helper requires
new compile evidence when published; do not reuse detached HOST exemption. Keep
MainActivity and adapter unwired. Test/receipt plumbing must count this suite
explicitly before claiming hosted coverage.

## Subsequent source work, not activated by this contract
A future read-only-existing catalog reader must fail retained on empty/unknown/
partial/corrupt databases, never initialize or repair them, and use a throwing
nondeleting error handler. Exclusive fresh bootstrap is a separate reviewed entry;
a false/uncertain exclusive create result never grants initialization. Do not
reinterpret a read-only-existing recipe as permission to reopen an existing DB
writable for the new transaction adapter. That transition requires proven source
validation, handle ownership, all-writer discipline and actual access integration.

Primary API availability only (not runtime evidence):
- https://developer.android.com/reference/android/system/Os
- https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()
