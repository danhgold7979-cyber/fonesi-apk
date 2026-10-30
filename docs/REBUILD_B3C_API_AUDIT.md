# B3c pure codec/key API review

2026-10-30. Scope: only new `OperationCodec` and `ScopeKey` in the independent logical module. Existing B2 API audit remains applicable to four prior-reviewed value/policy classes, with the separately approved D5 package-length ordering amendment. No Android source-set import or core-library desugaring was added.

| Selected calls | Official Android reference / minimum |
|---|---|
| DataInputStream construction, readInt/readLong/readUTF/available/close; DataOutputStream construction, writeInt/writeLong/writeUTF/close | [DataInputStream](https://developer.android.com/reference/java/io/DataInputStream), [DataOutputStream](https://developer.android.com/reference/java/io/DataOutputStream): API1. Modified UTF is deliberately distinct from standard UTF8. EOFException/UTFDataFormatException are preserved rather than flattened. |
| ByteArrayInputStream(byte[]); ByteArrayOutputStream(), toByteArray(), write(byte[],int,int) | [Input](https://developer.android.com/reference/java/io/ByteArrayInputStream), [Output](https://developer.android.com/reference/java/io/ByteArrayOutputStream): API1. The host's writeBytes(byte[]) is API33 and is **not** used. Streams only wrap bounded owned byte arrays, never files/sockets. |
| Arrays.copyOf(byte[],int), copyOfRange(byte[],int,int); equals(byte[],byte[]) | [Arrays](https://developer.android.com/reference/java/util/Arrays): copy methods API9, equality API1. No range-equality overload or newer helper. |
| MessageDigest.getInstance("SHA-256"), digest(byte[]), isEqual(byte[],byte[]) | [MessageDigest](https://developer.android.com/reference/java/security/MessageDigest): API1, SHA-256 supported API1+. Equality/checksum is not authentication. |
| StandardCharsets.UTF_8; String.getBytes(Charset), new String(char[]), charAt, length | [StandardCharsets](https://developer.android.com/reference/java/nio/charset/StandardCharsets): API19 constant; [String](https://developer.android.com/reference/java/lang/String): Charset overload API9. No charset default, Unicode normalization or locale case conversion. |
| ArrayList(Collection), Collections.sort(List), HashSet.add, UUID(long,long), getMost/LeastSignificantBits, compareTo | [Collections](https://developer.android.com/reference/java/util/Collections), [UUID](https://developer.android.com/reference/java/util/UUID): API1 surface. Natural signed UUID order, not string/unsigned order. |

Source uses ordinary classes, loops, try-with-resources and generic collections. Byte-array clone is performed after length bounds; no subsequent parse/checksum/equality consults caller input. Peer parsing is bounded by 4096 and by DataInputStream EOF checks against the owned body; no eager count shortcut changes host EOFException classification. No public stream interface, reflection, thread, path, file, adapter, dispatcher, permission or provider capability.

The narrow source tripwire now allows only the five named java.io imports in OperationCodec and StandardCharsets in ScopeKey. It continues forbidding other java.io/java.nio references, modern helpers, records, filesystem/thread/network names and Android wiring. This is a focused review aid, not a complete static analyzer.

Actual JDK17 compilation/runtime and cross-process vectors remain separate from this source-method audit. Android D8 packaging and API26 execution of these classes have **not** occurred: the existing app build deliberately does not import this module. Neither desktop compilation nor API documentation establishes runtime isolation, durability or guest compatibility.

## Diagnostic boundary

For malformed semantic values the portable codec preserves the host's top-level IOException and generic message, but omits the IllegalArgumentException cause: Enum.valueOf causes can embed attacker-controlled scope/state text. This does not change valid wire bytes or exception-family parity. The existing host diagnostic chain is recorded separately; no host source is changed as part of B3c. Other parser exceptions expose offsets/lengths, not decoded field text. No secret-bearing fields are added; metadata remains privacy-relevant.
