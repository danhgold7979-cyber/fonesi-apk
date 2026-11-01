# B2 fixture transport compaction plan — 2026-10-30 21:15 UTC

Root authorized transport-only compaction after publication preparation interruption; remote was still B1b509d71ba, not B1c/B2. Local clean source checkpoint bd5b052/tree753ec0a. No assumption that fixture size caused the interruption; this reduces known API payload size and is not a contract fix.

Preserve exact existing decoded bytes (never regenerate oracle): historical1,129,640bytes and desired1,141,217bytes, each17,317 LF-terminated rows, hashes recorded in existing READMEs. Encode deterministic gzip(mtime0) as canonical single-line base64 ASCII plus LF for text-only publication. Plain files remain recoverable in prior git commits; new transport files have their own pinned SHA/byte sizes.

Python-standard-library decoder must bound encoded/compressed/decoded input, strictly validate base64 canonical form, gzip integrity/end-of-stream and reject concatenated members/trailing or truncated bytes. Verify exact decoded size/SHA/row count before writing any output. Runners decode to their fresh temporary output and compare actual oracle output exactly; no fixture update/regeneration path. Test malformed/truncated/extra/member/size/hash/count cases plus real roundtrip. Independent review and full existing suites required before publication. No production/API/Android/provider changes.

## Verified implementation

Encoded sizes are73,413 and73,477 bytes (146,890 combined versus2,270,857 plain bytes; roughly93.5% reduction). Gzip compressed sizes55,058 and55,105. Decoder caps encoded128KiB/compressed96KiB/decoded2MiB, uses an exact expected-size+1 decompression bound and rejects non-EOF/unconsumed/unused data. No concatenated member or trailing NUL tolerance. Transport SHA/length and original decoded SHA/size/17,317 rows verified before any stdout bytes. Metadata rejects duplicate/extra keys, invalid types/bounds and additional JSON content.

Both decoded files compared byte-for-byte against originals before plain paths were replaced. Prior commitbd5b052 preserves originals; source/identity/policy/codec code and goldens are not regenerated. Independent review found no correctness blocker; additional duplicate-key/object diagnostics incorporated.57 tooling tests (including21 transport cases), original kernel/history, logical70+6,200 per process/17,317 vectors, manager108/76 and51 SQLite pass locally. Hosted JDK17/full exact-head CI still required after root publication. Size reduction does not prove the earlier interruption's cause.
