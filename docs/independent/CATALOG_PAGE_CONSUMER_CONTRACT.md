# Pure bounded page consumer — proposed next source boundary

Original P055/RM-13b/SUP-S08/SEC-021 and the fixed37/10column query contract remain
unchanged. This proposes a first-party Java consumer of owned stable projection
snapshots, not a Cursor adapter, schema guard, source/auth grant, UI, engine or file
opener. No Android dependency, I/O, raw SQL, normalization, provider or mutable DB.
The existing CatalogKey exact-key decoder is reused rather than reimplemented.

## Input transport and source obligations
Future Android code must validate exact query names/count/types and make stable
owned Object[] cell snapshots: SQLite INTEGER -> Long only, projected typeof TEXT ->
String from its fixed literal type vocabulary, BLOB -> byte[], SQL NULL -> null.
Never use Cursor coercion to hide a nonmatching storage class. Inputs/arrays must
remain stable for each call; a defensive copy alone does not make a concurrent
snapshot atomic. Array length, cell class and bounded byte lengths are validated
before any copy/decode. No arbitrary object.toString or guessed type conversion.

Consumer begins from a10column pre-page metadata snapshot plus either FIRST request
or a prior immutable continuation cursor. Metadata must be one actual source row
(the caller establishes cardinality), idINTEGER1, canonical lowerhex36byte generation
and nonnegative INTEGER revision. The generation is internal snapshot identity, NOT
authentication. Continuation cursor binds exact prior generation/revision and last
ACCEPTED signed rowid; first lower bound is Long.MIN_VALUE. No cursor arithmetic.
The helper cannot prove that a metadata array came from a fresh read or that the
underlying connection schema/encoding is valid; future reviewed adapter must do so.

## Streaming acceptance and publication boundary
A worker-confined builder processes one37cell snapshot at a time. It must reject
wrong-thread mutation/extraction without returning data. Snapshot/generation/revision
are checked on EVERY input row, including lookahead/aggregate-stop rows. Signed
positions must strictly increase; zero and MIN are ordinary rows, not emptiness.
Exact CatalogKey duplicate among accepted rows fails. No partial result is exposed.

row_present0 requires all row_position-and-later cells NULL and no prior ordinary
row; it is an empty sentinel, not absent metadata. No ordinary row may follow it.
Caller must explicitly finish row iteration/close the actual Cursor before final
metadata; helper state makes a missing end-of-input/final snapshot fail, but is not
proof of Android Cursor closure. At most129 projected rows may be submitted.

Accept <=128 rows and <=262144 raw bytes across the five selected field payloads.
Metadata/type-flag/native-cursor overhead is not included in that field publication
budget and no hard-heap claim is made. At row129, check metadata/position then mark
lookahead and stop before decoding/publishing its payload. A lookahead is not a
claim that its unconsumed fields are valid; next page must validate it normally.
At aggregate stop, retain cursor at last accepted row; never skip the pending row.
First-row field/transfer-budget failure fails the page. A page with hasMore is not
a claim that the complete catalog is valid. Stopping never creates a synthetic row.

Each accepted row has exact opaque CatalogKey, nonempty package/label Strings and
nullable original group. Per-field projected bytes <=16384; strict UTF8 REPORT and
roundtrip; decoded <=4096UTF16. Type/null/length/oversize facts must agree. Unknown,
nonTEXT or malformed bytes fail retained, not silently skip/default/trim. Over-budget
is an operation-budget failure, not a claim of corrupted storage. GroupNULL remains
null in the data object; any display-empty mapping is later and never written back.
No raw style/token/account/cluster is present in this query and no defaults are
invented for them. Future renderer retains its separate8192UTF16 normalized bound.

On complete input or explicit bounded stop, caller closes its Cursor and supplies a
fresh independent10cell metadata snapshot. It must match pre-page and continuation
snapshot; otherwise fail entire page. Only then may helper return an immutable page
with rows and optional continuation cursor. Auth/owner/cancel/deadline recheck before
actual UI delivery remains a separate mandatory adapter gate. No helper success is
an authentication claim or permission to write/launch. A write after final read can
make the page stale; all protected edits still require their own fresh broker and
revision checks. All trusted writers must participate, not defeated by a decoder.

## Lifecycle and errors
Builder is single-owner-thread, no callbacks or Android lifecycle assumptions. It
supports explicit cancellation/failure to discard pending rows; terminal finish or
failure cannot be reused, and no result is exposed early. Return/throw only generic
typed failures, never keys/source paths or raw payload in exception messages. Strong
references may be released on finish/cancel, not logged. Unknown input/revision/state
is failure, never empty success. Immutable rows/list/cursor expose no mutable arrays.

## Focused owned HOST tests before later Android integration
Valid1/128rows/empty sentinel and metadata, all signed cursor edges,129lookahead;
256KiB aggregate stop retaining cursor; exact namespace keys and null/empty group;
malformed UTF8/type/flags/NUL and byte/UTF16 bounds; invalid orchanged metadata before/
within/after page, cursor fromanothergeneration, duplicate/order violation, sentinel
mixedwithrows, missingfinish/postmeta, cancel/terminalreuse/wrongthread, and defensive
retention after caller mutates arrays AFTER a completed call. Concurrent input mutation
is excluded by explicit snapshot precondition, not claimed solved by these tests.

New core source is packaged: later exact-source JDK17/COMPILE required after review.
HOST parser tests do not prove actual Android CursorWindow refill/SQLite/lifecycle/
auth/permissions, source schema or8app behavior. No dependency download is involved;
optional AndroidX artifact approval remains separate and pending.

## Required acceptance precision before implementation
Accepted ordinary rows require both final inert flags exactly Long1. No zero-row
submission may finish successfully: valid empty means exactly one metadata-bearing
sentinel and no other input. Missing actual metadata/cardinality remains caller guard,
not invented by matching before/after snapshots around zero page rows.

For an ordinary potential accepted row, first validate all raw cell classes, type/
null/length/oversize consistency, per-field transfer bounds and inert flags. Only then
sum raw field bytes. If that structurally valid projected row exceeds remaining page
aggregate budget, stop before UTF8 decoding/accepting it and retain last accepted
cursor. Malformed projections or field-over-budget rows are failures, never aggregate-pagination
shortcuts. UTF8/UTF16 rejection after allocation remains a failure, not silent skip.
The deliberate row129 lookahead exception checks exact37cell array shape, metadata,
row-present and signed position only; its payload/inert fields are unvalidated and
not accepted. No >129th input or extra submit after STOP is allowed. Extra input is
a terminal state failure, not ignored. endInput is required even after STOP, then a
fresh post-close metadata snapshot; absent endInput/zero input cannot yield a page.

At an aggregate-budget stop, a structurally valid pending payload is deliberately not
UTF8-decoded or accepted. Its UTF8 validity is unknown until the next page validates
that same row; the cursor stays before it. This is not permission to skip it or claim
the remaining catalog is valid. It differs from a malformed projection/oversize
field, which fails before aggregate pagination can be selected.
