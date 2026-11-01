# Catalog decoder + SQLite fragment HOST milestone

> Historical HOST-only milestone at `8b372a22`. Later Android adapter source packaging supersedes the detached-source classification; the original evidence remains scoped to this historical commit.

This integration requires the new catalog Java suite and owned nativeSQLite
migration fixture exactly once in the HOST aggregate. The parser accepts a single
positive catalog assertion receipt with explicit no-adapter/Android/auth/provider
scope and rejects limited-JDK output, missing/zero/duplicate/wrong-tier claims.
The JDK17 aggregate and exact source/log binding remain mandatory.

The compile-input gate explicitly recognizes only the two detached CatalogKey
source/test paths, their script, the nativeSQLite fixture script and exact two-ALTER
SQL fragment as HOST-only additions. None is included by current `:app` Gradle
source sets or packaging. Other new storage/core paths remain unknown and require
compilation. This classification is not blanket permission to wire storage without
review or skip compilation after app/Gradle changes.

Local impacted integration5cases and baseline6cases PASS. Reuse reviewed catalog
151LIMITED_HOST assertions and nativeSQLite6cases evidence rather than repeat them
for plumbing edits. Expected new hosted totals are91Python cases,83preflight +
128style +151catalog synthetic assertions; expected values are not actual results.
Actual JDK17/catalog/source receipt remains pending publication. Prior run41308120579
covers only public9b760a81; do not transfer its PASS to this successor.

No new APK expected because unchanged app/Gradle/Android gates retain prior accepted
sourcea3372fa6/run41290554652 evidence only. No new COMPILE/baseline receipt on skip.
All V2 proposals and writer-generation plan remain inactive. No production SQLite
opener/migration/repair/restore, Android test or requirement/task acceptance added.
