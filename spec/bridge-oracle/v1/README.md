# B1a Java21 oracle observations — not portable acceptance approval

Production source frozen unchanged from dispatcher checkpoint `fcfc84fc4a3255f20e6f52026f4c0a632af00785` (still identical at B1a plan commit `9b1eddc`). `source-sha256.txt` pins exactly four production files. `observations.txt` is deterministic synthetic test output, SHA256:

`08d278b8523d156b4ceeca11f7ce3de789d264c517e2425a8d7d4f3ea0c1becf`

Generated on local Java21.0.12.1 with existing compiler, no new dependencies. The test harness invokes the unchanged private codec and scope-directory function via reflection in host tests only. Scope/read cases create and delete only fresh `fonesi-oracle-synthetic-*` temporary directories. No runtime provider, guest APK, Android app import or user database is involved.

CI runs `BridgeOracleTest` with no arguments: verify source hashes, compute observations, compare committed fixture. It never updates files. `--emit-observations` is an explicit developer-only stdout diagnostic; changing golden expectations requires defect review, not an automatic “update tests” action. The fixture itself contains no physical-device or provider evidence.

## Observation vs desired contract

All factory classifications say `ACCEPTED_OBSERVATION_ONLY` rather than PASS. Exact historical behavior may be wrong. The test protects evidence from accidental drift; it does **not** require a future portable bridge to accept every observed input. See [defect ledger](../../../docs/REBUILD_BRIDGE_ORACLE_REVIEW.md). Unsupported/unsafe rows must not become bridge positive fixtures by default.

- Safe-input compatibility candidates: fixed ASCII/supplementary-Unicode identities, all reachable state/revision pairs, deterministic v2 envelope bytes/digest, signed high-bit UUID ordering, deliberate provider-revision-independent lease namespace.
- Reject candidates: invalid state/revision, missing required fields/peers, ASCII blank/em-space identity, corrupt/truncated/oversized/unbound/unknown-version public journal input.
- **Defect/undecided observations:** malformed surrogates alias each other and ordinary valid `p?` scope namespace despite distinct journal bytes; embedded NUL and NBSP-only identity acceptance. Retained to reproduce and review—not approved as valid bridge input.

State construction alone is not authorization; VERIFIED value construction does not permit journal CREATE or provider dispatch. Codec/private-method observations are distinct from public journal validation. Negative journal cases exercise public `read` so its length/file/identity checks are included.
