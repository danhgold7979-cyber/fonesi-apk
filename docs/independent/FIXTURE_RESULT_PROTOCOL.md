# Owned fixture scalar text protocol — compile/source prerequisite

The accepted b82fe48b build remains immutable COMPILE evidence, not Android execution. Its Bundle arrays are unsuitable for exact text receipts: AOSP Instrument.java blob e0d949e04a921457ff8ae05286ce9a85144743ab prints `results.get(key)` directly. Arrays therefore do not serialize case/count values reliably.
Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/cmds/am/src/com/android/commands/am/Instrument.java

Replace fixture-only output with schema `owned-storage-fixture-text-v2`, fixed scalar case list `missing,fresh_read,commit_readback,stale,cancelled`, and five scalar integer keys `assertions_<case>`. Preserve exact target, source_sha and result. Failure remains FAIL with generic failure code; never parse it as success.

A pure HOST parser accepts at most 8192 bytes of strict UTF8 text, LF or CRLF lines, no blank/status/crash/unknown/trailing lines. It accepts exactly one occurrence of each required result key in arbitrary order (Bundle iteration is not case order), followed by exactly one terminal `INSTRUMENTATION_CODE: -1`. Exact target, schema, source SHA and case list must match; all five counts must be canonical positive ASCII decimal <=2147483647. Reject arrays, missing/duplicate/extra keys, wrong source, overflow, failed or truncated output. Return immutable parsed scalar data, not an ANDROID receipt.

Parser tests are owned synthetic strings only. Parser success is **not** runtime acceptance: real execution still needs authorized disposable environment, installed APK/source/signer identity, process transport/exit/timeout and fresh run/replay binding. No command execution, adb, installation, device provisioning or security flags are authorized here. Platform instrumentation defaults/flags require separate exact review before proposing an actual command; never relax hidden/test API, isolated-storage or signature enforcement to make tests pass.

Production app/core/SQL/UI/auth remain unchanged. The changed fixture classes require fresh independently verified fixture COMPILE evidence; prior b82 evidence cannot be reused as compilation of the new source.

Local evidence: 7 synthetic parser tests and 6 affected fixture source predicates passed; no Java/Android execution. The parser suite is mandatory once in HOST. Expected next aggregate is 180 Python cases / 27 commands, not yet observed. No runtime receipt is emitted.
