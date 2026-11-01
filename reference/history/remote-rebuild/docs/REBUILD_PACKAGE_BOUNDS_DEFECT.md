# D5 package validation work budget — blocking B3c review finding

2026-10-30 22:47 UTC. **Initial finding; later approval and bounded correction evidence are recorded below.**

Independent B3c review found an inherited denial-of-service/error-family defect: package validation evaluates the recursive repeated-segment regex before its existing 255-UTF16 limit. A synthetic dotted package `"a.".repeat(2048) + "a"` (4097 UTF16 units) can throw StackOverflowError rather than IllegalArgumentException. The value fits a modified-UTF field and bounded v2 envelope. The exact failure threshold is runtime/stack dependent, not a product limit or portable assertion. This is a malformed-input resource/exception-boundary problem, not guest isolation or authorization bypass evidence.

Affected current source checks:
- host `OperationProtocol.Identity` package validation;
- host `SharedUpdateDispatcher.Request` package validation;
- portable `OperationProtocol.Identity` package validation.

The fixed-count `{64}` digest patterns are not this repeated-segment regex. B3c checksummed-envelope confirmation is being independently recorded. Existing 342 draft parity vectors/731 checks pass but do not cover this issue; they are not grounds to accept or publish the pure port. StackOverflowError must never become a desired golden exception.

## Proposed narrow correction for root approval

1. Preserve current source hashes/history and byte-identical prior host/portable source archive, plus clearly historical-only finding evidence. Keep existing B3a/B2 fixtures unchanged.
2. At exactly the three checks above, evaluate null then `length() > 255` **before** the same existing regex. Do not change accepted grammar, length, normalization, format version or operation authorization. Avoid catching Error as a substitute for bounded validation.
3. Test constructor and dispatcher Request rejection at 256/257/4097/65535 synthetic package lengths and valid 255 boundary. Test checksummed codec decode returns IOException, with unchanged input bytes and no partial record. Public host read retains exact invalid bytes; no provider callback or dispatch. Include a high-segment valid255 string and representative invalid grammar to preserve semantics.
4. Keep old valid canonical bytes/hashes and all B2 desired values identical. Record new current source manifests rather than overwrite historical pinned manifests; route historical verification to retained source and desired current comparisons to new pins. No fixture may silently redefine the old StackOverflow behavior as acceptable.
5. Separate plan/host correction review and publication checkpoint before declaring B3c accepted. Root chooses exact sequencing; no new Android, file adapter, provider operation or legacy cleanup scope.

## Separate diagnostic observation

Host semantic decode wraps IllegalArgumentException as an IOException cause, which may contain raw malformed enum text. Portable B3c omits this cause while keeping top-level IOException/generic message; root was notified. This diagnostic issue is independent of D5's validation-work ordering.

### Confirmation limitation

The independent reviewer reported the direct constructor failure and exact source ordering. Its subsequent request to confirm a checksummed-envelope reproduction was blocked by platform cybersecurity screening. That confirmation was not completed and must not be represented as executed evidence. No retry/rephrasing or alternate reproduction route was used to bypass the restriction. Defensive source review and the proposed bounded validation ordering do not depend on claiming that denied experiment succeeded.

## Root approval — 2026-10-30 22:52 UTC, before correction

Approved exactly the three existing checks: null then length >255 before unchanged regex. Preserve historical sources/observations and valid bytes; publish a separate host checkpoint before B3c acceptance. Do not retry the denied malformed-envelope reproduction through any worker/tool. That verification remains unavailable, not an executed requirement. Only ordinary bounded constructor regression and source-order review are included here; no long malformed-envelope or long-string failure reproduction is performed. Portable generic IOException cause omission is separately approved as an intentional diagnostic difference, not exception-chain parity.

## Implemented bounded correction and review

Only the three approved source lines changed. A byte-identical nine-file historical host/portable source archive is retained under `spec/package-bounds/d5/historical-source`; old B3b/B2 manifests remain unchanged and are verified there, while new current manifests pin corrected code. All historical observation files remain unchanged.

New ordinary constructor checks cover valid255, invalid256/257, null and representative small invalid grammar:18host checks and9portable checks. A source-order test verifies exactly the approved expression reorder and byte equality of the rest of each affected source. Independent read-only review found no blocker and did not execute the denied experiment. Current B2 valid vectors and host canonical-byte suites remain regression gates. Separate B3c draft files are deliberately excluded from this checkpoint. Actual new-head JDK17/Android CI remains pending root publication; no malformed-envelope verification claim is made.
