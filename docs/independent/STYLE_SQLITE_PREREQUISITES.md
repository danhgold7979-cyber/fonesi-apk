# Original-scope SQLite prerequisite decision — before adapter code

Scope: SUP-S04/S05/S08 and STYLE-R1-01. No provider/profile architecture choice,
production database opening, UI integration or task acceptance.

## Frozen schema facts
The immutable E82 `reference/history/e82/app/schemas/vn.fonesi.app.storage.FonesiDatabase/{3,4}.json`
shows clone_metadata composite primary key `(engineKey,cloneId)` with no trim,
UUID grammar, length constraint or custom collation. Version4 adds exactly
`accentKey TEXT NOT NULL DEFAULT 'teal'` and nullable `badgeText TEXT`. Tasks,
queue pause, engine reservations, launch tokens and all existing fields survive.
The v3/v4 `changedAt` INTEGER is NOT an incarnation or monotonic revision contract.
There is no style/row revision. A delete/reinsert with identical fields can fool
row-content/changedAt compare-and-swap. No existing database is present in the
fresh active app; the app still has no catalog/storage adapter.

## Repair adapter is not ready to implement honestly
Before an Android repair adapter, review a manager-owned schema/incarnation and
writer contract or a proved equivalent stale-event strategy. All relevant writers,
restores, deletion/recreation and lifecycle invalidation must participate. Do not
silently add a revision to historical v4 or reinterpret changedAt as revision.
Java opaque key preservation is not proof of lossless arbitrary SQLite TEXT decode.
Invalid storage types/UTF-8 must be retained, not replacement-character decoded.

SQLite PRAGMA data_version is not a drop-in row revision: it only compares reads
on the same connection and omits that connection's own commits. Android connection
pool affinity is not established here. Therefore no host experiment using it may
be called the Android stale-row solution. See SQLite official documentation:
https://sqlite.org/pragma.html#pragma_data_version and https://sqlite.org/isolation.html.

## Smallest legitimate independent prerequisite slice
Implement two newly authored SQL migration statements for the required v3→v4
column delta, plus a HOST-native SQLite conformance test using owned temporary
fixtures derived from immutable schema data (not archived implementation/tests).
This is a reusable migration SQL fragment and real SQLite proof of that fragment,
NOT an activated Android migration manager or a repair adapter.

The fragment has no BEGIN/COMMIT/version/hash writes. A future reviewed owner must
take transaction/write-reservation ownership BEFORE the authoritative exact source
schema/version check (or repeat the check under that same transaction), run both
statements, verify target schema/preservation, and only then advance metadata.
Never fabricate a Room identity hash. The fixture harness may create the historical
Room metadata exactly from immutable schema data, but does NOT mutate its identity
hash or claim Room migration acceptance. This partial proof cannot open a legacy
Room DB as accepted v4 after changing only user_version.

Required focused tests: both added columns/defaults and target structural schema;
exact opaque keys including empty/space/case/Unicode; all nonstyle fields/tokens,
queue/tasks/reservations/sequence preserved; close/reopen; unknown/malformed schema
refused without modifications; injected interruption between ALTERs rolls back;
competing writer produces busy with no partial mutation; malformed saved bytes and
storage classes retained. Only temporary owned files, no user/private DB inputs.

HOST SQLite version must be recorded. Android API26/OEM SQLite compatibility,
actual Android transaction cancellation/process death, auth and Room migration
metadata remain NOT_RUN. Reuse this evidence without rerunning unrelated APK/style
checks. Full SUP-S04/S08 and T13 remain open.

## Exact fixture guards
Wrong/partial/already-v4 schemas and any unrecognized objects/indexes/triggers must
fail unchanged. Source-check→ALTER races are tested by a competing connection;
the guard and both statements are in the same write transaction. No fixture code
advances user_version or Room hash: structural v4 conformance is partial evidence.
Raw preservation compares each value's SQLite storage class plus raw bytes
(`typeof` and `hex(CAST(value AS BLOB))`), with NULL distinguished. Do not compare
lossy decoded strings or demand whole-database byte identity after a valid ALTER.
Include invalid UTF-8 TEXT, BLOB in TEXT-affinity fields, embedded NUL and exact
opaque keys; new columns must not rewrite retained values. Existing index and
AUTOINCREMENT sequence semantics are explicitly checked, not merely column names.
