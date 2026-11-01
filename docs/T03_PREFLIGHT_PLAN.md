# T03 bounded update comparison — plan before source

## Scope and source

Fresh, independent HOST-only slice for APK-P0-T03; reads MASTER_PLAN, original T03 contract and P028/P041/P057/SEC-025/SUP-U05. It partially prepares version-display logic, never completes those product requirements. No legacy implementation/test was copied. No Android/provider/queue/IO/permission/dependency actions.

## Contract

Input: nullable source/target `Observation`, each containing a nonempty exact package label (max 255 UTF-16 units, no control characters, no unpaired surrogate, no leading/trailing whitespace), nonnegative long observed version and nonnegative long snapshot sequence. These values are caller-supplied observations, not cryptographic identity, ownership, signer, freshness attestation or provider readiness. Expected snapshot sequence is nonnegative. Package syntax is not an APK verifier; no package normalization or allowlist.

Output: immutable `Preview` with comparison MISSING_OBSERVATION, STALE_OBSERVATION, PACKAGE_MISMATCH, EQUAL_OBSERVED_VERSION, SOURCE_OBSERVED_NEWER or SOURCE_OBSERVED_OLDER. Missing takes precedence over stale; stale over mismatch; no numeric comparison before both pass. Every result is `PROVIDER_UNAVAILABLE`, `executable() == false`. Equal version means equal observed numbers, never "up to date", verified identity or eligibility. Newer version is not permission to update; older version is not permission to downgrade. No accepted-capability parameter exists.

Errors: invalid Observation construction or negative expected sequence throws IllegalArgumentException before creating a value. Null observations return MISSING without exception. Snapshot-sequence matching is a display consistency check only and can be fabricated by caller; it is not an execution fence. Fresh independent observations must be obtained by a future reviewed integration. No cached decisions, mutable collections or retained external handles.

Cancellation/race: evaluation has no side effects, external calls or long work to cancel. Dropping Preview changes nothing. A subsequent observation with changed sequence is stale against a previous expected sequence, and cannot gain executable status. Updating expected sequence still does not enable execution. Exact package mismatch reports mismatch without aliasing/normalization. Immutable Preview retains its earlier display-only comparison.

## Tests and acceptance

Compile only these two fresh files with `javac --release 17` under installed JDK21; run test main with explicit check helpers (not JVM assertions). Cover missing source/target/both, invalid package/version/sequence, null/empty/edge-space/control/broken Unicode, valid Unicode exact retention, equal/newer/older and Long.MAX_VALUE, stale source/target/both and precedence, mismatched packages, snapshot replacement, immutable final classes/fields and no writable collections. Check every preview remains disabled with fixed unavailable reason.

HOST pass applies only to this synthetic display comparison. COMPILE(Android), ANDROID, PROVIDER and PHONE remain NOT_RUN. No tests claim source signature, guest version, scope, safety, lifecycle or clone acceptance. Aggregate owner owns full checkpoint tests, status, commits and publication.

## Local execution result (not T03 acceptance)

Installed Java/module compiler is OpenJDK 21.0.12.1; no `javac` launcher. `java -m jdk.compiler/com.sun.tools.javac.Main --release 17 ...` exits 2: `release version 17 not supported`; this runtime image lacks usable release17 symbols. No download or toolchain modification performed.

HOST fallback executed:

```
out=$(mktemp -d /tmp/apk-preflight.XXXXXX)
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -Xlint:all,-options -Werror -d "$out" core/src/main/java/vn/fonesi/apk/foundation/UpdatePreflight.java core/src/test/java/vn/fonesi/apk/foundation/UpdatePreflightTest.java
java -cp "$out" vn.fonesi.apk.foundation.UpdatePreflightTest
```

Exit 0; 83 explicit checks PASS. `-Xlint:-options` excludes the expected system-modules/source17 mismatch warning; it does not supply Java17 API symbols, so this is **not release17 API compatibility evidence**. Separately, the same module compiler with `-Xlint:all -Werror` and default Java21 target also exits0 with 83 checks PASS, without warning exclusions. Full JDK17/`--release17` and Android compile remain aggregate-owner gates. No accepted requirement/task counts changed.
