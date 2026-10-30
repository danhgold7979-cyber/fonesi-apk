# D3 policy defect and proposed correction — pending root approval

## Finding and stop

B2 full policy matrix exposed inherited host behavior: `HOST_UID_NAMESPACE` with caller-supplied `ADVERSARIAL_ISOLATION` plus dependency claims can permit THIRD_PARTY split execution, lifecycle and atomic update. This contradicts E35's exclusion of same-host-UID/folder namespace as adversarial isolation. These are compatibility observations only, **not safe authorization requirements**. Pause their use as desired B2 parity acceptance. No Android/provider module consumes this unfinished port. Host/B2 production policy correction is not yet implemented.

## Proposed B1c necessary-condition truth table

Define *strong candidate mode* as exactly `SEPARATE_OS_UID` or `SEPARATE_GUEST_OS`. Enum selection is necessary, never sufficient proof: neither supplied mode nor claims is attestation, and no provider is enabled.

After existing request/evidence null handling and exact provider/revision/environment/scope/cluster checks, before claim/dependency acceptance:

| Evidence has ADV claim OR request is ADV OR request is THIRD_PARTY {SPLIT_EXECUTION,GUEST_LIFECYCLE,ATOMIC_UPDATE} | Isolation UNKNOWN | HOST_UID_NAMESPACE | SEPARATE_OS_UID / SEPARATE_GUEST_OS |
|---|---|---|---|
| True | DENY | DENY | Continue existing claim/dependency proof checks; not automatically permit |
| False | Continue existing checks | Continue existing checks | Continue existing checks |

Propose stable new denial reason `ISOLATION_MODE_UNSUPPORTED` for this necessary-condition rejection. This explicitly supersedes the older inconsistent/unknown isolation reasons in overlapping cases; freeze revised reason precedence rather than hiding drift. Provider/environment/scope/cluster mismatch reasons still precede isolation. Missing claims/import prerequisites/execution prerequisites/adversarial proof remain separately denied after the mode check.

Preserve SYNTHETIC non-adversarial import/readback/split/lifecycle/update proof cases and exact identity bindings. THIRD_PARTY import/readback without an ADV claim is not newly classified as guest execution or isolation proof. A future enum must default-deny unless separately reviewed into the strong-mode allowlist. A strong-mode object without necessary ADV/dependency claims remains denied for protected third-party operations.

## Version and provenance proposal

1. Commit the D3 observation ledger and current B2 draft as **blocked compatibility evidence**, not completed bridge acceptance, if root wants a checkpoint. Do not publish unsafe parity as approved production.
2. Preserve unchanged B1b host source hashes and archived source bytes (three logical source files), current B2 observation fixture/hash and old B1a historical oracle. Never regenerate old unsafe observations into desired vectors.
3. Separate approved B1c host correction commit, with explicit version/denial precedence, exhaustive truth-table and negative cases. Keep unchanged identity/codec/hash and B1b identity tests; keep old compatibility oracle in an isolated historical classpath.
4. Apply same approved correction to unfinished B2 logical port; freeze **new desired B1c vectors** separately, differential host/Java17 parity plus independent truth-table assertions. Existing B2 unsafe vectors remain observation-only history, not active desired-contract target.
5. Independent review and exact hosted JDK17/full CI before completion; no Android, auth, storage, provider or device action follows automatically.

Root review requested for truth table, new denial reason/precedence and provenance layout before policy code changes. Independent B2 value/immutability and API review may continue while this decision is pending.

## Approval before host correction — 2026-10-30 20:09 UTC

Root approved the truth table and `ISOLATION_MODE_UNSUPPORTED` precedence exactly as above, after identity/scope/cluster checks; strong-mode exact allowlist is necessary, not attestation. Preserve synthetic non-ADV proof cases and remaining dependency denials. Archive B1b source and unsafe observations as historical-only; separate host correction commit, then align unfinished B2 desired vectors. Require independent truth-table tests, review and full JDK17 CI. No Android/provider/storage activation.
