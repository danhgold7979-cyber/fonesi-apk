# Android catalog writer source checkpoint — COMPILE pending

New package-private OwnedCatalogWriter contains actual Android SQLite transaction,
strict schema/index/metadata guards, blob-bound revision/style/readback, raw badge
operation budget, cancellation and one-shot commit-boundary implementation. It opens
no file/DB and has no runtime caller or actual broker implementation. Caller exclusive
handle ownership and actual broker correctness are explicit unsatisfied runtime gates.

MainActivity, manifest and all UI resources remain byte-identical to public8b372a22.
No permission/component/network/SDK/license changes. Core Java and five SQL assets
are now genuine APK inputs; the gate no longer exempts production CatalogKey/StyleValue
or SQL asset edits. APK verifier binds all five exact source asset hashes, rejects
missing/extra/duplicate/changed assets, and baseline binding requires that proof.
Historical detached-HOST documents are explicitly dated/scoped to their old commits.

Local impacted checks PASS:6Android-source/asset/projection fixtures,6baseline,5compile-routing,
13shell,4compile-aggregate and20manifest/resource predicates (54Python cases). These
are static/synthetic fixtures, not actual Android transaction tests or APK evidence.
The prior15nativeSQLite writer cases remain the SQL primitive evidence and were not
rerun for these Android plumbing edits. No new Java HOST/AGP/D8/Android execution is
claimed locally. All assets/verifier/binder/source changes require independent review
before a new exact-source hosted build, using only installed pinned SDK/JDK17.

New compile/lint/signature/manifest/assets receipt still PENDING. Android database
pooling, physical file ownership, cross-thread broker/cancel ordering, process death,
all-writer routing, Room migration/restore and device/app compatibility remain NOT_RUN.
No full task/requirement or V2 acceptance. The schema design is root-adopted engineering
scope, not permission to operate an existing user database.
