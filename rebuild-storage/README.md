# SQLite preservation/quarantine reference contract

Run `python3 rebuild-storage/check.py`. Standard-library Python/SQLite only; no downloads or device access.

Historical Room schema JSON1–6 is retained as a hash-pinned **schema oracle**, not copied implementation. Fixtures are created under temporary directories, populated with synthetic metadata and read through SQLite mode=ro/query_only. The fresh separate target is transactional and inert: all catalog entries UNVERIFIED, task dispatch disabled, all cluster ownership retained/release disabled. Original queue payloads, pending states, routes, foreign engine identities and typed rows remain preserved as history; no guest/session data or provider actions exist here.

This is a reference implementation tested with desktop SQLite, **not an Android Room migration**, live-data utility or replacement APK. No CLI accepts user databases; tests own their fixtures. Native Android schema identity/migration registration, SQLite versions/OEM behavior, source/WAL lifecycle, durable installation and real upgrades remain separate gates. Room legacy tables are not modified, no destructive fallback is added, and unknown schemas fail closed.

The bounded 16MiB snapshot budget is a test-contract resource limit, not a product clone count. Future native streaming/indexed migration must preserve all rows and unresolved ownership without silently dropping large histories. The source snapshot fingerprint is an integrity identifier, not a cryptographic trust certificate or proof of guest compatibility.

Source checks validate known column/key/default/index order/collation/partial flags, foreign-key/table options and auxiliary-table structure; this is not a general SQL-equivalence parser or hostile-database authenticity certificate. SQLite cell and cumulative snapshot limits bound this reference harness. The target rejects TEMP shadow objects, attachments, unsafe journaling/synchronization and disabled CHECK constraints; explicit SQL COMMIT/ROLLBACK works under tested Python autocommit mode.

`TargetUncertain` preserves the original error when post-commit state or rollback cannot be confirmed. Reopen/reconcile the receipt before any reuse; a valid receipt still describes **quarantined, non-executable history**, never provider authorization. Current local evidence: **51 unittest cases**, Python3.12.14/SQLite3.53.1; no skipped tests. Android SQLite/Room/WAL lifecycle and actual migration remain unverified.
