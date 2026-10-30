# B2 desired logical contract after approved B1c

Host correction `7e208b1`, root approval `c920569`; B2 pre-code scope approval `2caaea6`. Three host source hashes and four portable-source hashes are pinned in separate manifests, checked before compilation. Production host files are never rewritten. Only test package declarations are changed into temporary source folders for isolated host/portable processes.

17,317 deterministic observations SHA256:
`5524e281c11e827f6e22dfe62c21c78b1a51f2ef79e66a46248dd4ae62cca78a`.
Initial generation is reviewed local developer work; CI compares frozen bytes and never regenerates them. Against archived B1b observations, exactly **1,536 policy rows** change to `false:ISOLATION_MODE_UNSUPPORTED`: 96 previously permitted rows now denied;576 previous inconsistent-isolation,528 missing-claim,255 import-proof,63 execution-proof and18 adversarial-proof denial reasons change under the explicitly approved precedence. No non-policy observation changes, no new permit is introduced. Old bytes/harness/source remain under `../b1b-observed` and `../../bridge-oracle/b1b/source`, isolated from desired acceptance.

Coverage:6,144 policy combinations; exhaustive unpaired surrogates across five identity entries; Cc/fixed whitespace/scalar/length boundaries; exact identity-field drift, state/revision transitions, readback peer coverage and rejection classes. 70 independent value/hash/immutability checks and6,200 independent desired D3 truth-table checks run on both host and portable implementations. Differential parity alone is not policy correctness.

Enum identity hashes and collection iteration/toString ordering are not frozen cross-process contracts. Record-style zero-seed hash composition is independently asserted per runtime; provider exact text preserved. Optional null-query behavior on collections is not an identity constructor acceptance rule; null sources/elements/keys/values still reject.

Mode allowlisting is necessary, not attestation; capability objects do not authorize a real provider. This source set has no Android imports/app wiring/filesystem/codec/dispatcher/auth callbacks. JDK17 hosted execution remains required; local stripped-JDK21 fallback is only behavior evidence. API26 audit and eventual Android D8/runtime acceptance are separate gates. Invalid historical records stay retained/quarantined, never remapped or replayed by this port.

## Byte-preserving publication transport

The logical `observations.txt` bytes described above are now stored as `observations.txt.gz.b64` (canonical base64 of deterministic gzip). The decoded SHA/row count/byte size are unchanged. `../transport-manifest.json` pins encoded and decoded provenance; `rebuild-tools/decode_logical_fixture.py` verifies all bounds, gzip integrity/exact end and identities before output. Tests compare decoded temporary bytes; CI never regenerates fixtures. Original plain bytes remain in git checkpoint `bd5b052a1f047f090ce5daa9fcdd97189d6205d5`. See `docs/REBUILD_FIXTURE_TRANSPORT.md`.
