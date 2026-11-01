# Catalog SELECT asset/receipt integration — source checkpoint

Adds exactly three new bounded SELECT assets to the existing exact packaged-asset
allowlist: eight total. Missing/changed/duplicate/extra assets still fail; each stays
<=4096bytes and every receipt hash must match reviewed source. No broad asset wildcard
or arbitrary additional SQL is accepted. Existing writer loads exactly its original
four pinned assets only; its runtime loader has no page-query caller. The predicate
now spells those four exact names instead of assuming all nonmigration assets belong
to the writer. Page consumer/runtime asset loading remains unimplemented.

Mandatory HOST aggregate includes test_catalog_page_sql.py exactly once. Expected
future hosted total is129Python cases, including12native page-query fixtures, plus
83preflight/128style/151key/60ownership synthetic assertions; these are expectations,
not a completed new run. Local affected integration proof: four exact asset/routing/
runtime-pin predicates, sixHOST and sixbaseline fixtures PASS. Unchanged Android
source, style/ownership Java tests and old APK build were not rerun locally.

Storage SQL is already an explicit APK asset source. New exact-source COMPILE and
lint/manifest/signature/backup/eight-asset evidence are required after review; prior
c7bd0f2e APK proves only its original five assets. No UI, permission, component,
SDK/toolchain/license, authentication or provider behavior changes. Task counters and
ANDROID/PROVIDER/PHONE remain unchanged. Denied optional AndroidX artifact downloads
are a separate pending gate; this integration does not download/import that library.
