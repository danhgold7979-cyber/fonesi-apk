# Catalog directory-mode compatibility correction — proposal only

2026-10-30 23:52 UTC. No production edit authorized by this proposal. Keep FULL readback checkpoint0b2615f separate. Root identified this source-level blocker; no device experiment was used.

## Confirmed defect and primary evidence

- Android8.0 ContextImpl `getNoBackupFilesDir()` calls `ensurePrivateDirExists`, which explicitly creates/chmods0771. Current CatalogReader applies `(mode & 0022)==0` to both directory and file, so normal framework group-write fails before catalog creation. The framework's cache directory is separately02771 with a cache GID; it is not the chosen no-backup path. [Exact ContextImpl source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/java/android/app/ContextImpl.java), lines511–527,558–563.
- Normal app startup passes its UID as primary GID; native specialization sets real/effective/saved GID to that argument. This supports checking the actual app-private group, not accepting every supplemental/shared/cache group. [Exact ActivityManagerService](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/services/core/java/com/android/server/am/ActivityManagerService.java), lines3693–3703; [exact Zygote native source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/jni/com_android_internal_os_Zygote.cpp), lines549–557.
- Android8.0 init service children set umask077. [Exact init/service.cpp](https://github.com/aosp-mirror/platform_system_core/blob/android-8.0.0_r1/init/service.cpp), Service::Start child branch; inspected through GitHub connector. This is source evidence, not a guaranteed OEM/runtime process mask.
- `File.createNewFile()` guarantees exclusive atomic creation, not a public fixed permission-mode contract. An inspected historical AOSP libcore implementation uses exclusive open0666: [libcore be16089](https://android.googlesource.com/platform/libcore/+/be16089/ojluni/src/main/native/UnixFileSystem_md.c), lines221–242. **That revision was not established as exact Android8.0 source**; exact-tag libcore retrieval was unavailable. Thus0666 masked by077 yielding0600 is an explained baseline expectation, not a claimed observed API26 file mode. [File API](https://developer.android.com/reference/java/io/File#createNewFile()). Retain runtime stat validation rather than trusting inferred mode.

## Smallest proposed production change

Separate directory permission policy from regular-file policy; keep the existing fixed framework-supplied path and creation method.

1. Always require lstat expected type (no leaf symlink), st_uid==Process.myUid(), and no world write `(mode & 0002)==0`.
2. Regular file: retain current no-group-write check `(mode & 0020)==0` without exception. Do not change existing read-bit policy, permissions or contents.
3. Directory: group-write is allowed **only** when st_gid==Process.myUid() AND Os.getgid()==Process.myUid() AND Os.getegid()==Process.myUid(); otherwise reject group-write. Require effective UID equal to Process.myUid() when granting this exception. Non-group-writable directories retain existing acceptance rules. These equalities identify the normal current app-primary group; merely belonging to a supplemental group is insufficient.
4. Preserve canonical parent selection/rechecks, file device/inode identity rechecks, sidecar retention, exclusively-new creator permission, and all read-only-existing/schema/UTF8/cursor/deadline guards. Unknown GID/type/ownership fails retained; no chmod/chown/umask change, existing-file repair, directory recreation, weaker path selection or retry.
5. New calls `Os.getgid/getegid/geteuid` are public API21, available at min26. [Os API](https://developer.android.com/reference/android/system/Os#getgid()). No O_CLOEXEC reference or numeric workaround. A small pure `CatalogOwnership` permission predicate can receive observed UID/GID/process IDs plus role/mode; lstat/type validation remains in Reader. This makes the actual decision directly testable without pretending host stat tests execute Android.

The exception trusts the framework-assigned current-app sandbox and the existing no-hostile-sameUID assumption. It is not attestation of SELinux, ACLs, hostile guest isolation or arbitrary filesystem safety. This app declares no sharedUserId; other trusted sameUID code still lies inside the boundary. No provider/guest activation follows.

## Creation choice and stop gates

Do **not** switch to a new low-level creator merely to fix this directory defect. Keep File.createNewFile plus immediate strict regular-file stat validation. If an OEM/process mask creates a group/world-writable file, fail and retain it; never chmod an uncertain existing file. A separately audited explicit0600 exclusive creator would be a later proposal only if actual source/runtime evidence establishes necessity. No new Os.open/descriptor lifetime/API flag scope is included here.

No runtime success is claimed until the real framework path is exercised in an authorized environment. The optional hosted emulator inventory remains unknown/deferred; no tool installation, SDK license action, AVD creation/boot or device install is part of this correction.

## Required tests and concrete files after approval

- `.../catalog/CatalogOwnership.java` (small dependency-free permission predicate) and `CatalogReader.stat` adapter: synthetic truth tables covering directory0771 with own group accepted,0700/0750 baseline acceptance,0771 foreign/cache/supplemental/mismatched real/effective IDs rejected, world-write rejected regardless of group, regular0660/0666 rejected,0600/0644 unchanged, wrong UID rejected; type/symlink check remains mandatory in adapter.
- Direct Java17 tests integrated into check-model.sh; update exact packaged source allowlists only for this reviewed helper. Source-order/guard regression verifies Reader actually uses predicate and retains lstat/type/canonical/inode checks, no chmod/chown or low-level creator introduced.
- Existing source/SQL/decoder/loader/presentation/kernel/storage/history suites, independent review and new-head JDK17/Android compile/lint/package gates. No changes to SQL schema/data identity/budgets or FULL readback.
- Host truth-table PASS is not Android no-backup mode/runtime acceptance. Unsupported OEM ownership remains an explicit failed source, never silent fallback.

Decision requested: approve only this directory-specific private-primary-group exception and its direct tests, with creator/file checks unchanged.

## Root approval — 2026-10-30 23:54 UTC

Root approved the exact directory-only private-primary-group exception and directly tested CatalogOwnership predicate. Regular-file0022guard, exclusive File.createNewFile, UID/type/worldwrite/canonical/inode/sidecar checks and FULL readback remain unchanged. No permission mutation, low-level creator, O_CLOEXEC or budget change. FULL remotefffced9/run41146808770 is SUCCESS per root. Implement then independent review/full local regressions and root-published JDK17/Android CI; actual Android/OEM runtime remains NOTRUN.
