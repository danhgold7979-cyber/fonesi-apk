UPDATE manager_catalog SET accent_key=?, badge_text=?
WHERE typeof(engine_key)='text' AND typeof(clone_id)='text'
  AND CAST(engine_key AS BLOB)=? AND CAST(clone_id AS BLOB)=?;
