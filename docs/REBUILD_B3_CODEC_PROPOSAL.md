# B3 proposal: in-memory v2 codec and scope key — review before implementation

Date2026-10-30 21:52UTC. Prerequisite: R2 bounded standalone gate accepted after exact published dc19cdd57b41a97ce3787589815a104b136a41dc/run41133142565 SUCCESS; B2 Java17 logical values match corrected host. Proposal only: no codec implementation, filesystem adapter, Android source-set import or further cleanup performed.

## Bounded deliverable and source ownership

Add two dependency-free classes to the separate `vn.fonesi.rebuild.logical` module:

- `OperationCodec.encode(Operation)` → fresh `byte[]`, `decode(byte[], UUID expectedOperation)` → immutable operation. Only in-memory streams/collections/digest; required expected UUID retains the host public-read identity-binding obligation without pretending to validate a filename or path.
- `ScopeKey.forIdentity(Identity)` →64 lowercase hexadecimal characters. Accept the already validated identity, not a display string/path. Never create directories, acquire leases or authorize operations.

Proposed null programmer inputs reject with NullPointerException; malformed envelope/unknown enum/version/identity/state/budget/expected-UUID mismatch rejects with IOException without including raw fields or bytes in diagnostics. Specify and test this public boundary; do not emulate accidental exceptions from private host decode called with impossible-short arrays.

Keep Java21 host sources/manifests and historical v1/B1b/B1c provenance unchanged unless separately reviewed hardening becomes necessary. Port is honestly a compatibility adaptation, not a new engine or proof of durable persistence. No public stream/path/network API, no callback, no new dependency, permission, account field or Android import.

## Exact valid wire and namespace contract

Journal v2 body uses DataOutput big-endian primitives in this order: magic0x464f4e32; versionint2; operationUUID (signedlong MSB thenLSB); providerID, providerrevision, package, environment,scope.name via `writeUTF`; cloneUUID; lowercase64character clusterdigest via `writeUTF`; peer countint; sorted peerUUID pairs; state.name via `writeUTF`; revisionlong. Append exactly32 raw SHA-256 digest bytes of the preceding body. No field/version/reordering change.

- `writeUTF` is Java **modified UTF-8 with unsigned16-bit encoded-byte length**, not standard UTF-8. Supplementary scalars encode surrogate code units as two three-byte sequences. B1b rejects unpaired surrogates/control/NUL identity text; do not restore unsafe historical acceptances merely because modified UTF supports them. Standard UTF-8 identity bound768bytes is distinct from modified UTF byte count; existing256UTF16 budget also bounds modified UTF fields.
- Peer serialization uses Java UUID.compareTo signed-long ordering, **not string/unsigned byte order**. Port through copied `ArrayList` + `Collections.sort`, preserving caller collections. Include high-bit MSB/LSB boundary vectors.
- Scope preimage is `providerId.length()` in UTF-16 code units, ASCII `:`, exactproviderID, ASCII `:`, exactpackage; hash **standard UTF-8** bytes withSHA-256 and encode lowercase hex manually. Providerrevision deliberately omitted so upgrades cannot create a fresh lease namespace around unresolved work. OperationUUID/environment/scope/cluster/clone set likewise do not enter this existing namespace key. This is a key label, not proof of ownership or a lock.
- Same valid identity must yield byte-identical v2 output/hash as corrected host. NFC/NFD/case are preserved, not normalized. D1 invalid scalar aliases remain rejected before hashing; D3 policy stays intact but codec serialization itself never grants capability.

## Bounded decoder and negative cases

Carry existing host public read budget36..262144bytes and1..4096peers, not a product clone-count limit. Check input size before defensive snapshot/allocation; parse from a bounded copied buffer, verify checksum and version, reject trailing bytes, duplicate peers, missing target peer, wrong expectedoperation, malformed package/digest/provider/revision/environment/scope/state/reachable revision. Reject oversize/count arithmetic before peer allocation and enforce remaining-byte feasibility. Never return partial data, silently skip bad peers, repair, rewrite, dispatch, delete or migrate.

Tests cover every truncation of representative valid envelopes; valid-checksum malformed bodies (not only checksum errors); short/oversized boundary; negative/zero/4097/hugepeer counts; duplicate/missingclone; unknownv1/v3/magic/scope/state; all state/revision pairs; exactUUID mismatch; B1bidentity negatives injected into each relevant field; input/output mutation isolation; maximum validpeer/identity boundaries; supplementary Unicode and signedUUID sort. SHA-256 here detects corruption, **not authenticity/encryption**.

### Decoder ambiguity review before desired negatives are frozen

The current private host decoder delegates strings to `DataInputStream.readUTF` and inserts peerUUIDs into a set. Do **not assume** it enforces canonical modified-UTF byte encodings or sorted input order. Before port acceptance, characterize checksummed overlong encodings, rawNUL, invalidcontinuations, four-byte standardUTF8, unpairedsurrogates and reorderedpeer sequences against the current public-read contract. These are questions, not currently demonstrated exploits.

If any ambiguous/noncanonical inputs are accepted, freeze them as historical observations and stop the affected desired-classification decision. Root must review whether to reject noncanonical wire form (e.g. canonical re-encode equality) or explicitly allow safe semantic equivalence. Do not silently tighten the host, regenerate old goldens, or canonize unsafe acceptance in the port. Valid bytes emitted by current canonical encoder must retain parity. Unknown/version1 data always remains reject/retain/quarantine-required, never rebound or replayed.

## Evidence sequence

1. Pin corrected host codec/key source SHA plus B2 logical source provenance. Test-only host reflection may invoke private encode/decode; host public read length/expectedUUID behavior must be represented too. Prefer existing published vectors; any filesystem host-oracle call uses fresh synthetic temporary metadata only, neverproduction paths. New production classes remain filesystem-free.
2. Freeze compact reviewed valid-byte/hash and negative classifications with source/SHA/count manifests. Keep historicalunsafe categories separate; CI compares only. Reuse strict reversible fixture transport if needed, preserving decoded identity.
3. Implement API26-compatible in-memory helpers after contract review. Add exact and differential cross-process JDK21host/JDK17port tests, plus independently specified byte layout/bounds/rejection cases. Both new source methods must be tested independently of reflection/oracle assumptions.
4. Independent review of encoding, signed sort, hashframing, allocation/exception boundaries and API surface; exact-head hosted JDK17/full CI before acceptance. Source tripwires must permit only reviewed in-memory `java.io` classes, not remove all I/O exclusions indiscriminately.

## Preliminary API26 review (must re-audit actual code)

[DataInputStream](https://developer.android.com/reference/java/io/DataInputStream) and [DataOutputStream](https://developer.android.com/reference/java/io/DataOutputStream) provide the established modifiedUTF read/write primitive API; [MessageDigest](https://developer.android.com/reference/java/security/MessageDigest) suppliesSHA-256. [StandardCharsets.UTF_8](https://developer.android.com/reference/java/nio/charset/StandardCharsets) is available atAPI19. Use ByteArrayOutputStream.write(byte[],offset,length), not newerwriteBytes; copiedlist+Collections.sort, not Stream.toList; manual lowercasehex, not HexFormat. No java.nio.file/channels, File streams, reflection, records or newcollectionfactories in production.

Actual Java17 execution is necessary, not API26 or D8/runtime proof. Module remains outside Android build; scoped AndroidCI success does not compile/package this module. A future import requires its own exactD8/lint/packaging and eventually Android runtime review.

## Secrets and unresolved external gates

Wire fields are only existing operation/provider/package/environment/cluster/UUID/state metadata. Add no passwords, tokens, cookies, account/session content, user labels, original-appdata or raw APK contents. Existing metadata can still disclose app/provider/environment information: do not call serialization confidential, encrypted or universally secret-free. Never log entire records/decodedbytes in production errors. Hash namespace is not anonymization or authentication.

No ownership/rootbinding, fileKey/fsync/atomicrename/locks, CAS durability, journalrecovery, migration, auth/foreground/provider execution or guestisolation is implemented byB3. Those remain separate gates. StageA stays limited;16legacyentrypoints and oldsource remain untouched.

**Decision requested:** approve bounded B3 with an initial test-only ambiguity characterization/ledger step; approve desired rejection classifications only after its findings if host behavior differs. No implementation begins before root review.

## Root approval for B3a only — 2026-10-30 21:59 UTC, before test code

Root approved initial test-only pinned ambiguity characterization: valid canonical bytes/key parity, checksummed malformed modifiedUTF/peer ordering/duplicates/public-read bounds and expectedUUID, exact exception/no record mutation. Preserve all production source/provenance. Unsafe accepted observations require separate desired-contract review before any codec/host hardening. No production port/Android wiring approved yet. Freeze compact comparison fixture; independent review required.
