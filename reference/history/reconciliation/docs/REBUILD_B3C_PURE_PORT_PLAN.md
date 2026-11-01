# B3c final pure codec/key port proposal — root review before implementation

Date2026-10-30 22:37UTC. Host B3b exact published `8b7fc87e9b0881e4d5078c3455c28f425245430f`, tree7267243d193292aed0a4403b92f77de7f698327d, run41138979576 SUCCESS. Actual hosted495B3b desired tests and343B3a/408historical observations pass on separate source/classpaths; full kernel/logical/storage/Android/root-refusal gates green. D4 canonical-admissibility decision is now approved and implemented in host; no ambiguity is silently inherited.

## Proposed production API (two classes only)

In existing independent `vn.fonesi.rebuild.logical` Java17 module:

```java
public final class OperationCodec {
    public static byte[] encode(OperationProtocol.Operation operation) throws IOException;
    public static OperationProtocol.Operation decode(byte[] input, UUID expectedOperation) throws IOException;
}
public final class ScopeKey {
    public static String forIdentity(OperationProtocol.Identity identity);
}
```

No public stream/path/file API, stateful adapter, lock, callback, journal/dispatcher/auth/provider interface or Android import. No new dependencies, permission, signing material or legacy deletion. Logical byte[] output is a fresh value, not a persisted/verified operation.

## Fixed contracts

- Encode exact existing v2 field order/big-endian UUID/primitive layout, Java modifiedUTF length+bytes, signedUUID.compareTo sorted peers,32rawSHA256 suffix. Use defensive copied list/Collections.sort, compatible byte-array write(offset,length), manual lowercasehex. No formatversionbump, normalization or newfields.
- Decode requires non-null input and expectedUUID (programmer errors:NPE). Reject input length outside36..262144 withIOException **before cloning**. Clone bounded array once, then checksum and semantic parse from ownedbytes, enforce1..4096peer budget/remaining-byte feasibility/duplicates/targetpresence/identity/scope/state/revision/trailingbytes, re-encode and require exactownedbytes equality; then require expectedUUID equality before return. Unknown/unbound versions reject, never infer or repair scope. Malformedinput IOException family matches host; diagnostics contain no rawfield/record/secret data.
- The owned copy is the sole parse/checksum/canonical comparison source. A caller concurrently mutating its original array cannot make subsequent checks consult a different caller buffer. Snapshot timing is not transaction/authenticity proof. No returned collection/bytearray aliases input or mutable internal state.
- ScopeKey uses validated Identity only: providerID UTF16length + ':' + exactproviderID + ':' + package, standardUTF8 SHA256 lowercasehex. Providerrevision intentionally omitted; no UUID/env/scope/cluster addition. This preserves existing lease namespace, not actual locking/ownership. D1scalar/Cc/whitespace and D3capability conditions remain unchanged.
- Enforce existing byte/peer operational budgets, not clone-count product caps. No plaintextpassword/token/cookie/session/content fields; current metadata is still privacy-relevant and checksum/hash provides no encryption/authentication.

## Desired fixture scope and adapters

Freeze **new B3c desired vectors against corrected B3b host**, never overwrite B3a408historical acceptance rows or B2fixtures. Pin correctedhost five-source manifest plus portable source manifests and test-harness provenance. Keep outputs compact: recipes/inputSHA + exact canonical hex only for small representative payloads, digest/count for4096peer cases; no repeatedmegabyte blobs. If >32KiB, use separately pinned bounded transport rather than weakening decodedcomparison.

A shared deterministic test case generator runs through two **test-only** adapters: Java21 adapter invokes correctedhost privatecodec by reflection and public-read-equivalent size/expectedUUID contract; Java17 adapter invokes proposed publicpuremethods directly. Testpackage rewrite/classpaths remain separate; Java17 never loadsJava21 bytecode. Host scope-directory oracle, if invoked, uses only fresh synthetic temp metadata and cleanup as existing B3a; no productionfilesystemadapter added. Cross-process comparison must validate encodedbytes/hash and logicalidentity, not just equal exception names or toString ordering.

Required independent assertions in addition to parity:

1. KnownASCII and supplementaryUnicode modifiedUTF bytes; all reachable states; signedMSB/LSB UUID boundaries and deterministic order; maxidentity and4096peer budgets; validcanonical sourcebytes exactly re-encode and preserve currenthost scopehash/revisionomission.
2. Every15observed D4overlong/order variant now rejects(IOException), plus raw/modifiedNUL,unpairedsurrogate/control/whitespace,invalidcontinuations/four-byte standardUTF8,declaredUTF overflow,valid-checksum badmagic/v1/v3/scope/state/revision/package/digest/count/duplicate/missingclone/trailingbytes.
3. Every truncation of representativecanonicalpayload, correctlysealed size boundaries35/36/262144/262145,expectedUUID mismatch. Distinguish outerbudget failure from checksum/semantic failure; no false “valid maximum envelope” claim from paddedbytes.
4. Null programmer inputs,immutable decodedvalues,defensive peer copies,mutated callerinput afterdecode,mutated returnedencodebytes beforefreshencode; directsource review verifies clonebeforeallchecks. Do not claim deterministic concurrencyproof from post-return mutation tests.
5. Ensure failure returns no partialoperation and no callback/file mutation exists by API shape/sourcechecks. Existing host retention/no-replay495tests remain necessary but are not rebranded portablefilesystemproof.

Parity taxonomy: preserve IOException/EOFException/UTFDataFormatException/NPE distinctions where correctedhost publiccontract produces them; do not freeze platform-specific error messages. The explicit noncanonical and expectedUUID failures are IOException. Any unexplained subtype/validbyte/value drift stops work for review, never auto-updatefixture.

## API26 / toolchain gate

Use previously reviewed DataInput/OutputStream,ByteArrayInput/OutputStream,MessageDigest,UUID/Collections/Objects/StandardCharsets APIs; audit actual selectedmethods against primaryAndroidreferences before freeze. Permit a narrow in-memory java.io allowlist in source tripwires, still forbid File/Path/channels/network/reflection/threads in production. No HexFormat/Stream.toList/ByteArrayOutputStream.writeBytes/collectionfactories/records. Actual installedJDK17 `--release17 -Xlint:all -Werror` and runtimecomparison mandatory; localstrippedJDK21fallback is not acceptance. Module remains unimported by Android, so currentAPKcompile is regression-only, not D8/API26execution ofcodec.

## Acceptance and stop boundary

Pre-code approval→pinnedcase plan→implementation/differential+independent tests→independentreview→rootpublication→exactheadfullCI. Complete only the purebyte/key compatibility gate. No Android wiring, filesystemdurability, migration, realauth/provider/readback, source replacement or guestisolation acceptance. Unexplaineddrift or unexpectedhostacceptance pauses affected work. Root must approve this finalscope before code; no productionport exists at this checkpoint.

## Approval recorded before implementation

2026-10-30 22:42 UTC: root approved exactly the two proposed pure classes, corrected B3b owned/bounded canonical validation and UUID binding, precise exception-family parity, new compact desired fixtures, independent assertions and cross-JDK comparison. Historical B3a/B2 fixtures remain unchanged. No Android, filesystem adapter, journal, dispatcher, provider or dependency activation. Actual JDK17 CI and independent review remain acceptance gates. Unexplained drift stops work rather than updating expected fixtures automatically.

## Implementation sequencing after D5 review

B3c draft review found D5's inherited package-regex-before-length ordering. Root approved a separate bounded defensive correction, committed e78e722/treeab4ddb1 and published b9b1a6a. D5 historical sources/old manifests are preserved. Root then approved continuation of this same B3c scope against corrected D5 host; no production surface expanded beyond the original two pure classes. The separately denied malformed-envelope verification remains unavailable and is not retried or included in B3c acceptance claims. See REBUILD_PACKAGE_BOUNDS_DEFECT.md.

The new342row desired fixture remains byte-identical across B3b→D5 host comparison, rather than being regenerated after the correction. Portable generic semantic-value IOException intentionally omits raw enum cause text by root approval; only top-level failure-family parity is required. No exception-chain parity or denied test result is asserted. Actual installedJDK17 and exact-headCI remain required after local review.
