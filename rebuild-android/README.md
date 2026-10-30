# Fresh Android catalog manager

Independent native Java17 presentation project, not an engine or imported old app.
Distinct applicationId `vn.fonesi.rebuild.debug`; all unsupported actions disabled.
No requested permissions, legacy storage migration, provider, Java21 kernel import or legacy user-data access.

Only the inventory-gated hosted CI experiment is authorized for build. It requires
preinstalled API36/build-tools36.0.0/JDK17; no SDK installation or license action.
The root verified Gradle8.13 wrapper is reused with `-p rebuild-android`; root
historical Gradle settings are not evaluated. Debug APK is CI review only, not release.
Source tests and APK inspection do not substitute for device/UI/isolation acceptance.

## Metadata presentation model (R5.1)

`ManagerModel` is new dependency-free Java17-compatible source compiled inside the
Android project. It models immutable metadata, refresh ordering, accent-insensitive
search and visible-only selection with stale revision rejection. All entries remain
unverified/quarantined/unavailable; selection never enables operations. Activity
now loads only the fresh application-owned catalog described below. No fake sample rows,
legacy source reads or provider calls are connected.

`./rebuild-android/check-model.sh` runs direct-source contract tests. Installed
JDK17 CI uses `--release 17`; the limited local JDK21 fallback establishes behavior
only, and prints that boundary. Android compile/lint additionally verifies source
and API compatibility. UI event wiring, persistence, rotation, accessibility,
authentication, shortcuts and device execution remain separate unaccepted work.

## Native presentation wiring (R5.2)

Foreground-bound `ManagerController` drives native search, exact-group and
visible-selection controls. Epoch/revision tokens reject stale UI events; pause
invalidates pending refresh tickets, clears selection and renders hidden metadata.
Controller and Activity both guard render-triggered callbacks. The reviewed reader
now connects only the new owned metadata format, with no sample rows.
Original import/clone/launch/migration buttons remain disabled.

Fake-view lifecycle tests are not Android UI/device tests. The connected reader bounds one page before presentation; current filtering and
selection apply only to that page, not the whole catalog. General large-list
virtualization and legacy migration remain separate work.

Manager row identity is the exact opaque `(engineKey, cloneId)` pair, matching the retained schema key rather than assuming globally unique UUIDs. It is display-only and carries no executable provider authority. The bounded reader preserves exact opaque keys, while invalid or over-budget pages fail retained without partial publication. Empty/whitespace opaque keys are preserved according to the schema, not normalized into a different identity.


## Owned catalog native path (R3/R5)

The Activity requests First on foreground resume and exposes First/Next metadata-only
buttons. A singleton application-context worker exclusively creates an empty
`manager-catalog-v1.sqlite` under no-backup storage, with application_id FCM1 and the
exact fresh schema. Existing files are read-only and never upgraded, repaired,
deleted or silently initialized. The new file contains only its catalog singleton,
not seeded clones. An empty result is not a scan of existing apps or clones.

Strict bounded BLOB→UTF8 REPORT decoding preserves opaque keys. One page is limited
to 128 rows / 256 KiB raw UTF8 plus twice cached normalized UTF16 units, with 16 KiB and 4096 UTF16 units per field and a
separate 8192 normalized-display budget. These are rendering budgets, not schema
validity restrictions. Invalid/type/NULL/over-budget source fails the entire page;
no row is silently dropped. Group NULL alone becomes an empty display string.
Signed rowid continuation is bound to catalog ID/revision; every page row and a
fresh metadata read after cursor closure must agree. This assumes a future trusted
writer atomically increments revision, not hostile-file tamper resistance.

Cancellation, a 2-second delivery deadline, owner identity and controller tickets
prevent obsolete publication. Native open/locking/cancellation latency is not a
hard real-time guarantee. All four import/clone/launch/migrate actions stay disabled.
No writer, provider, legacy import or kernel operation execution is enabled.

Tests execute exact SQL on synthetic SQLite and pure Java ownership/decoding/value
contracts. **Android SQLite, CursorWindow, OEM filesystem and Activity lifecycle
runtime acceptance remain NOTRUN.** Hosted compile/lint/package evidence is required
for this new checkpoint and is not device execution. See
`docs/REBUILD_R3_R5_CATALOG_READ_PLAN.md` and the API/query audit documents.

The no-backup directory guard recognizes framework0771 only when its writable
group is the verified current-app primary group. World write and foreign writable
groups remain rejected; regular files retain the stricter no-group/world-write
rule. No directory/file permission is modified. Direct predicate tests do not
substitute for actual Android lstat/OEM filesystem validation.
