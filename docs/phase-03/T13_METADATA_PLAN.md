# Phase 3 — APK-P3-T13 metadata and migration plan

Date: 2026-11-03  
Branch: phase-03  
Base: phase-01@41f1709330fbb968946834f2fd4616825462461e  
Product roadmap: Phase 3, step 13 of 32  
Status: PLAN ONLY — no implementation or acceptance is claimed by this document.

## 1. Why this task can proceed now

The current Phase 1 provider search has no eligible provider. T06, T07 and T08 remain blocked; no production clone engine, guest runtime or account isolation is accepted. The master plan explicitly permits cloud-independent work while provider/phone gates remain open, provided dependent operations stay disabled.

T13 is independent at the metadata-contract and HOST-fixture level. Its Android SQLite/file-lifecycle acceptance still requires a later ANDROID receipt. This plan does not bypass the provider gate, enable guest operations or claim a working clone.

## 2. Authoritative requirements and dependencies

Original task: APK-P3-T13, Phase 3, step 13.

Primary requirements:
- P055, P056
- RM-13a, RM-13b, RM-13x
- SUP-S04, SUP-S08
- SEC-021, REC-009, REC-013

Relevant original contract:
- Stable engine-scoped identity, source package, display name/group, actual version/state and migration from the first metadata version.
- Never persist account passwords.
- Unknown or malformed schema/ownership is retained or quarantined; no destructive fallback.
- Preserve shortcut identifiers, sibling records, queue/pause state and provider namespaces.
- A changed runtime behavior requires its corresponding Android/phone evidence; documentation-only changes do not trigger device acceptance.

Dependencies:
- T01/T02 are accepted.
- T08 provider mapping remains unavailable; any provider-derived actual version/state must remain absent until a verified readback exists.
- HOST work may proceed; ANDROID and PHONE remain NOT_RUN.

## 3. Baseline findings

At phase-01@41f1709:
- The active app is a stateless native shell; guest create/open/stop/delete controls are disabled.
- The active catalog reader/writer is package-private, dormant and has no production opener or authorization broker.
- The current catalog SQL is a separate inert reference catalog, not an accepted production clone metadata store.
- The approved architecture in docs/MASTER_PLAN.md is Java/native Views + SQLite. Historical Room schemas and Kotlin implementation are archive-only references, not active source.
- The existing style validator already accepts exactly six accent keys and bounded plain-text badges; persistence and Android rendering are not accepted.

No historical implementation or old test oracle will be copied into active source.

## 4. Design decisions to validate before implementation

### 4.1 Separate fresh metadata store

Create an independently versioned SQLite metadata contract under storage/metadata/. Do not mutate the existing manager-catalog-v1.sqlite reference catalog, its SQL assets, or its dormant reader/writer.

The first implementation slice is a HOST-testable schema/migration contract. It is not wired into MainActivity, an Android database opener, a clone action or an update worker.

### 4.2 Identity and metadata

The metadata row is keyed by the exact composite (engine_key, clone_id). Neither component may be trimmed, case-folded, normalized, converted to a UUID or used as a filesystem path. Equal clone IDs under different engines must remain distinct.

The v1 record contract covers:
- source package name, display label and nullable group;
- creation/change timestamps;
- a stable shortcut key, treated only as a local reference and never as authentication;
- local reconciliation state, initially fail-closed;
- nullable observed version code/name, observed runtime state and observation timestamp;
- an optimistic metadata revision.

Actual version/state fields are nullable. They may be populated only by a future eligible provider's exact, current, scoped readback. UI values, caller claims, original-app version and synthetic fixture values are not provider readback.

No password, OTP, cookie, session, access token, refresh token or credential column is permitted.

### 4.3 Versioned style migration

Use a fresh SQLite schema version 1 and an explicit SQLite migration 1→2 for the style behavior formerly described as Room schema 3→4:
- add accent_key TEXT NOT NULL DEFAULT 'teal';
- add nullable badge_text;
- constrain the accent to teal, blue, purple, amber, red or slate;
- keep badge validation in the existing strict style policy, not in UI-only code.

This is a deliberate architecture translation to the currently approved SQLite design. It is not a migration of an archived Room database. No legacy Room file is opened, adopted, rewritten or deleted. Any future Room-to-SQLite conversion requires a separately planned, explicitly tested migration and exact source compatibility evidence.

### 4.4 Fail-closed migration

The migration runner must accept only the exact known v1 schema and PRAGMA user_version=1. It must reject unknown versions, unexpected columns/indexes, malformed ownership and duplicate identities before mutation. The v1→2 migration runs in one transaction. The version marker advances only after both style columns and their constraints are present and verified.

If any step fails or is interrupted, roll back the target transaction and preserve source bytes. Never use DROP TABLE, destructive fallback, silent table recreation, original-app fallback or automatic identity remapping.

### 4.5 Namespace and foreign-state preservation

A migration must preserve exact values and SQLite storage classes for every pre-existing clone row, including shortcut keys, IDs, labels, groups, timestamps, observed fields, revisions and reconciliation state. It must not mutate rows belonging to another engine.

The owned test fixture will also include independent sentinel rows for update tasks, pause state, engine instances and cluster/journal references. The migration must leave these tables byte-for-byte/value-for-value unchanged. These sentinels test non-interference; they do not implement or accept queue/journal behavior.

Ambiguous legacy task payloads remain opaque. This slice must not decode them, invent authority or fall back to an original catalog.

## 5. Planned implementation files

- storage/metadata/clone_metadata_v1.sql — exact initial SQLite schema.
- storage/metadata/clone_metadata_1_2.sql — explicit style migration.
- tools/test_metadata_migration.py — independent owned SQLite fixtures, positive/negative/interruption/reopen tests.
- tools/test_metadata_schema_contract.py — static checks tying the SQL, version marker, secret-field exclusions and task contract together.
- tools/run_host_checks.py — register the new host tests in the single aggregate owner.
- tools/compile_scope.py — classify the new SQL and Python-only tests correctly where appropriate.
- docs/phase-03/T13_METADATA_RESULTS.md — write only after exact-source checks finish; separate HOST from ANDROID/PROVIDER/PHONE.
- docs/status.json, docs/tasks.json and docs/TASKS.md — update current task and evidence only from actual results.

No production opener, DAO, UI action, shortcut dispatch, provider adapter or APK behavior is part of this first slice.

## 6. Required HOST test matrix

Positive:
1. Create the exact v1 schema and insert multiple provider namespaces with the same clone_id.
2. Verify composite identity distinguishes engines while preserving case, whitespace, Unicode and embedded NUL exactly.
3. Apply v1→2 and verify existing rows, shortcut keys, timestamps, revisions and nullable values remain unchanged.
4. Verify existing rows receive accent_key=teal and badge_text=NULL.
5. Reopen the database and verify schema version, table shape, values and indexes.
6. Verify update-task, pause, engine-instance and cluster/journal sentinel fingerprints are unchanged.

Negative:
1. Reject unknown user_version values, unexpected columns/indexes and wrong schema shape without modifying the source.
2. Reject duplicate composite identity and duplicate shortcut key.
3. Reject invalid reconciliation state, negative timestamps/revisions and inconsistent observed-version tuples.
4. Assert no credential/password/OTP/session/cookie fields exist in the schema.
5. Assert no migration path reads the original app database or archived Room files.

Interruption/race:
1. Inject failure after the first ALTER TABLE and prove the transaction rolls back both schema and rows.
2. Inject failure before version-marker advancement and prove version remains 1.
3. Re-run after a clean rollback and prove one successful migration, not duplicate columns.
4. Run two migration attempts against the same owned fixture; only one may advance the schema, and the other must observe the new version or fail closed.
5. Close/reopen after rollback and after success; compare exact row fingerprints.

HOST results are synthetic SQLite evidence only. Python SQLite does not substitute for Android framework SQLite, Android file ownership, process death or OEM behavior.

## 7. Acceptance gates and limits

This plan is accepted as the next implementation plan only. It does not mark T13 complete.

- HOST: exact schema/migration fixtures and aggregate checks pass on the exact source SHA.
- COMPILE: run only if changed paths require it under the repository's compile-scope policy; a compile pass is not migration acceptance.
- ANDROID: NOT_RUN until the actual Android SQLite opener, lifecycle, file ownership, reopen and interruption behavior are implemented and exercised on an authorized Android environment.
- PROVIDER: NOT_RUN; no provider is eligible.
- PHONE: NOT_RUN; no device/account action is authorized by this task.

T13 can be accepted only after both HOST schema tests and actual Android SQLite/file-lifecycle evidence meet the task contract. T14/T15/T16 remain separate tasks. No percentage of product functionality may increase from schema-only work.

## 8. Stop conditions

Stop the affected change if any requirement would be lost, any foreign namespace changes, a migration becomes destructive, a version/state is fabricated, a shortcut key is remapped, a secret field appears, source bytes cannot be recovered, or a test cannot distinguish HOST from ANDROID evidence.

Do not weaken assertions to obtain a green CI. Preserve the exact failure and continue only with independent safe work.
