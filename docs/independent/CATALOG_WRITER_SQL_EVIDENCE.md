# Native catalog writer SQL evidence — no active Android writer

Four new SQL sources define the referenced inert v1 schema, typed UUID/revision
claim, exact existing-key style update and readback of known bounded validated inputs (not a standalone transfer bound). New owned fixture
`tools/test_catalog_writer_sql.py` executes these actual statements against native
SQLite temporary files. It is not an in-memory substitute database, Android adapter
or production access broker; fixed valid style fixtures do not authorize app writes.

Local **15 test cases PASS**, Python/nativeSQLite3.53.1. Verified exact row/sibling/
nonstyle preservation including malformed original style and sibling bytes, reopen,
stale UUID/revision, missing/wrong namespace/no upsert, participating delete/reinsert
ABA, max revision/no wrap, metadata storage classes/singleton, unknownschema/version, TEMP/ATTACHed database refusal,
UTF16 refusal, exact SQLITE_BUSY, pre-entry cancellation/rollback, readback mismatch,
post-entry failure with truthful INDETERMINATE, failed rollback and exclusive new-file
bootstrap refusal. Post-entry uncertainty closes the fixture-owned connection and
requires fresh readback; it never promises rollback or replays old intent.

The ABA test explicitly bumps revision in both owned delete/reinsert transactions.
It proves the protocol under participating writers, NOT enforcement by unimplemented
app-wide writers. SQL primitives alone cannot provide complete type/schema validation,
StyleValue validation, input encoding provenance or authentication. The test owner
checks schema/metadata and only uses known owned valid keys/style; actual Android
composition with reviewed CatalogKey/StyleValue remains unimplemented.

Production writer, user database open/migration/restore, all-writer inventory binding,
Android auth/foreground/cancel coordinator and process-death/commit uncertainty proof
remain NOT_RUN. No legacy Room version/hash changed; no new permission/UI/provider
or V2 activation. Source adoption is engineering design only. No acceptance counters
changed. No previous unchanged Java/APK suite was rerun for this SQL slice.
