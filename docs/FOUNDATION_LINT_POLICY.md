# Finite foundation debug lint policy

Local implementation after independent design review; actual AGP/report/package execution of this batch is **NOT_RUN**. This is not a zero-warning or release acceptance claim.

Only `OldTargetApi` becomes a recorded informational notice, and only for the exact command tasks `:app:assembleDebug :app:lintDebug` with resolved min26/target36/compile36, AGP8.13.2 and Gradle8.14.6. Other task lists (including any release task) retain strict warnings-as-errors. Any pin/target/task/notice source change expires this policy and requires review. `AndroidGradlePluginVersion` is never exempted: the Gradle security maintenance was applied instead.

The original target36 is an explicit provisional compile baseline, not device support evidence. [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) supports API36.1 maximum with JDK17 and minimum Gradle8.13. [Current API minimum toolchain table](https://developer.android.com/build/releases/about-agp) requires AGP9.1.1+ for API37. Changing target silently would change Android behaviors and the approved foundation contract, not prove those behaviors tested. Reassess target and distribution requirements before any release; no store submission is authorized or accepted here.

All detectors continue running. `abortOnError` and `warningsAsErrors` remain true. No disabled checks, ignored warnings, baseline filtering or swallowed Gradle failures. TXT/XML/HTML are preserved and printed. The independent mandatory report gate requires exactly one `OldTargetApi` with exact XML severity `Information`, a recognized message, and a location resolving to the actual `targetSdk 36` line in app/build.gradle. Missing/duplicate/other diagnostics, unknown severity, wrong file/target, malformed/stale/missing/empty reports or a source-SHA mismatch fail. Prior reports are removed before the new build, and all three fresh reports are hashed into an exact-source receipt.

Synthetic failures cover ordinary/security warnings, unexpected informational findings, missing/duplicate notices, aliases, wrong source/target/message, stale/malformed/missing/hidden evidence and SHA mismatch. Source-contract checks cover exact debug task guard, resolved pin guards, and release task exclusion. These do not replace the required future actual Gradle/AGP run and binary resource verification.

HOST/COMPILE remain distinct from ANDROID/PROVIDER/PHONE. This finite policy never permits a provider, guest execution, device/account work or release signing.
