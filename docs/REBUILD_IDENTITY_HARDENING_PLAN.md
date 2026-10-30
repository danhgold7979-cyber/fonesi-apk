# B1b standalone identity hardening — approved contract

Prerequisite: B1a remote `5f84af9a2b545ab7c3ad99f1b37cf70aa5dbc72d`, exact-head run **41117815149 SUCCESS**. This plan precedes implementation. Root approved the compatibility change for standalone kernel only.

## Contract
Provider ID, provider revision and environment must be non-null, nonempty well-formed Unicode scalar sequences, at most 256 UTF-16 units and 768 non-lossy standard UTF-8 bytes. Reject Cc U+0000–001F/U+007F–009F anywhere. Reject leading/trailing or all-whitespace using fixed U+0009–000D,0020,0085,00A0,1680,2000–200A,2028–2029,202F,205F,3000. Preserve exact accepted text (including case, NFC/NFD, internal non-control whitespace and Cf). No silent normalization or visually-blank promise. Bounds are identity-field budgets, not clone limits.

## Ordered implementation and evidence
1. Preserve byte-identical four v1 sources in a historical fixture archive; keep v1 hash manifest and 99 observations unchanged. Run historical oracle in isolated classpath, never link it into current production.
2. Add one deterministic shared validator for all identity entry points; retain valid journal v2 encoding and scope hashing. New identity contract revision is B1b, not a journal format bump.
3. Test every boundary/scalar/control/whitespace class through provider ID/revision and both capability and operation environment entry points. Pin valid old Unicode/hash/codec parity separately from unsafe observations.
4. Test checksummed old invalid journal input is rejected on decode/read/recovery/inspection, bytes retained unchanged and no invalid-record observer delivery or provider dispatch. No migration/remapping/deletion. Rejection is quarantine-required evidence, not implementation of an Android quarantine store.
5. Run original suites, immutable legacy oracle, new tests and independent review. Root publishes; exact-head hosted CI required afterward.

## Exclusions / stop conditions
No Android imports/bridge, provider integration, real guest data, dependencies, source removal or device work. Stop on unexplained valid-byte drift or ambiguity; unsafe historical acceptance is not an acceptance requirement. Contract guards are not an isolation guarantee.
