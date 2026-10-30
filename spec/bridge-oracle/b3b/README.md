# B3b canonical-input validation revision (wire format remains v2)

Root approved strict canonical re-encode equality after green B3a remote f776841b/run41137171301; pre-code plan d2b31a9. Five current source hashes pinned here; only OperationJournal changes from B1c.

Private decoder checks existing36..262144 length budget, copies input into an owned snapshot, performs checksum + existing semantic parse, and requires exact encoder bytes before returning. Noncanonical modifiedUTF or peer order now throws IOException without rewriting caller/persisted bytes. Publicread expectedUUID binding unchanged. This tightens admissible v2 input; emitted canonical v2 format/hash is unchanged. Historical noncanonical files may now be rejected, retained for review/quarantine, never rewritten/remapped/deleted/replayed.

495 desired tests cover unchanged canonicalASCII/supplementary/state/4096peer encoding, all15observed D4 variants rejecting, unchanged arrays/files, inspect/recovery/dispatcher refusal with zero external callbacks, bounds and UUID binding. Other identity/policy/lease/uncertainty suites remain. Owned-copy-before-checksum ordering is directly inspected source; post-return mutation tests establish returned-value independence, not a proof of all concurrent caller scheduling.

All B1c sources and B3a harness/408observations are preserved byte-identically on a separate explicit five-file compile list/classpath. Unsafe historical acceptance remains historical-only. B2 logical source/pins unchanged; no Java17codec port, Android, storage adapter, provider or extra cleanup in this checkpoint. Host review/publication/exact CI required before pure port.

Independent review confirms source/ownership/canonicalization boundary and archive provenance. Zero observer callbacks is specific to the single-invalid-record test fixture; streaming inspection may have delivered earlier valid records before reaching invalid history. Invalid records are never delivered, and failed scan cannot reach provider/preflight/mutation/readback callbacks. Existing mixed-history tests retain this distinction.
