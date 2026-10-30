# B1b desired identity contract provenance

Plan commit `1a80405`; immutable historical evidence checkpoint `e1e73c5` / remote `5f84af9a2b545ab7c3ad99f1b37cf70aa5dbc72d`, run41117815149 SUCCESS. Root explicitly approved this standalone identity compatibility change.

`source-sha256.txt` pins five current production sources and is checked before compilation. CapabilityPolicy and OperationProtocol now delegate to new IdentityText. Journal and dispatcher source/format v2 remain unchanged. B1b is an identity-validation revision, not a wire-format migration.

Desired classifications are executable `IdentityTextTest` cases, not regenerated v1 observations. Eight valid historical codec vectors (all five reachable states, high-bit UUIDs, literal question and supplementary Unicode) decode/re-encode byte-identically, preserve lease hashes and safe recovery semantics. Historical unsafe codec vectors and freshly checksummed invalid provider/revision/environment records in PREPARED/DISPATCHED states reject without external callbacks/replay or byte changes. Streaming journal inspection may deliver preceding valid rows before aborting; it never delivers an invalid row, and dispatcher never treats that incomplete scan as permission to proceed. Scalar tests include every one of the 2,048 unpaired surrogate code units; Cc, every fixed whitespace and length-boundary tests cover all five identity constructor entry points. Current desired suite: 11,471 checks.

V1 artifacts remain historical defect evidence on a separate classpath. They must never be packaged/imported as production. No portable Android bridge, real storage migration, quarantine persistence adapter, provider or guest isolation acceptance is implemented here.
