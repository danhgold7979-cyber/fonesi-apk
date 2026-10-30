# B2/B3c logical compatibility port — not an Android engine

Dependency-free, separate `vn.fonesi.rebuild.logical` Java17 source boundary. This is an explicit compatibility port of the pinned B1c host capability and B1b identity/state contracts, not newly invented provider functionality. Records are replaced by final value classes; newer collection factories by defensive null-rejecting immutable copies. B3c adds no further host changes; the separately approved D5 package-length ordering correction is preserved in both value implementations.

Run `./rebuild-logic/check.sh`. Hosted CI requires existing JDK21 and JDK17, compares independently compiled processes against reviewed frozen fixtures, and cannot regenerate them. Local fallback is JDK21 behavior evidence only, not Java17 runtime acceptance. Production has no filesystem/dispatcher/auth/provider callbacks, Android import or app source-set wiring.

**Trust boundary:** capability objects are caller-supplied data, not independent attestation. A permitted decision does not establish guest isolation. Historical B1b HOST_UID_NAMESPACE + asserted adversarial permits are archived as D3 defect evidence. Approved B1c now denies these modes for ADV claims/requests and protected third-party operations; stronger labels still are not proof. See fixture ledger and API audit. Persistence ownership, durable dispatch, verified provider/readback and runtime Android/OEM acceptance remain separate gates.

Collection element/key/value null rejection, immutability and value equality are required. Implementation-optional collection null-query behavior, iteration/toString ordering and enum identity hash numbers are not cross-runtime serialization contracts. Explicit value hash composition uses the host record-style zero-seed multiply31 algorithm; enum hashes remain process-local. No text/hash normalization added. The B3c pure codec validates canonical metadata envelopes in memory; it does not load, recover, rewrite or dispatch persisted records.

## B3c pure codec/key boundary

`OperationCodec.encode` emits unchanged canonical v2 bytes. `decode(input, expectedUUID)` bounds then owns input, validates checksum/semantic values/canonical re-encoding and UUID binding, and returns immutable data only. `ScopeKey.forIdentity` computes the existing provider/package namespace hash, with provider revision deliberately omitted; it acquires no lock. Neither API grants authority or proves persistence/isolation.

`check.sh` also invokes `check-codec.sh`: corrected host JDK21 and pure JDK17 processes compare reviewed frozen B3c desired vectors, while historical B3a observations stay separate. API26 method review is documented in `docs/REBUILD_B3C_API_AUDIT.md`; this module is not D8-packaged or executed by the current Android app. Diagnostic cause chains containing raw malformed enum fields are intentionally omitted in the portable semantic-value IOException, without changing its top-level family or generic message.
