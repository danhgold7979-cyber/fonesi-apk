# B3a ambiguity findings and desired-contract decision — 2026-10-30 22:03 UTC

## Observed, not assumed

Unchanged corrected B1c host source pinned by five hashes in `spec/codec-observations/b3a/source-sha256.txt`; real local Java21 test runner, public journal read boundary.408 deterministic compact observations. No production codec, host hardening or Android import added.

| Case | Actual public-read behavior | Meaning |
|---|---|---|
| Canonical encoder output, supplementary Unicode, all state/revision pairs represented validly,4096peers | Accepted and re-encodes byte-identically | Valid compatibility candidate; serialization itself is not authorization. |
| First ASCII UTF code unit expanded to two-byte or three-byte overlong form, across providerID/revision/package/environment/scope/digest/state (14cases), correct checksum | Accepted; same logical record as canonical; re-encode differs | **D4 representation ambiguity/wire malleability.** Not a SHA collision, not a demonstrated permission/data/isolation bypass. |
| Same peer UUID set, reversed on-wire order, correct checksum | Accepted; same logical record; re-encode sorts and differs | Noncanonical ordering accepted. Set semantics erase wire order; significance must be decided, not silently copied. |
| RawNUL or modifiedUTF NUL, unpaired surrogate/NBSP identity, invalid/truncated continuation/four-byte standardUTF8 | Rejected (IOException or UTFDataFormatException as frozen) | B1b identity and decoder rejection remain; D1 is not reopened. |
| Duplicate/missingclone/count-invalid peers, badmagic/version/scope/state/package/digest/revision, trailingbytes, declaredUTF overflow, short/oversized/truncated input, expectedUUID mismatch | Rejected with frozen precise exception classes | Real publicread/outerbudget binding exercised, not just private parser or bad checksum. |

Every publicread case verifies bytes unchanged. Scope-key checks independently confirm exact UTF16length framing, standardUTF8 digest and revision omission. Temporary metadata tests do not authorize filesystem use in a portable module. SHA checksum remains unauthenticated corruption detection; no claim attacker-controlled bytes are trustworthy.

## Proposed desired canonical rule — NOT IMPLEMENTED / awaiting root review

For new pure codec and, if root separately approves, host publicdecode: only accept an envelope if ordinary bounded/checksummed semantic parse succeeds **and canonical encode(parsedOperation) equals the exact input bytes**. This rejects overlong modifiedUTF and reordered peers without normalization, mutation or repair. Keep valid canonical v2 bytes and scope hashes unchanged; preserve B1b scalar/Cc/whitespace and B1c capability rules. ExpectedUUID must match before return. Exact exception class for invalid envelope remains IOException; canonicalization is a rejection test, not a persistence write.

Wire version stays2 because emitted format does not change; admissible input becomes stricter under an explicitly recorded new validation revision. Root must approve this compatibility decision rather than letting a test update choose it implicitly. Old noncanonical/unrecognized records retain their bytes and require review/quarantine; never remap, rewrite, delete, migrate or dispatch. A file previously accepted can now be rejected; this must be documented even though current canonical encoder never emits those variants.

Alternative: explicitly allow semantic-equivalent noncanonical inputs. That requires a reviewed rule for signed/checksummed byte identity and future ownership/receipt comparisons; it is not automatically safe or required merely because DataInputStream/read-set currently accepts it. Recommend strict canonical rejection for the new boundary, subject to root decision.

## Provenance and sequencing if approved

1. Freeze this test-only checkpoint and observe exact-head hosted CI; retain observedD4 positive rows as historical-only.
2. Before any host edit, archive/pin unchanged B1c codec sources and harness/fixture for isolated historical replay. Do not regenerate their unsafe observations as new desired vectors.
3. Separate approved host validation correction, independent canonical/bounds/immutability/no-replay tests, valid-byte/hash parity and source-version provenance. Unexpected unrelated drift stops work.
4. Only then separately implement approved in-memory Java17 codec/key module with differential plus independent tests, existing budget/expectedUUID binding, no filesystem/journal/dispatcher/provider/Android wiring. Root reviews/publishes each checkpoint.

This proposal is not acceptance of B3 implementation, Android durability, provider feasibility or further legacy cleanup. R0/R2 retain their bounded acceptance; no product percentage follows from additional observations.

## Independent review corrections before freeze

Separate the journal.read exception classification from reflective re-encoding: a successful read followed by encode failure now fails the harness, never mislabeled rejected. Original truncation/padded cases also fail checksum; additional correctly sealed35/36/262144/262145byte cases distinguish actual outer length rejection (35/262145) from EOF(36) and trailing-body rejection(262144). No valid262144byte envelope is claimed. Final343checks/408observations remain under32KiB; two fresh JVM emissions match.

## Approved B3b disposition (host checkpoint)

Root approved strict canonical rejection after exact B3a CI41137171301 success; pre-code plan d2b31a9. Current host now bounds/copies caller input, verifies semantic/checksum and compares canonical re-encode to that owned snapshot. Historical B1c sources/B3a harness/observations remain byte-identical and isolated. Canonical bytes remain unchanged; invalid originals retained, no rewriting/replay. PureJava17codec implementation still awaits separately reviewed/published host checkpoint. Independent review found no blocker; exact new-head CI pending.
