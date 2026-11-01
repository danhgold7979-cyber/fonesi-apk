# Dormant page adapter — local source evidence, not Android acceptance

CatalogPageRead is package-private and has no MainActivity/UI caller. It reuses the
existing read-only lease, exact pinned page assets and pure CatalogPage consumer.
OwnedCatalogWriter now offers only a read-only inspection entry reusing the exact
same format guard; no apply/CommitBoundary is called by the reader. Before-query
callbacks recheck the lease and budget, not grant authorization. The original writer
transaction/commit path remains gated and dormant. Its guard SQL predicates are
unchanged; query calls route through a helper (no callback for the writer path).

ExistingCatalogRead gains an owner-only pre-close external-resource quarantine bit.
Cursor-close uncertainty sets it before DB close; successful DB close cannot release
that reservation or return a page. Calling quarantine after confirmed close rejects,
never poisons a different owner's reservation. No raw Cursor/database/lease escapes.

Six new source/native-SQL interface cases, five affected lease cases, seven affected
writer/asset cases and thirteen shell cases PASS locally. Source tests initially
caught a missing mandatory HOST command and ordering/slice assumptions; those exact
causes were corrected without dropping predicates. The pure consumer269 assertions
were unchanged and not rerun. These31cases are not execution of Android APIs. Exact
future hosted total with the separate proposed comparison changes would be140Python
cases; no future receipt is pre-filled. Actual new compile and runtime gates remain.

The schema asset is loaded once per inspection with the same pinned bytes; page/meta
assets are bounded4096 and exact hashes. Four fixed format/profile assets are not
executed as migrations. Query row projection uses exact37/10 column identities,
actual Cursor storage classes, bounded BLOB access and signed bindLong. The129th
lookahead transport deliberately reads only metadata/presence/position. No native
heap limit, SQLite thread/process adversary isolation or real cancellation timing is
claimed from source fixtures.

Observed1..5000ms deadline and cancellation fail without partial results. A successful
internal page is not an auth grant or UI delivery. Real broker/lifecycle/all-writer
integration, owned Android CursorWindow/refill/encoding/cleanup fixtures, source races,
locked-user behavior and device tests remain NOT_RUN. AndroidX download and local Git
fetch denials remain untouched; this source work required neither denied action.
