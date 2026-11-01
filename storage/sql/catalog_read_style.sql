SELECT typeof(engine_key),CAST(engine_key AS BLOB),typeof(clone_id),CAST(clone_id AS BLOB),
       typeof(accent_key),CAST(accent_key AS BLOB),typeof(badge_text),CAST(badge_text AS BLOB)
FROM manager_catalog
WHERE typeof(engine_key)='text' AND typeof(clone_id)='text'
  AND CAST(engine_key AS BLOB)=? AND CAST(clone_id AS BLOB)=?;
