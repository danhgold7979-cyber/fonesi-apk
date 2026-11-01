UPDATE catalog_meta SET revision=revision+1
WHERE id=1 AND typeof(catalog_id)='text' AND CAST(catalog_id AS BLOB)=?
  AND typeof(revision)='integer' AND revision=?
  AND revision>=0 AND revision<9223372036854775807;
