# E13 — Android native A/B file and SQLite routing proof (pre-code plan)

Date: 2026-10-28. Branch `phase/e13-android-native-ab-proof-20261028`, based on E12 source snapshot `eda5f66bc8c8b3c9ee6f59ee3b1053bd7565b73f`; E12 emulator gate is separately pending, not assumed successful. Keep `main@c055df6`, E10/E11 and E12 branch history untouched.

## Read back the master plan and prerequisites

The [8-phase/32-step roadmap](../ROADMAP.md), [execution plan](../EXECUTION_PLAN.md), and [current status](../STATUS.md) still require **two independent clone data regions**, preservation of original app data, crash/restart handling, and real Android evidence before any account login. E11 [Android emulator PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977) verified private DEX/Resources loading for one synthetic APK. E12 is testing *actual synthetic bytecode execution*. The E5 Linux host proof had two-process native routing, but E6 **has no successful Android file/SQLite receipt**. The current `native-runtime-probe` already contains synthetic `MainActivity` and `ProbeService.A/B` designed for that exact proof. `scripts/run_native_probe.py` expects the obsolete manifest shape (one Activity/two services) and uses `-accel off`, which prevented useful results in the old no-KVM environment. Reuse the working E11 GitHub-owned KVM AVD runner instead of weakening checks.

## Work for this slice

1. Keep the original `native-runtime-probe` two independent services and file/SQLite fixtures; do NOT open third-party apps or access accounts. Harden receipts to include `uid`, `pid`, distinct synthetic A/B labels, and parent host process validation. Explicitly document all are **same Android UID**, despite distinct process names, and do not claim kernel sandbox.
2. Reuse `run_guest_code_emulator.py` with new `--proof-level e13`; build and sign fresh probe APK, boot a fresh owned Android 34 AOSP x86_64 KVM emulator, install only synthetic APKs, launch `.MainActivity` with an externally chosen UUID run ID. Poll only `files/native-probe/latest.json` through serial-qualified `run-as`. Require exact run UUID; no stale PASS.
3. For a PASS, require two independent child PIDs, both native hooks ENABLED, two distinct A/B markers/files/SQLite readback, original marker and database unchanged, and the host main process remains unhooked. Never treat launcher success, a file existing, or one passing child as a successful clone.
4. Keep the E11/E12 proof variants unchanged and add only explicit e13 acceptance; continue to validate signed APK hashes, AVD ownership, bounded timeout, and sanitized crash logs.
5. Add dedicated E13 GitHub workflow on this branch, pin SDK/toolchain and build/lint native probe. Store report + signed synthetic APK as short-retention artifacts.
6. If real Android hooks/file IO/SQLite fail, record `FAIL` with exact stage and sanitized Android log excerpt, fix the implementation without bypassing assertions, and rerun CI. No false claim of social-account separation.

## E13 acceptance gates and known limits

- **Build/test**: signed native probe APK, expected x86_64 ELF + lint pass and verified artifact hashes; core JVM tests if changed.
- **Android proof**: AOSP34 KVM boot and actual independent A/B processes with file/SQLite checks and original untouched, report PID/UID identity; no test uses real accounts.
- Even with a PASS, Android Java/SDK permissions, Binder, Application/Activity virtualization, system service mediation, full base+split guest execution, Android ARM64/Samsung, and social account isolation remain **NOT VERIFIED**. Two processes sharing the host UID do not prove full application sandbox security or arbitrary app cloning.
- All work is scoped to own GitHub branch and own synthetic emulator; no force push/merge/deployment/phone action.
