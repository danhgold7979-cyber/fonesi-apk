SELECT
typeof(m.id) AS meta_id_type,
CASE WHEN typeof(m.id)='integer' THEN m.id ELSE NULL END AS meta_id,
typeof(m.catalog_id) AS catalog_id_type,
CASE WHEN m.catalog_id IS NULL THEN 1 ELSE 0 END AS catalog_id_is_null,
CASE WHEN typeof(m.catalog_id)='text' THEN length(CAST(m.catalog_id AS BLOB)) ELSE -1 END AS catalog_id_byte_length,
CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))>36 THEN 1 ELSE 0 END AS catalog_id_over_budget,
CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))<=36 THEN CAST(m.catalog_id AS BLOB) ELSE NULL END AS catalog_id_bytes,
typeof(m.revision) AS revision_type,
CASE WHEN typeof(m.revision)='integer' AND m.revision>=0 THEN 1 ELSE 0 END AS revision_valid,
CASE WHEN typeof(m.revision)='integer' THEN m.revision ELSE NULL END AS catalog_revision
FROM main.catalog_meta AS m LIMIT 2;
