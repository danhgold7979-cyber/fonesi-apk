# Exact catalog decoding — original-scope source contract

Anchors: BRG-001 catalog exception; SEC-021; RM-25b; SUP-S05; frozen E82 schemas1–6
and STYLE-R1-01 / EXEC-ID-R1-01. Historical schema/query documents are references,
not implementation code to restore. No provider, new storage format or Android
reader is activated by this component.

## New Java-only value boundary
`CatalogKey.decode` takes each component's explicit SQLite storage-class enum and
raw byte array. Only TEXT is accepted; NULL/BLOB/INTEGER/REAL or missing bytes fail.
The future SQLite adapter must first validate/read back database encoding UTF8.
CAST(TEXT AS BLOB) bytes depend on database encoding: unsupported UTF16 sources
remain retained/quarantined, never reinterpreted as UTF8 or silently transcoded.
See https://sqlite.org/lang_expr.html#castexpr. The decoder takes explicitly UTF8
transport bytes, not arbitrary SQLite TEXT bytes. The adapter must obtain raw
bounded BLOB projections together with
original typeof flags; passing already replacement-decoded getString output would
violate this contract. The value itself does no database/path/resource access.

Use strict UTF-8 decoder REPORT and exact encode roundtrip. Reject overlong/truncated
sequences, UTF-8 surrogate encodings and lone continuation bytes; do not substitute
U+FFFD. A genuinely encoded U+FFFD remains valid identity. Do not apply style or
provider grammar: preserve empty strings, whitespace/NBSP, controls/NUL, punctuation,
case, Unicode composed/decomposed forms and all well-formed scalar values exactly.
No UUID parsing, concatenation, hashing-as-remapping or arbitrary normalization.

Read budget: at most16KiB raw bytes and4096UTF16 code units per component, matching
the frozen catalog read recipe. These are adapter transport/allocation bounds, not
schema validity. Over-budget means explicit BUDGET_EXCEEDED/read-quarantine, never
invalid identity, truncation, skipped row or empty success. The adapter still owes
page/aggregate/cancellation budgets; this standalone value is not a whole reader.

Caller must provide exclusively owned or stable arrays during decode; a defensive
copy does not promise an atomic snapshot of concurrently mutated input.
Copy bounded bytes before decoding; retain immutable exact String components.
Equality/hash include both components; equal hashes cannot collapse unequal pairs.
No raw-key logging/toString override. Failed result has no key. Input arrays are
never mutated, and later caller mutations cannot alter a decoded value. Invalid
stored bytes remain untouched in storage; a pure decoder cannot claim storage
retention beyond not mutating its supplied arrays.

Tests: null/nontEXT inputs; empty/space/NBSP/NUL; exact namespace/case/delimiter and
hash collision pairs; composed/decomposed Unicode; valid supplementary/U+FFFD;
malformed UTF-8 families; exact4096/4097UTF16 and16384/16385-byte budget boundaries;
no replacement/no trim; input/result immutability. Byte and UTF16 guards are separate;
a structurally impossible accepted boundary must not be fabricated.

Local JDK21 source17/target17 tests are LIMITED_HOST. HostedJDK17/Android behavior
need actual evidence. No metadata read/write, SQL adapter, authentication, row
incarnation, provider authority or requirement/task acceptance is added.
