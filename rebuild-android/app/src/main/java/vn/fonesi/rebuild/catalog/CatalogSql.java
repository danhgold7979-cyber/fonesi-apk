package vn.fonesi.rebuild.catalog;

/** Fixed trusted SQL; never accepts identifiers or caller SQL. */
public final class CatalogSql {
    private CatalogSql() { }
    public static final int APPLICATION_ID = 1178815793;
    public static final String META_DDL =
            "CREATE TABLE catalog_meta (id INTEGER PRIMARY KEY CHECK(id=1), catalog_id TEXT NOT NULL, revision INTEGER NOT NULL CHECK(revision>=0))\n";
    public static final String CATALOG_DDL =
            "CREATE TABLE manager_catalog (engine_key TEXT NOT NULL, clone_id TEXT NOT NULL, package_name TEXT NOT NULL, label TEXT NOT NULL, group_name TEXT, legacy_launch_token TEXT, accent_key TEXT, badge_text TEXT, cluster_id TEXT, readiness TEXT NOT NULL CHECK(readiness='UNVERIFIED'), launch_allowed INTEGER NOT NULL CHECK(launch_allowed=0), PRIMARY KEY(engine_key,clone_id))\n";
    public static final String FIRST_PAGE =
            "SELECT\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' THEN 1 ELSE 0 END AS catalog_id_is_text,\n" +
            "  CASE WHEN m.catalog_id IS NULL THEN 1 ELSE 0 END AS catalog_id_is_null,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' THEN length(CAST(m.catalog_id AS BLOB)) ELSE -1 END AS catalog_id_byte_length,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))>36 THEN 1 ELSE 0 END AS catalog_id_over_budget,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))<=36 THEN CAST(m.catalog_id AS BLOB) ELSE NULL END AS catalog_id_bytes,\n" +
            "  CASE WHEN typeof(m.revision)='integer' AND m.revision>=0 THEN 1 ELSE 0 END AS revision_valid,\n" +
            "  CASE WHEN typeof(m.revision)='integer' THEN m.revision ELSE NULL END AS catalog_revision,\n" +
            "  CASE WHEN p.position IS NULL THEN 0 ELSE 1 END AS row_present,\n" +
            "  p.*\n" +
            "FROM main.catalog_meta AS m\n" +
            "LEFT JOIN (\n" +
            " SELECT\n" +
            "  rowid AS position,\n" +
            "  CASE WHEN typeof(engine_key)='text' THEN 1 ELSE 0 END AS engine_key_is_text,\n" +
            "  CASE WHEN engine_key IS NULL THEN 1 ELSE 0 END AS engine_key_is_null,\n" +
            "  CASE WHEN typeof(engine_key)='text' THEN length(CAST(engine_key AS BLOB)) ELSE -1 END AS engine_key_byte_length,\n" +
            "  CASE WHEN typeof(engine_key)='text' AND length(CAST(engine_key AS BLOB))>16384 THEN 1 ELSE 0 END AS engine_key_over_budget,\n" +
            "  CASE WHEN typeof(engine_key)='text' AND length(CAST(engine_key AS BLOB))<=16384 THEN CAST(engine_key AS BLOB) ELSE NULL END AS engine_key_bytes,\n" +
            "  CASE WHEN typeof(clone_id)='text' THEN 1 ELSE 0 END AS clone_id_is_text,\n" +
            "  CASE WHEN clone_id IS NULL THEN 1 ELSE 0 END AS clone_id_is_null,\n" +
            "  CASE WHEN typeof(clone_id)='text' THEN length(CAST(clone_id AS BLOB)) ELSE -1 END AS clone_id_byte_length,\n" +
            "  CASE WHEN typeof(clone_id)='text' AND length(CAST(clone_id AS BLOB))>16384 THEN 1 ELSE 0 END AS clone_id_over_budget,\n" +
            "  CASE WHEN typeof(clone_id)='text' AND length(CAST(clone_id AS BLOB))<=16384 THEN CAST(clone_id AS BLOB) ELSE NULL END AS clone_id_bytes,\n" +
            "  CASE WHEN typeof(package_name)='text' THEN 1 ELSE 0 END AS package_name_is_text,\n" +
            "  CASE WHEN package_name IS NULL THEN 1 ELSE 0 END AS package_name_is_null,\n" +
            "  CASE WHEN typeof(package_name)='text' THEN length(CAST(package_name AS BLOB)) ELSE -1 END AS package_name_byte_length,\n" +
            "  CASE WHEN typeof(package_name)='text' AND length(CAST(package_name AS BLOB))>16384 THEN 1 ELSE 0 END AS package_name_over_budget,\n" +
            "  CASE WHEN typeof(package_name)='text' AND length(CAST(package_name AS BLOB))<=16384 THEN CAST(package_name AS BLOB) ELSE NULL END AS package_name_bytes,\n" +
            "  CASE WHEN typeof(label)='text' THEN 1 ELSE 0 END AS label_is_text,\n" +
            "  CASE WHEN label IS NULL THEN 1 ELSE 0 END AS label_is_null,\n" +
            "  CASE WHEN typeof(label)='text' THEN length(CAST(label AS BLOB)) ELSE -1 END AS label_byte_length,\n" +
            "  CASE WHEN typeof(label)='text' AND length(CAST(label AS BLOB))>16384 THEN 1 ELSE 0 END AS label_over_budget,\n" +
            "  CASE WHEN typeof(label)='text' AND length(CAST(label AS BLOB))<=16384 THEN CAST(label AS BLOB) ELSE NULL END AS label_bytes,\n" +
            "  CASE WHEN typeof(group_name)='text' THEN 1 ELSE 0 END AS group_name_is_text,\n" +
            "  CASE WHEN group_name IS NULL THEN 1 ELSE 0 END AS group_name_is_null,\n" +
            "  CASE WHEN typeof(group_name)='text' THEN length(CAST(group_name AS BLOB)) ELSE -1 END AS group_name_byte_length,\n" +
            "  CASE WHEN typeof(group_name)='text' AND length(CAST(group_name AS BLOB))>16384 THEN 1 ELSE 0 END AS group_name_over_budget,\n" +
            "  CASE WHEN typeof(group_name)='text' AND length(CAST(group_name AS BLOB))<=16384 THEN CAST(group_name AS BLOB) ELSE NULL END AS group_name_bytes,\n" +
            "  CASE WHEN typeof(readiness)='text' AND CAST(readiness AS BLOB)=X'554E5645524946494544' THEN 1 ELSE 0 END AS readiness_inert,\n" +
            "  CASE WHEN typeof(launch_allowed)='integer' AND launch_allowed=0 THEN 1 ELSE 0 END AS launch_inert\n" +
            " FROM main.manager_catalog\n" +
            " WHERE rowid >= CAST(? AS INTEGER)\n" +
            " ORDER BY rowid ASC\n" +
            " LIMIT 129\n" +
            ") AS p ON 1=1\n" +
            "WHERE m.id=1\n" +
            "ORDER BY p.position ASC;\n";
    public static final String NEXT_PAGE =
            "SELECT\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' THEN 1 ELSE 0 END AS catalog_id_is_text,\n" +
            "  CASE WHEN m.catalog_id IS NULL THEN 1 ELSE 0 END AS catalog_id_is_null,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' THEN length(CAST(m.catalog_id AS BLOB)) ELSE -1 END AS catalog_id_byte_length,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))>36 THEN 1 ELSE 0 END AS catalog_id_over_budget,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))<=36 THEN CAST(m.catalog_id AS BLOB) ELSE NULL END AS catalog_id_bytes,\n" +
            "  CASE WHEN typeof(m.revision)='integer' AND m.revision>=0 THEN 1 ELSE 0 END AS revision_valid,\n" +
            "  CASE WHEN typeof(m.revision)='integer' THEN m.revision ELSE NULL END AS catalog_revision,\n" +
            "  CASE WHEN p.position IS NULL THEN 0 ELSE 1 END AS row_present,\n" +
            "  p.*\n" +
            "FROM main.catalog_meta AS m\n" +
            "LEFT JOIN (\n" +
            " SELECT\n" +
            "  rowid AS position,\n" +
            "  CASE WHEN typeof(engine_key)='text' THEN 1 ELSE 0 END AS engine_key_is_text,\n" +
            "  CASE WHEN engine_key IS NULL THEN 1 ELSE 0 END AS engine_key_is_null,\n" +
            "  CASE WHEN typeof(engine_key)='text' THEN length(CAST(engine_key AS BLOB)) ELSE -1 END AS engine_key_byte_length,\n" +
            "  CASE WHEN typeof(engine_key)='text' AND length(CAST(engine_key AS BLOB))>16384 THEN 1 ELSE 0 END AS engine_key_over_budget,\n" +
            "  CASE WHEN typeof(engine_key)='text' AND length(CAST(engine_key AS BLOB))<=16384 THEN CAST(engine_key AS BLOB) ELSE NULL END AS engine_key_bytes,\n" +
            "  CASE WHEN typeof(clone_id)='text' THEN 1 ELSE 0 END AS clone_id_is_text,\n" +
            "  CASE WHEN clone_id IS NULL THEN 1 ELSE 0 END AS clone_id_is_null,\n" +
            "  CASE WHEN typeof(clone_id)='text' THEN length(CAST(clone_id AS BLOB)) ELSE -1 END AS clone_id_byte_length,\n" +
            "  CASE WHEN typeof(clone_id)='text' AND length(CAST(clone_id AS BLOB))>16384 THEN 1 ELSE 0 END AS clone_id_over_budget,\n" +
            "  CASE WHEN typeof(clone_id)='text' AND length(CAST(clone_id AS BLOB))<=16384 THEN CAST(clone_id AS BLOB) ELSE NULL END AS clone_id_bytes,\n" +
            "  CASE WHEN typeof(package_name)='text' THEN 1 ELSE 0 END AS package_name_is_text,\n" +
            "  CASE WHEN package_name IS NULL THEN 1 ELSE 0 END AS package_name_is_null,\n" +
            "  CASE WHEN typeof(package_name)='text' THEN length(CAST(package_name AS BLOB)) ELSE -1 END AS package_name_byte_length,\n" +
            "  CASE WHEN typeof(package_name)='text' AND length(CAST(package_name AS BLOB))>16384 THEN 1 ELSE 0 END AS package_name_over_budget,\n" +
            "  CASE WHEN typeof(package_name)='text' AND length(CAST(package_name AS BLOB))<=16384 THEN CAST(package_name AS BLOB) ELSE NULL END AS package_name_bytes,\n" +
            "  CASE WHEN typeof(label)='text' THEN 1 ELSE 0 END AS label_is_text,\n" +
            "  CASE WHEN label IS NULL THEN 1 ELSE 0 END AS label_is_null,\n" +
            "  CASE WHEN typeof(label)='text' THEN length(CAST(label AS BLOB)) ELSE -1 END AS label_byte_length,\n" +
            "  CASE WHEN typeof(label)='text' AND length(CAST(label AS BLOB))>16384 THEN 1 ELSE 0 END AS label_over_budget,\n" +
            "  CASE WHEN typeof(label)='text' AND length(CAST(label AS BLOB))<=16384 THEN CAST(label AS BLOB) ELSE NULL END AS label_bytes,\n" +
            "  CASE WHEN typeof(group_name)='text' THEN 1 ELSE 0 END AS group_name_is_text,\n" +
            "  CASE WHEN group_name IS NULL THEN 1 ELSE 0 END AS group_name_is_null,\n" +
            "  CASE WHEN typeof(group_name)='text' THEN length(CAST(group_name AS BLOB)) ELSE -1 END AS group_name_byte_length,\n" +
            "  CASE WHEN typeof(group_name)='text' AND length(CAST(group_name AS BLOB))>16384 THEN 1 ELSE 0 END AS group_name_over_budget,\n" +
            "  CASE WHEN typeof(group_name)='text' AND length(CAST(group_name AS BLOB))<=16384 THEN CAST(group_name AS BLOB) ELSE NULL END AS group_name_bytes,\n" +
            "  CASE WHEN typeof(readiness)='text' AND CAST(readiness AS BLOB)=X'554E5645524946494544' THEN 1 ELSE 0 END AS readiness_inert,\n" +
            "  CASE WHEN typeof(launch_allowed)='integer' AND launch_allowed=0 THEN 1 ELSE 0 END AS launch_inert\n" +
            " FROM main.manager_catalog\n" +
            " WHERE rowid > CAST(? AS INTEGER)\n" +
            " ORDER BY rowid ASC\n" +
            " LIMIT 129\n" +
            ") AS p ON 1=1\n" +
            "WHERE m.id=1\n" +
            "ORDER BY p.position ASC;\n";
    public static final String FINAL_META =
            "SELECT\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' THEN 1 ELSE 0 END AS catalog_id_is_text,\n" +
            "  CASE WHEN m.catalog_id IS NULL THEN 1 ELSE 0 END AS catalog_id_is_null,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' THEN length(CAST(m.catalog_id AS BLOB)) ELSE -1 END AS catalog_id_byte_length,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))>36 THEN 1 ELSE 0 END AS catalog_id_over_budget,\n" +
            "  CASE WHEN typeof(m.catalog_id)='text' AND length(CAST(m.catalog_id AS BLOB))<=36 THEN CAST(m.catalog_id AS BLOB) ELSE NULL END AS catalog_id_bytes,\n" +
            "  CASE WHEN typeof(m.revision)='integer' AND m.revision>=0 THEN 1 ELSE 0 END AS revision_valid,\n" +
            "  CASE WHEN typeof(m.revision)='integer' THEN m.revision ELSE NULL END AS catalog_revision\n" +
            "FROM main.catalog_meta AS m\n" +
            "WHERE m.id=1;\n";
    public static final String META_CARDINALITY =
            "SELECT count(*), sum(CASE WHEN id=1 THEN 1 ELSE 0 END) FROM main.catalog_meta\n";
}
