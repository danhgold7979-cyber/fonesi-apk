# Pure bounded page consumer — SOURCE / limited HOST only

New first-party CatalogPage consumes stable owned37/10cell snapshots, reuses exact
CatalogKey decoding, and exposes only immutable completed rows/snapshot/continuation.
No Android, database, Cursor, auth broker, UI or I/O is implemented. A positive page
is display metadata, never permission to write/launch or proof of source validity.

269 own assertions PASS with local JDK21 module compiler/source17/target17, explicitly
LIMITED_HOST (not release17 API/Android proof). Covers required empty sentinel/nozero
input; signedMIN/negative/zero/MAX positions;128accepted/129unvalidatedlookahead;
exact262144field-byte aggregate stop and cursor retention; namespace/NUL/case/null
fidelity; raw type/flag/length/UTF8/UTF16/byte/inert failures; metadata changes before/
within/after includinglookahead; duplicate/order/mixedsentinel; terminal/cancel and
wrongthread refusal; no rawarray/list mutability leaks after completed calls.

Critical stop distinction: malformed projections and field-budget failures reject
before aggregate pagination. A structurally valid aggregate-stop pending payload is
not UTF8-decoded or accepted; its unchanged cursor makes next-page validation fail
if its UTF8 is invalid. The fixture verifies that distinction, not a false claim
that all unconsumed rows are valid. Caller must actually close Cursor and perform
fresh post-page metadata/access checks; pure helper cannot certify those events.

The initial232assertion suite passed; after adding explicit pre-stop failure ordering,
pending-invalid-UTF8 next-page validation and lookahead-snapshot/shape boundaries,
only this affected Java suite was rerun for the269total. No unrelated Java/Android
build was repeated. Independent source review and actual new hosted JDK17/COMPILE
are pending; this new core class is now an APK input. No existing proof is rebound,
no task progress uplift or higher-tier acceptance, no denied AndroidX download retry.
