# Exact catalog page query recipe — planning only

This file is documentation, not an executed database migration or source implementation. Read with REBUILD_CATALOG_API26_RECIPE_AUDIT.md. Literal SQL below has no untrusted table/field/operator substitution. Bootstrap/schema DDL is pinned in that audit.

## First-page SQL

Bind the sole parameter to decimal Long.MIN_VALUE. Subsequent page uses a separately fixed constant replacing only `rowid >=` with `rowid >`, binding last accepted rowid. Never add an OR-based first-page shortcut or interpolate a view string.

```sql
SELECT
  CASE WHEN typeof(m.catalog_id)='text' THEN 1 ELSE 0 END AS catalog_id_is_text,
  CASE WHEN m.catalog_id IS NULL THEN 1 ELSE 0 END AS catalog_id_is_null,
  CASE WHEN typeof(m.catalog_id)='text' THEN length(CAST(m.catalog_id AS BLOB)) ELSE -1 END AS catalog_id_byte_length,
  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))>36 THEN 1 ELSE 0 END AS catalog_id_over_budget,
  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))<=36 THEN CAST(m.catalog_id AS BLOB) ELSE NULL END AS catalog_id_bytes,
  CASE WHEN typeof(m.revision)='integer' AND m.revision>=0 THEN 1 ELSE 0 END AS revision_valid,
  CASE WHEN typeof(m.revision)='integer' THEN m.revision ELSE NULL END AS catalog_revision,
  CASE WHEN p.position IS NULL THEN 0 ELSE 1 END AS row_present,
  p.*
FROM main.catalog_meta AS m
LEFT JOIN (
 SELECT
  rowid AS position,
  CASE WHEN typeof(engine_key)='text' THEN 1 ELSE 0 END AS engine_key_is_text,
  CASE WHEN engine_key IS NULL THEN 1 ELSE 0 END AS engine_key_is_null,
  CASE WHEN typeof(engine_key)='text' THEN length(CAST(engine_key AS BLOB)) ELSE -1 END AS engine_key_byte_length,
  CASE WHEN typeof(engine_key)='text' AND length(CAST(engine_key AS BLOB))>16384 THEN 1 ELSE 0 END AS engine_key_over_budget,
  CASE WHEN typeof(engine_key)='text' AND length(CAST(engine_key AS BLOB))<=16384 THEN CAST(engine_key AS BLOB) ELSE NULL END AS engine_key_bytes,
  CASE WHEN typeof(clone_id)='text' THEN 1 ELSE 0 END AS clone_id_is_text,
  CASE WHEN clone_id IS NULL THEN 1 ELSE 0 END AS clone_id_is_null,
  CASE WHEN typeof(clone_id)='text' THEN length(CAST(clone_id AS BLOB)) ELSE -1 END AS clone_id_byte_length,
  CASE WHEN typeof(clone_id)='text' AND length(CAST(clone_id AS BLOB))>16384 THEN 1 ELSE 0 END AS clone_id_over_budget,
  CASE WHEN typeof(clone_id)='text' AND length(CAST(clone_id AS BLOB))<=16384 THEN CAST(clone_id AS BLOB) ELSE NULL END AS clone_id_bytes,
  CASE WHEN typeof(package_name)='text' THEN 1 ELSE 0 END AS package_name_is_text,
  CASE WHEN package_name IS NULL THEN 1 ELSE 0 END AS package_name_is_null,
  CASE WHEN typeof(package_name)='text' THEN length(CAST(package_name AS BLOB)) ELSE -1 END AS package_name_byte_length,
  CASE WHEN typeof(package_name)='text' AND length(CAST(package_name AS BLOB))>16384 THEN 1 ELSE 0 END AS package_name_over_budget,
  CASE WHEN typeof(package_name)='text' AND length(CAST(package_name AS BLOB))<=16384 THEN CAST(package_name AS BLOB) ELSE NULL END AS package_name_bytes,
  CASE WHEN typeof(label)='text' THEN 1 ELSE 0 END AS label_is_text,
  CASE WHEN label IS NULL THEN 1 ELSE 0 END AS label_is_null,
  CASE WHEN typeof(label)='text' THEN length(CAST(label AS BLOB)) ELSE -1 END AS label_byte_length,
  CASE WHEN typeof(label)='text' AND length(CAST(label AS BLOB))>16384 THEN 1 ELSE 0 END AS label_over_budget,
  CASE WHEN typeof(label)='text' AND length(CAST(label AS BLOB))<=16384 THEN CAST(label AS BLOB) ELSE NULL END AS label_bytes,
  CASE WHEN typeof(group_name)='text' THEN 1 ELSE 0 END AS group_name_is_text,
  CASE WHEN group_name IS NULL THEN 1 ELSE 0 END AS group_name_is_null,
  CASE WHEN typeof(group_name)='text' THEN length(CAST(group_name AS BLOB)) ELSE -1 END AS group_name_byte_length,
  CASE WHEN typeof(group_name)='text' AND length(CAST(group_name AS BLOB))>16384 THEN 1 ELSE 0 END AS group_name_over_budget,
  CASE WHEN typeof(group_name)='text' AND length(CAST(group_name AS BLOB))<=16384 THEN CAST(group_name AS BLOB) ELSE NULL END AS group_name_bytes,
  CASE WHEN typeof(readiness)='text' AND CAST(readiness AS BLOB)=X'554E5645524946494544' THEN 1 ELSE 0 END AS readiness_inert,
  CASE WHEN typeof(launch_allowed)='integer' AND launch_allowed=0 THEN 1 ELSE 0 END AS launch_inert
 FROM main.manager_catalog
 WHERE rowid >= CAST(? AS INTEGER)
 ORDER BY rowid ASC
 LIMIT 129
) AS p ON 1=1
WHERE m.id=1
ORDER BY p.position ASC;
```

## Final independent metadata SQL

Close the page Cursor first; run this new query using the same cancellation/deadline request but no long-lived transaction. Require exactly one metadata row with strict36byte canonical UUID and valid nonnegative integer revision, matching every accepted page row and requested cursor. Missing/extra/mismatching metadata is failure, never empty-success.

```sql
SELECT
  CASE WHEN typeof(m.catalog_id)='text' THEN 1 ELSE 0 END AS catalog_id_is_text,
  CASE WHEN m.catalog_id IS NULL THEN 1 ELSE 0 END AS catalog_id_is_null,
  CASE WHEN typeof(m.catalog_id)='text' THEN length(CAST(m.catalog_id AS BLOB)) ELSE -1 END AS catalog_id_byte_length,
  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))>36 THEN 1 ELSE 0 END AS catalog_id_over_budget,
  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))<=36 THEN CAST(m.catalog_id AS BLOB) ELSE NULL END AS catalog_id_bytes,
  CASE WHEN typeof(m.revision)='integer' AND m.revision>=0 THEN 1 ELSE 0 END AS revision_valid,
  CASE WHEN typeof(m.revision)='integer' THEN m.revision ELSE NULL END AS catalog_revision
FROM main.catalog_meta AS m
WHERE m.id=1;
```

## Review invariants

- `row_present=0` means metadata-only sentinel; ignore its NULL page projections, not the metadata fields. A missing meta row produces no result and is a failure. Extra meta rows are rejected by prior singleton validation and final cardinality checks.
- Validate row positions strictly increasing; accept at most128. Row129 is lookahead; never publish it. Aggregate-budget stop leaves cursor at last accepted row, not rejected/peek row. First-row budget failure fails the page.
- For ordinary rows all five field flags must be internally consistent. Only group_name allows storedNULL; emptyTEXT is a zero-length BLOB, distinct from NULL. IDs remain exact opaque text after strict UTF8REPORT decode and byte roundtrip; schema does not inherit display budget limits.
- Check metadata on each row; CursorWindow refill may re-execute. Do not filter metadata revision in SQL, which could hide a changed revision as no rows. Final post-close metadata read catches changes even when the result window shrank. Unknown cursor outcomes fail.
- Both inert flags must equal1. NonTEXT/NULL/overbudget/invalidUTF8 and existing unsupported empty package/label fail without source change. No token/account/legacy launch route is selected.
- Tests planned: empty source, signed rowid Long.MIN_VALUE/negative/zero/positive/Long.MAX_VALUE in order (initial includes MIN, continuation never adds1),1/128/129rows, byte/UTF16/normalized boundaries, exact opaque/composite identities, malformed storage type/UTF8 flags, revision change between cursor fills or before finalread, cancel/deadline and no partialpublish. These are planned tests, not results; no denied experiment is included.
