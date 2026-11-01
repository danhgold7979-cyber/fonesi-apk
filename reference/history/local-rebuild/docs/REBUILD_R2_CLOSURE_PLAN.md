# R2 G1/G2 test-only closure — approved before code, 2026-10-30 21:35 UTC

Root approved bounded closure of detailed audit gaps, independently of provider/device gates. Production must remain byte-identical unless an actual defect is evidenced and reviewed.

G1: all25 ordered states, each constructed at its valid reachable revision before the CAS rejection boundary. Set up source durably through legal transitions; four allowed edges must persist/reopen,21 rejected pairs must leave record and bytes unchanged. Keep previous tests. New temporary synthetic roots only.

G2: peer providerID/revision and evidence providerID/revision/environment/cluster mismatch at each of three live preflight stages. Assert expected phase result, exact probe count and zero mutation/readback callbacks. Check no journal at stage1,CANCELLED atstage2,REVIEW_REQUIRED atstage3. Stage2/3 duplicateUUID and stage3 newUUID retries cannot enter external mutation/readback; retained journal unchanged. These are trusted callback simulations, not real auth verification.

Independent review + exact-head hosted CI must pass before R2 acceptance. Gradle future path must be labeled partial rather than falsely equivalent. No production/source removal/provider/Android change in this commit; StageA cleanup has a separate plan/commit.
