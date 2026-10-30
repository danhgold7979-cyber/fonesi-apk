# B1a oracle freeze: findings and required decisions

Date: 2026-10-30. Test-only; unchanged Java21 production source pinned in `spec/bridge-oracle/v1/source-sha256.txt`. No portable classes or Android imports implemented.

## Findings sent for root review

### D1 — malformed Unicode provider IDs alias the scope namespace

The current `CapabilityPolicy.Provider` accepts both Java strings `p\uD800` and `p\uDC00` (unpaired surrogate code units). `SharedUpdateDispatcher.scopeDirectory` frames the provider with UTF-16 `String.length` and hashes UTF-8 bytes. Both accepted strings have length2 and produce UTF-8 bytes `70 3f`, so both produce the same directory hash:

`5c2772c2f70d5b2e059cef856bd5e900f6ad59759914acc8458d75b21d484edd`

The same hash also belongs to otherwise-valid provider ID `p?` (length2, UTF-8 `70 3f`). Invalid historical IDs must never be mapped into that valid namespace.

The journal's `DataOutputStream.writeUTF` uses modified UTF-8 over code units and preserves distinct bytes for these identities. Thus codec equality and lease namespace encoding disagree. This is an unintended namespace alias, not a cryptographic SHA256 collision. It may cause unrelated provider identities to share a lease directory. No guest-data access or isolation bypass was demonstrated; all runs are synthetic private temp files.

**Disposition:** DEFECT observation, excluded from desired portable positive acceptance. Recommended next decision: reject ill-formed UTF-16 at trusted identity ingress before hashing/persistence, preserve exact valid Unicode without normalization, and explicitly quarantine already persisted malformed identities rather than silently rewriting/mapping/deleting them. Root must approve revised contract/version handling before production or portable implementation; do not require the bridge to reproduce lossy acceptance.

### D2 — embedded NUL/control and visually blank identities accepted

`p\u0000x` and NBSP-only `\u00A0` are accepted provider/environment observations. Embedded NUL has different standard versus modified UTF-8 representation, though the tested scope hash remains distinct from ordinary IDs. NBSP is not Java `String.isBlank` whitespace and ASCII `trim` leaves it intact. These are undesirable/ambiguous identity-policy candidates, **not automatically proven exploits**.

**Disposition:** undecided input-policy observations, excluded from approved bridge input until explicit review. Decide permitted scalar/control categories and meaningful nonblank identity rules without accidentally normalizing two existing namespaces together. A later rule change must fail closed/quarantine old invalid records; no silent drop/reset or “successful migration.” No arbitrary provider identity rewrite is authorized by this fixture.

## Safe-input parity candidates (await root approval)

1. Exact provider ID/revision, package, environment, scope, operation/clone UUID, cluster SHA256 and affected-peer membership/equality survive copying and serialization.
2. UUID peer ordering follows signed `UUID.compareTo` on both halves, including high-bit most/least significant UUID values; lexicographic UUID-string sorting is not equivalent.
3. Valid supplementary Unicode provider `provider-😀` has UTF-16 length11; scope hash uses that length prefix and standard UTF-8. The journal uses `writeUTF` modified UTF-8 instead. Preserve both deliberate encodings byte-for-byte for valid identities, not a generic “UTF8 everywhere” rewrite.
4. PREPARED0, DISPATCHED1, CANCELLED1, VERIFIED2, REVIEW_REQUIRED2 are reachable value pairs; all sampled other state/revision combinations reject. Value construction is not authority to dispatch or journal-create.
5. Source provider revision is deliberately omitted from the package lease hash so unresolved old revisions cannot evade the same provider/package lease. Full revision remains part of durable operation identity.
6. Public journal read rejects checksum corruption, legacy-unbound/unknown version, truncation, oversized and trailing data. Existing no-replay and uncertainty behavior remains covered by original suites; these new observations do not replace those tests.

## Portable implementation remains gated

First review the desired valid-input contract and D1/D2 disposition. Only then design an independent Java17/API26 logical source layer with immutable copies, safe scalar validation and explicit codec compatibility. Keep Java21 source/fixtures as historical oracle even if a later corrected oracle is versioned. Never overwrite the defect evidence to make a new implementation appear historically identical.

Filesystem ownership, atomic publication/parent durability, cross-process locks, Android runtime and actual provider/auth/readback remain separate prerequisites. B1a creates no authority to enable UI operations or delete old implementation/user data.

## Proposed provider-ID contract for root approval (not implemented)

D1 is BLOCKING for a portable/production bridge. Keep the pinned legacy oracle unchanged. Proposed minimal deterministic identity rule:

- Non-null, nonempty Java string; every high surrogate must pair with the next low surrogate, and no isolated low surrogate. Validate code units before any UTF-8 encoding so replacement bytes can never define a namespace.
- Reject Unicode General_Category **Cc**, explicitly U+0000–U+001F and U+007F–U+009F (includes NUL). This proposal does not silently classify every format character Cf as Cc; bidi/format restrictions are a separate review decision, not a normalization step.
- Define whitespace with a fixed explicit set, not OS-varying Unicode tables: U+0009–000D, U+0020, U+0085, U+00A0 (NBSP), U+1680, U+2000–200A, U+2028–2029, U+202F, U+205F, U+3000. Reject whitespace-only IDs, including NBSP-only. Proposed edge rule also rejects leading/trailing members of this set, preserving the old ASCII-edge rejection and making Unicode edges explicit; internal non-control whitespace remains byte-exact.
- Retain the existing maximum **256 UTF-16 code units**. Explicitly account for at most **768 standard UTF-8 bytes**, computed from validated scalar values without replacement. Supplementary pairs count as two UTF-16 units/four UTF-8 bytes. These are identity-field budgets, not clone limits. Journal modified-UTF8 encoding remains a distinct codec requirement.
- Preserve accepted string contents exactly: no trim, Unicode normalization, case folding, transliteration or display-name substitution. NFC/NFD and case-distinct valid IDs remain distinct namespaces. Normalization is never a migration mechanism.
- Decide applicability explicitly: provider ID is mandatory for the namespace defect; applying the same rule to provider revision/environment is recommended consistency work but requires recorded compatibility approval. No rule change to package/UUID/digest contracts is implied.

Desired tests before any production hardening: null/empty, paired/unpaired surrogates at every position, Cc boundaries, NBSP/other fixed whitespace-only and edge cases, valid internal whitespace, supplementary Unicode, NFC/NFD distinction, UTF-16/UTF-8 length boundaries, and proof that accepted IDs cannot alias through lossy encoding. Freeze original legacy observations separately; a corrected validator gets new version/source provenance, never overwritten old expectations.

Persisted identity policy: when a trusted decoder/recovery path sees old malformed/disallowed text, mark the record/provider scope quarantined/unavailable, retain bytes/history, and never dispatch, auto-map, drop or delete guest data. Existing valid namespaces retain their exact scope hashes. This decision must be reviewed before modifying the kernel or implementing portable classes.


Root approved the proposed scalar/Cc/fixed-whitespace edge contract for provider ID, provider revision **and** environment for a separate new-kernel hardening checkpoint, after B1a freeze and exact CI. This does not modify the v1 oracle or authorize Android/provider integration. Clarifications: Cc rejection takes precedence (internal TAB/newline rejected; internal NBSP can remain); Cf-only strings such as zero-width space remain permitted unless separately prohibited, so this is not a promise to reject every visually blank identity.768 UTF-8 bytes is derived from256 valid UTF-16 units; boundary tests use256 three-byte BMP characters/128 supplementary pairs, not an impossible over768-within256 case.

## B1b disposition (standalone only)

After green B1a run41117815149, root approved the proposed scalar/Cc/fixed-whitespace/exact-text contract for provider ID, revision and environment. B1b current constructors reject D1 malformed-scalar aliases and D2 controls/edge whitespace; valid `p?` remains distinct accepted text. Historical v1 observations and source hashes are unchanged and run from an isolated archive. This is not retroactive approval of their unsafe acceptances. Checksummed invalid old records reject/retain with no external provider callbacks or invalid-record observer delivery; automatic repair/remapping/deletion/dispatch is forbidden. Actual Android quarantine ownership/durability and bridge/provider acceptance remain unresolved.
