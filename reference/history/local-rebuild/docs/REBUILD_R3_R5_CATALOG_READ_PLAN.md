# R3/R5 next product slice: owned empty catalog → bounded native metadata pages

2026-10-30 23:12 UTC. **Proposal only. No adapter, source creation or UI wiring implemented. Root review required.** Prerequisite composite-key checkpoint c54191f/tree1584ef6 published a73a8e6; exact-head CI is being monitored by parent. B3c actualJDK17/fullCI is green but supplies no database ownership or provider authority.

## 1. Product result and honest source of truth

Implement the existing Activity's first real metadata read path, not simulated rows. On first permitted foreground load, create a fresh **empty** manager-owned catalog and show LOADING→LOADED-empty. No legitimate populated source currently exists in the fresh debug application sandbox. Never seed examples, fabricate imported instances, scan original app accounts or pretend to recover another application's private database. An empty fresh catalog is not evidence that no old clones exist.

The new catalog is authoritative only for this manager installation's metadata. No writer for clone rows is enabled in this slice. A later separately reviewed migration/configuration/verified-instance flow may populate it; that future flow must preserve original rows/ownership/receipts and never interpret a catalog row as a live clone. This is useful integrated infrastructure, not completed migration or useful clone execution.

## 2. Ownership, creation and failure policy

- Use only trusted application Context, reject device-protected context, and derive one fixed filename under credential-protected `getNoBackupFilesDir()`. No Intent/path/URI/string supplied by a view, configuration or guest chooses the root or file. Retain manifest backup exclusions and no requested permissions.
- Root/file checks reject unexpected links/nonregular entries/path redirection. All catalog work is serialized on one manager-owned background worker. This is a trusted app-UID boundary; a hostile guest sharing that UID would invalidate it, so no guest runtime is introduced.
- Distinguish absent from existing. Only a newly exclusively created owned file may be initialized: the create operation must return proof that this invocation created it, not merely pass an earlier exists check. Create schema+singleton metadata in one transaction. Existing zero-byte/partial/unknown-version/wrong-schema/corrupt files fail visibly and remain retained; never treat them as new empty storage, downgrade, drop tables, delete, or destructive-recreate.
- Explicit non-deleting DatabaseErrorHandler is mandatory. Android's default handler can delete corrupted database files. Preserve diagnostics as generic failure categories, not paths/metadata dumps. No repair/export action in this slice.
- Bootstrap failure/uncertain completion stops loading. Reopen/schema validation is diagnosis; no clone operations exist. Existing-file reads must not invoke schema upgrade or repair callbacks. SQLite's own recovery behavior and exact owned-file implementation require code review and later Android runtime evidence; do not promise byte-for-byte immutability of a live SQLite/WAL file merely from a Java guard.

## 3. Exact fresh schema proposal (version1, not legacy Room version1)

Use a separately named `manager-catalog-v1.sqlite`, SQLite `user_version=1`, encodingUTF8, and fixed `application_id=0x46434d31` (decimal1178815793, ASCII FCM1). Expected user tables only:

```sql
CREATE TABLE catalog_meta (
 id INTEGER PRIMARY KEY CHECK(id=1),
 catalog_id TEXT NOT NULL,
 revision INTEGER NOT NULL CHECK(revision>=0)
);
CREATE TABLE manager_catalog (
 engine_key TEXT NOT NULL,
 clone_id TEXT NOT NULL,
 package_name TEXT NOT NULL,
 label TEXT NOT NULL,
 group_name TEXT,
 legacy_launch_token TEXT,
 accent_key TEXT,
 badge_text TEXT,
 cluster_id TEXT,
 readiness TEXT NOT NULL CHECK(readiness='UNVERIFIED'),
 launch_allowed INTEGER NOT NULL CHECK(launch_allowed=0),
 PRIMARY KEY(engine_key,clone_id)
);
```

`catalog_id` is a freshly generated installation-catalog identifier, not a clone/provider identity or authentication secret; initial revision0, zero instance rows. Exact schema/table/index/default/constraint metadata and singleton shape are validated, with no unexpected view/trigger/attached/temp schema. Future writes must change revision atomically with data; overflow refuses mutation, no wraparound. This is a fresh schema, **not a drop-in migration of the Python reference target**, which also preserves complete rows/tasks/ownership/receipt. Never discard those reference obligations to fit this smaller live reader.

All catalog rows stay UNVERIFIED and launch_allowed0, regardless of engine spelling. Unknown engines are not mapped to a supported provider. Invalid readiness/flag/schema blocks the page rather than enabling or silently hiding records. The reader does not fetch legacy launch tokens/account content. Nullable group is projected to the UI's existing ungrouped display (`""`) without changing stored NULL; root approval of this display-only mapping is requested. Existing unsupported empty package/label display shapes cause explicit load failure with retained rows, not replacement identity or fake label.

## 4. Empty opaque IDs: resolve the reference inconsistency separately

Evidence: all six pinned Room schemas allow non-null TEXT engineKey/cloneId with no nonempty/length/UUID CHECK; R5.3 now represents exact strings. `rebuild-storage/storage_contract.py` currently rejects empty engineKey/cloneId alongside package/label before projection.

Proposed smallest reference correction: for engineKey/cloneId require String type only; preserve empty/whitespace/opaque text exactly. Leave packageName/label projection checks unchanged. Preserve original canonical rows/source bytes, composite uniqueness, quarantine flags and transaction rollback. Add synthetic schema1–6 tests for empty component(s), same clone across engines, unchanged source fingerprint and zero launch/dispatch permissions. Do not modify historical schema JSON, remap IDs or call this real migration. Commit/review this correction separately before live-reader work.

## 5. Bounded page and snapshot contract

Proposed resource budgets for approval, not historical validity rules:
- 128 displayed rows/page; SQL lookahead at most129 rows; one page retained, no growing accumulated list/history stack.
- 256KiB total accepted UTF8 display/key payload per page; at most16KiB UTF8 and4096UTF16 per fetched text field; 8192UTF16 maximum cached normalized field. Bound before normalization/copy where possible and verify after normalization. These are transport/render budgets, not database constraints or a product clone cap.
- One background request in flight; replacement/pause cancels and supersedes it. Two-second cooperative load deadline checked around row work and wired to CancellationSignal; cancellation does not guarantee hard interruption of all OS/open/SQLite work. No worker result publishes after timeout/pause, even if underlying work finishes late.

Read via fixed parameterized SELECT over indexed rowid order, never arbitrary SQL supplied by UI. Rowid is an internal opaque pagination position only; CatalogKey remains true row identity. Cursor binds catalog_id + revision + last accepted rowid + load owner/generation. Every page is a single SQLite statement snapshot joining the singleton metadata to the limited row window (including an empty-window metadata sentinel), so rows and revision come from the same statement snapshot. A following page must match catalog_id/revision; otherwise discard and request restart, not splice revisions. No long-lived transaction while waiting for a user's next-page click. Future writers must uphold atomic monotonic revision; this is a trusted-writer rule, not tamper authentication.

SQL projects separate per-field byte-length/type/NULL/overbudget flags and only bounded BLOB byte values for TEXT fields (CASE guard before CursorWindow transfer). Java uses Cursor.getBlob and a fresh UTF8 CharsetDecoder configured REPORT for malformed/unmappable input, never Cursor.getString replacement for identity/display fields. The4096UTF16 cap is checked after strict decode. It does not silently substitute NULL as a valid identity. A field over budget, unsupported type/encoding or invalid display shape produces explicit page failure and retained source. If another otherwise valid row would exceed the aggregate page budget, finish the already bounded page with hasMore and leave that row for the next cursor—never skip it. If even the first row cannot fit, fail, not an infinite empty page. Java-side checks still enforce measured lengths. Exact SQL/API26 SQLite compatibility and memory behavior must be reviewed before implementation; document CursorWindow/native overhead separately from the256KiB logical payload budget.

No global count/search claim: current filters and selection apply to the loaded page only. Cache normalized search strings once per accepted row; bound query/group text as a UI resource policy separately from stored key validity. New First/Next navigation clears selections and carries current foreground/view tokens. Unknown/failed/over-budget results never become empty-success. Explain more-available/source-changed/error states in native text.

## 6. Concrete implementation locations and lifecycle

- New `rebuild-android/app/src/main/java/vn/fonesi/rebuild/catalog/OwnedCatalog.java`: fixed-context ownership/bootstrap/schema/read-only page access, nondeleting error handler, no row mutation/provider API.
- New small `.../catalog/CatalogPage.java` and `CatalogLoader.java`: immutable page/cursor/budget result and one-worker cancel/dispatch boundary, using platform/JDK APIs only. These directly feed the existing UI, not a detached runnable module.
- Existing ManagerModel/ManagerController: page metadata, cached search and First/Next guards; preserve composite identity, owner/epoch/revision/reentrancy rules. Do not make selection into authorization.
- Existing ManagerActivity: initiate load after attach; onPause cancel first and detach/invalidate before any late delivery; on main thread accept only the captured current RefreshTicket. Close resources on worker, never UI-thread DB operations. Loading an empty real catalog differs from previously unconnected IDLE.
- Existing native layout/strings: explicit loaded-page navigation/source/error wording. If adding two navigation Buttons, update packaged semantic verifier to an exact ID allowlist: original four import/create/launch/migration buttons MUST remain disabled; only First/Next may enable under page-token conditions. Do not weaken a blanket button check without preserving these operation-specific assertions.
- `rebuild-storage/storage_contract.py` and existing tests: separate empty-ID reference correction; new synthetic SQLite tests share pinned exact fresh DDL/query fixtures with the Android adapter, rather than a different invented schema.
- Existing contract-tests/check-model.sh and tooling: test page/cancel/revision/ownership outcomes with fake executor/view; hosted actualJDK17 plus Android D8/lint/packaged gates. No new third-party dependency.

## 7. Evidence and stop gates

Before code root must approve: (a) empty-ID reference correction, (b) fresh empty source/owned bootstrap, (c) exact schema and nullable-group display projection, (d) explicit provisional budgets and pagination UI, (e) no device-runtime acceptance from compile-only evidence. This is more than the prior identity-only slice and needs its own approval.

Required tests: bootstrap/reopen/unknown schema refusal without destructive handling; exact composite/empty IDs; query length/type/row/page boundaries; consistent snapshot and revision-change refusal; cancelled/interrupted/repeated/late work, lifecycle detachment and cross-loader tickets; no partial/empty-success on malformed pages; source retention/quarantine flags; original4operations disabled. Synthetic SQLite can prove SQL/transaction recipes; fake-view tests prove coordination. **Android SQLite execution, CursorWindow behavior, cancellation under real disk pressure, device lifecycle/TalkBack and OEM backup behavior remain NOTRUN without a separately authorized runtime environment.** No device/emulator install, export, provider operation or migration deployment is authorized by this proposal. Stop on unsupported API, unknown ownership/schema, missing runtime prerequisite or unexplained fixture drift; no denied experiment retry.

Primary evidence: pinned E82 schema manifest; current R3 DDL/projection; current ManagerController RefreshTicket identity+epoch guards; existing no-backup manifest; [Context storage APIs](https://developer.android.com/reference/android/content/Context), [SQLiteDatabase query/cancellation/error-handler APIs](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase), [CancellationSignal](https://developer.android.com/reference/android/os/CancellationSignal), [default corruption handler](https://developer.android.com/reference/android/database/DefaultDatabaseErrorHandler). Current API references were read for this proposal; exact implementation-method audit is still required before acceptance.

## Root decision — 2026-10-30 23:15 UTC, before correction

**Approved for implementation now: only the separate empty-ID reference correction**, with complete original rows, source retention, quarantine flags and all existing checks preserved. Review/test/commit and root-published CI precede any live reader. No Android reader implementation is authorized yet.

Approved later-reader design refinements: fixed application_id `0x46434d31` (FCM1); exact schema above; bootstrap only a newly exclusively created owned file, retaining uncertain/partial existing files with no delete-on-retry. Query TEXT fields as bounded SQL-projected BLOB bytes with separate type/NULL/overbudget flags; strict UTF8 decoder REPORT, never Cursor.getString replacement. Check4096UTF16 after strict decode; preserve exact empty/whitespace identity bytes/codepoints, no key normalization. GroupNULL→empty display mapping and128row/256KiBpage/16KiBfield budgets are approved only as read/render budgets, not schema validity. No device/runtime/provider/migration deployment. A revised exact plan is required before live-reader code; wait for root review after this reference checkpoint.

### Separate reference-correction acceptance

Change only engineKey/cloneId projection validation from nonemptyString to String; packageName/label keep existing nonemptyString validation. Test schema1–6 with empty engine, empty clone, both empty, exact whitespace/opaque strings and same clone in distinct namespaces. Verify full preserved canonical row equality, unchanged source bytes/fingerprint, retained task/owner rows, all launch/dispatch/release flags0 and pausedUNVERIFIED receipt. Non-TEXT IDs still reject and transaction rolls back; empty package/label still reject. No historical schema/DDL alteration, database path/source policy or resource limit change. These are synthetic SQLite tests, not installed-data migration.

## Exact later-reader refinements for review (still no reader code)

### Schema/bootstrap identity

The two CREATE statements above are the complete user DDL; no extra column/default/collation/trigger/view is permitted. Pin their literal SQL plus inspected table/index/PK structure in a shared fixture before implementation. `PRAGMA application_id=1178815793`, `user_version=1`, encodingUTF8; NO_LOCALIZED_COLLATORS prevents platform locale-table creation. `catalog_meta` must contain exactly(id1, canonical newly generated catalogUUID, nonnegative integer revision); validate actual SQLite storage types, not only declared affinity. A future writer cannot add a row without transactionally incrementing revision; no writer is enabled here.

Creation sequence: obtain trusted credential-protected no-backup parent; verify canonical regular ownership boundary; exclusively create fixed new file; only its successful creator may run initialization. If exclusive creation says existing, use validation-only path. Never initialize/truncate an existing empty file. Transaction/create/readback failure leaves retained uncertain/partial file and visible failure. A retry cannot delete/recreate it. No legacy Room file, arbitrary path, URI, token or guest-chosen root is accepted. Own-UID trust and Android filesystem behavior remain explicit runtime prerequisites, not a hostile-writer certificate.

### Lossless TEXT-to-Java boundary

For each of engine_key, clone_id, package_name, label and group_name project separately:
- storage type flag (TEXT / NULL / other), byte length and overbudget flag;
- `CASE WHEN typeof(field)='text' AND length(CAST(field AS BLOB))<=16384 THEN CAST(field AS BLOB) ELSE NULL END` as bytes.

Column names are fixed constants, never UI input. A NULL result is never sufficient to classify a field: inspect the independent flags first. Only group_name permits storedNULL; its approved display-only projection is emptyString, with the stored row untouched. EmptyTEXT has zero-byte BLOB, not NULL, and remains exact emptyString. NonTEXT, invalidUTF8, overbudget or unsupported display shapes cause explicit page failure with source retained. Strict decoder REPORT is required before CatalogKey construction; no replacement character, trim, normalization, UUID conversion or case fold. Preserve codepoints and exact UTF8 by round-trip equality for accepted values. Any malformed source bytes remain unavailable/quarantined, never rewritten. This is distinct from the existing Python reference's strict UTF8/canonical snapshot behavior; no real migration is invoked.

Read budget accounting: byte bounds enforced by SQL before CursorWindow transfer; after strict decode check each field<=4096UTF16, then normalized display-only cache<=8192UTF16. Count accepted rawUTF8 payload plus2×cached-normalizedUTF16 units against the256KiB page budget before publication; document additional bounded decoded-string/object/CursorWindow/native overhead rather than claiming a hard256KiB process heap limit. Query/group UI text gets separate length/work guards without altering stored identity validity. No NFD or accent-fold operation is applied to keys.

The page SELECT must keep meta row and limited metadata rows in the same statement snapshot, bind cursor revision/catalog ID, preserve an empty-window sentinel, and expose at most129 rows (128 accepted plus lookahead). Draft SQL must be reviewed/tested on SQLite syntax available at API26—no window functions or version assumptions. Overbudget next row cannot be skipped; use the last accepted rowid cursor. If first row fails, the page fails. Deadline/cancellation cannot promise to interrupt every native open/lock syscall; one worker and stale-ticket invalidation bound publication, with explicit timeout failure and no queued unlimited tasks.

### Sequencing remains mandatory

1. Empty-ID Python reference correction only → independent review → separate root publication/fullCI.
2. Root reviews this revised exact live-reader plan and actual method/API audit before Android source changes.
3. Approved implementation then source/SQL/fake-lifecycle tests and compile/lint/APK gates. Actual Android database/native UI execution still NOTRUN without separate runtime authorization; no device installation/export/provider/migration deployment.

### Reference checkpoint evidence

Permitted empty-ID correction implemented only in the Python synthetic projection, with5new unittest methods/108subcases across allsix schemas;56total methods pass. Complete source rows/bytes/fingerprint and inert catalog/task/owner flags preserved, BLOBidentity and unchanged emptydisplay rejects roll back, injected after_catalog failure proves emptyIDs reached projection before rollback. Independent read-only review passes. This does not implement any later-reader design above; wait for root-published reference CI and renewed reader review.

## Final API/query audit refinement — 2026-10-30 23:28 UTC

See REBUILD_CATALOG_API26_RECIPE_AUDIT.md and fully expanded REBUILD_CATALOG_QUERY_RECIPE.md. These refine the earlier single-statement snapshot wording: Android CursorWindow can refill/re-execute, so require every row's same catalog_id/revision, close page cursor, independently reread metadata, and compare again with cancellation/deadline/current owner-ticket before publish. Unknown outcomes fail, never partial success. Future trusted writers must atomically increment nonwrapping revision; not a hostile-file authentication claim.

BaselineAPI26 AOSP SQLite3.18.2 supports index_xinfo (introduced3.8.9); table_xinfo/table_list/sqlite_schema/window functions excluded. Expected PRAGMA result shape must be present or source fails; no weaker fallback. Initial pagination explicitly includes Long.MIN_VALUE and all negative/zero rowids; continuation usesstrict>lastposition, no+1 or implicit0cursor. Existing SQLite open is READONLY; throwing nondeleting corruption handler prevents baseline internal retry. Exclusive creator alone initializes absent file; all uncertain existing files retained. ReferenceCI41143832070 is green; live-reader implementation still awaits root review of this final audit.

## Native implementation authorization — 2026-10-30 23:31 UTC

Root reviewed a87d0c5 and approved the coherent native reader path: exclusive fresh empty bootstrap, strict schema/read-only retained-failure reader, bounded strict UTF8 pages and cancellation, existing foreground controller/Activity First/Next wiring. Validate ALL metadata rows as exactly one id=1, not merely the filtered page. Preserve four disabled operation controls. Implement and independently review before root publication; actual JDK17 and Android compile/lint/package CI required. Android SQLite/CursorWindow/OEM/lifecycle execution remains NOTRUN. No seeded rows, legacy database access, provider activation, device installation, source deletion or denied experiment.

## Implemented scope and proof boundary — 2026-10-30 23:41 UTC

Native source now implements the approved empty-owned-catalog path. `CatalogReader/CatalogSql/CatalogPage` own bounded schema/query/decoding; `CatalogLoader/CatalogRequestGate` own one-worker delivery; existing Activity/controller/model own current-page native controls and cached display filtering. Fixed2-second deadline and rawUTF8+2×normalizedUTF16 accounting are enforced, not changed to broader budgets. UI query/group4096UTF16 limits and normalized-query8192 are rendering limits only. All First/Next listeners capture render tokens, and LOADING navigation is rejected explicitly.

Host tests execute actual loader source on a separate fake-Android classpath, not packaged stubs. Synthetic SQLite executes exact source literals, not a divergent schema. These close host coordination/recipe tests only. Android database/CursorWindow/filesystem/UI execution remains NOTRUN, and exact-head JDK17/Android compile/lint/package CI is still required after root publication. Read REBUILD_STATUS.md for exact counts, corrections and remaining acceptance boundaries.

## Approved initialization readback correction — 2026-10-30 23:47 UTC

Root verified remote38f589e/run41146244852 SUCCESS including actualJDK17 and Android compile/package. Read-only review found creator initialization set synchronousFULL but did not read its effective value. Root approves requiring the existing strict one-row INTEGER query helper to return2 from `PRAGMA main.synchronous`, immediately after settingFULL and before beginTransaction, on the same connection. Unknown/missing/wrong setting fails retained before DDL. Add source-order regression and synthetic exact SQL readback proof, independent review and regressions. Encoding remains checked on read-only reopen. This is configuration verification, not fsync/power-loss/Android runtime acceptance. [SQLite PRAGMA documentation](https://www.sqlite.org/pragma.html#pragma_synchronous).
