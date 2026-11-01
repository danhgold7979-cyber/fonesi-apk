# Bounded catalog SELECT source checkpoint

Three independently authored SELECT assets implement the reviewed fixed projection:
37-column first/continuation pages (129-row lookahead) and10-column metadata (LIMIT2).
SQL uses signed rowid comparisons, byte-counted guarded BLOB projections, explicit
null/type/oversize flags, exact inert readiness bytes and integer-only revision.
No token/account/style/guest authority is selected or storage data normalized.

Twelve own native SQLite fixtures PASS locally, SQLite3.53.1 via Python binding.
Proof: empty sentinel versus absent metadata;1/128/129lookahead and continuation;
signedMIN/negative/zero/MAX rowids; exact namespace/empty/NUL/case keys; embeddedNUL
and multibyte UTF8 16KiB boundaries; invalidUTF8 TEXT and BLOB-affinity preservation;
bounded malformed metadata/revision; exact readiness/launch type; extra/wrong/missing
metadata; fresh post-cursor-close revision/generation change including shrink toempty.
Three affected cases were rerun after strengthening multibyte/per-row metadata checks
and guaranteed readonly-connection cleanup; no unrelated suite repeated.

Fixture metadata validation is deliberately small and owned. It does not implement
production Android schema/access validation, Java strict decoder,128accepted-row/
256KiB publication budget, rendering, cancellation, CursorWindow refill behavior,
all-writer participation or lifecycle delivery. Native SQL projections are transfer
bounds, not a hard bound on SQLite internal allocations/lock latency. Unknown rows
and malformed bytes are retained; no existing user database or Android app was opened.

Independent source review, exact packaged8asset verifier integration and fresh hosted
JDK17/AGP/lint/APK proof are still pending. c7bd0f2e evidence covers the prior5assets
only. Original362 assertions and5/32acceptedtask count remain unchanged; no V2 or
provider acceptance, no fixture dependency/device action, no actual runtime proof.
