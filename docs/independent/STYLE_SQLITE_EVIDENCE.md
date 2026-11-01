# Native SQLite migration fragment evidence (partial prerequisite)

New source: `storage/sql/style_columns_3_to_4.sql`, exactly two ALTER statements.
New owned fixture harness: `tools/test_style_migration.py`. It uses frozen v3/v4
schema JSON data as oracles; no archived implementation or tests were imported.

Local `python3 tools/test_style_migration.py`: **6 test cases PASS**, using Python's
native SQLite **3.53.1**. These are real temporary-file transactions/reopen/competing
connections and raw-value comparisons, not an in-memory replacement storage engine.
No unrelated style/core/APK suite was rerun for this slice.

Verified: exact added defaults/columns versus v4 structural schema; v3 stored
values/types/raw bytes preserved (including invalid UTF-8 TEXT key/label, BLOB key/
field and embedded NUL); exact composite keys, indexes, tokens, task/pause/reservation
rows and sequences retained; reopen; rollback between ALTERs; busy competing writer;
source schema rechecked under write reservation; wrong/partial/already-v4/extra
trigger/missing-index refusal; unique-index enforcement and next sequence values.

Intentionally NOT implemented: production schema opener/validator, real catalog
incarnation/writer protocol, repair adapter, auth/lifecycle dispatch, Android SQLite
migration/process-death/device testing, or Room metadata advancement. Harness leaves
user_version3 and original Room hash unchanged, so its structural-v4 result is NOT
an accepted or reopenable-as-v4 Room database. No user database was opened or mutated.

No new acceptance counter: SUP-S04/S08 and T13 remain open. This evidence establishes
a useful required migration SQL delta, not stale-row repair or a working clone tool.
