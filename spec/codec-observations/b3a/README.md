# B3a pinned codec/key observations — not desired portable acceptance

Test-only approval before code: local3d9c5aa. Five source hashes match corrected B1c host, unchanged by this checkpoint. `CodecObservationTest` uses reflection only to the existing private encoder/key method; malformed/length/expectedUUID cases call **public OperationJournal.read**, including its outer bounds and UUID binding. All files are fresh synthetic temporary metadata; each read checks record bytes unchanged and test cleanup removes only that owned temporary root.

`observations.txt`:408 sorted observations,20,903 bytes, SHA256
`b59a9b9356ce5b62a193516d75bf720672324de1487cc2ec0413457761dbd7ea`.
Fixture comparison is bounded to32KiB. Two independent JVM emissions matched before freeze. CI runs default compare mode, never regenerates; developer-only `--emit-observations` outputs to stdout. No production source or existing historical fixture changed.

Canonical positives include independently constructed ASCII v2 envelope equality, supplementary modifiedUTF, all reachable states, signed-UUID peer order,4096 peers, exact scope hash framed UTF16length/standardUTF8 bytes and revision-independent key. Rejections cover rawNUL/modifiedUTF NUL identities, lone surrogates/NBSP-only, invalid UTF continuation/truncation/four-byte standardUTF8, unknownmagic/version/scope/state, invalidpackage/digest/revision, peers count/duplicates/missingclone, declaredUTF length overflow, every truncation of canonicalASCII, public length bounds and expectedUUID mismatch.

**Observed ambiguity (D4):** all14 overlong first-ASCII UTF encodings (two/three bytes across seven UTF fields) and reordered peer sequence are accepted, with identical logical value but different wire bytes from re-encoding. Accepted rows are explicitly ACCEPTED_OBSERVATION_ONLY; they are not portable positive requirements or proof of authentication/isolation. Other malformed cases reject with their exact observed exception class. See `docs/REBUILD_B3_AMBIGUITY_LEDGER.md` for proposed desired contract; root approval required before hardening/port.

After approved B3b hardening, byte-identical `CodecObservationTest.java` is archived beside this fixture. check.sh explicitly compiles only five pinned B1c production source files into a separate classpath and supplies the original expected filesystem layout in a fresh temporary root. Original source manifest/observations/harness are unchanged, not repurposed as desired current behavior. Current CanonicalJournalTest instead requires the15noncanonical variants to reject and retain originals.
