# B1c D3 necessary isolation-mode correction

Root approval before code: `c920569`, detailed decision `docs/REBUILD_POLICY_D3_DECISION.md`. `source-sha256.txt` pins current five host files. Only CapabilityPolicy changes from B1b: identity/scope/cluster precedence retained, then exact strong-mode allowlist is necessary for ADV evidence/request or protected THIRD_PARTY operation. New denial reason ISOLATION_MODE_UNSUPPORTED is intentional reviewed compatibility change. Mode labels/claims are not attestation or runtime authorization.

B1b five source bytes and original manifest preserved under `../b1b/source`; D3 observations/harness in `../../logical-bridge/b1b-observed` run on that isolated source only. Earlier v1 observations remain unchanged. Current identity/codec/hash tests remain unchanged; no filesystem/provider/Android implementation added. Independent PolicyModeTest asserts revised truth table separately from differential parity.

B3b preservation update: unchanged B1c manifest resolves relative to archived `source/` here. All five files match exact pre-hardening bytes. B3a harness runs against this archive in an isolated classpath/temp fixture-root layout. Current production pins move to `../b3b/`; historical D4 acceptances remain evidence only.
