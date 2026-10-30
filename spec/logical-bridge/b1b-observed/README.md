# HISTORICAL B1b/D3 observations — not desired B2 acceptance

# B2 logical bridge observations

`observations.txt` is a reviewed compatibility baseline, not a provider acceptance
certificate or permission to deploy a runtime. It contains 17,317 sorted ASCII
key/value observations produced from the B1b host classes pinned in
`host-source-sha256.txt`. The portable harness is
`rebuild-logic/oracle/LogicalVectors.java`. Only its package declaration is rewritten
from `vn.fonesi.rebuild.logical` to `vn.fonesi.rebuild` for the host run; production
sources are not rewritten. Separate output directories/classpaths are required.

Initial generation used local JDK 21. Two independent JVM runs matched byte for
byte. Real hosted JDK 17 execution is a separate pending gate; `--release 17` alone
is not proof of JDK 17 runtime execution or Android API compatibility. Runners
compare to this checked-in fixture, never regenerate it on a mismatch.

SHA-256 of observations: `330acb1152da6f329792ea36628731f270183f341c4d6f37185da6c7ae679942`.

## Meaning and boundaries

- Policy matrix covers all 64 claim masks, four isolation labels, both request and
  evidence scopes, and six requested capabilities (6,144 combinations), plus
  missing evidence and provider ID/revision, environment, digest and scope drift.
- These are **observed compatibility results**, not independently justified
  authorization decisions. In particular the inherited host permits some
  THIRD_PARTY/HOST_UID_NAMESPACE combinations when callers supply the
  ADVERSARIAL_ISOLATION claim. That is not evidence of actual adversarial isolation
  and must not be used to activate a provider. The root's separate security-policy
  disposition remains required; this bridge must not silently fix or endorse it.
- Identity classifications freeze approved B1b scalar/Cc/fixed-whitespace/bounds
  behavior over five construction paths, including all 2,048 individual surrogate
  units, valid supplementary scalars, NFC/NFD preservation, and boundary lengths.
- State observations include reachable revisions, forbidden/stale transitions,
  every durable identity-field drift, recovery and exact peer/readback matching.
- `LogicalContractTest` independently checks collection defensive copies,
  unmodifiable views, value equality/hash consistency and immutable transitions.
  It prints only the check count, never identity/enum hash values.
- Null constructor arguments/members are classified where explicitly prohibited.
  Optional collection-query semantics such as `contains(null)` are not frozen.
- No reflection, journal/filesystem/provider code, Android API, serialization,
  production attestation verification, or historical storage activation is covered.
  Claim-bearing evidence objects are inputs to a policy, not a verifier.

Changing a fixture requires a separately reviewed semantic reason. Port mismatch
is not itself justification to change the expected output. The harness uses only
public APIs and deterministic keys, enum names and exception categories; no
exception messages, collection iteration order or object identity hashes appear.

Archived before approved B1c policy correction. Source manifest resolves relative to `spec/bridge-oracle/b1b/source/`; archived harness here preserves observed D3 behavior in a separate classpath only. Desired B2 targets a new corrected contract. Root approved D3 correction in docs/REBUILD_POLICY_D3_DECISION.md; unsafe old permits are not requirements.

## Byte-preserving publication transport

The logical `observations.txt` bytes described above are now stored as `observations.txt.gz.b64` (canonical base64 of deterministic gzip). The decoded SHA/row count/byte size are unchanged. `../transport-manifest.json` pins encoded and decoded provenance; `rebuild-tools/decode_logical_fixture.py` verifies all bounds, gzip integrity/exact end and identities before output. Tests compare decoded temporary bytes; CI never regenerates fixtures. Original plain bytes remain in git checkpoint `bd5b052a1f047f090ce5daa9fcdd97189d6205d5`. See `docs/REBUILD_FIXTURE_TRANSPORT.md`.
