# E12 — Synthetic guest bytecode execution and process identity (pre-code plan)

Date: 2026-10-28. Branch: `phase/e12-synthetic-guest-execution-20261028`, based on validated E11 Android emulator commit `bc02df3ec8430da2515f6686bc9e661fe3ba43f9`. Baseline `main@c055df6` stays unchanged. This plan is a concrete implementation slice within the existing **8 phases / 32 tasks**, not a new completion claim.

## Master plan reconciliation (before work)

Reviewed `docs/ROADMAP.md`, `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, E10/E11 plans, `GuestCodeLoader`, `NativeRuntime` and the E11 emulator evidence. E11 CI [40640306977](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977) is green: an Android 34 AOSP x86_64 emulator booted, installed signed synthetic APKs, and loaded a private copied DEX class and resources in a dedicated named process. It did **not execute guest class business code**, create/attach a guest Application, virtualize Binder/services/providers, validate clone data isolation or test any social app.

The next minimally testable milestone is to prove **actual execution of guest bytecode** (rather than class lookup alone), with per-run challenge/response and proof of process identity, before moving to a bounded host↔guest Binder contract and Android lifecycle work.

## Scope and gates

1. Add a *pure Java, account-free* class `vn.fonesi.payload.ProbeEntrypoint` to the project-owned synthetic payload. Its deterministic method takes a caller-generated random challenge and returns an HMAC-free SHA-256 result with a domain separator and fixed marker; no network, files, credentials, profile or permission access. The API intentionally does not depend on Kotlin stdlib or Android framework.
2. In `GuestCodeProbeService`, after the existing signed private APK and resource proof, load the new class through `LoadedGuestCode.classLoader`, require that the defining class loader is exactly the private guest loader, and invoke the declared static method with a fresh challenge. Independently calculate the expected digest in the host probe and compare. Verify the class cannot be resolved by the host app's own class loader, to catch a false positive via packaging/parent loader. Do not call Android Activity/Application lifecycle.
3. Record bounded, non-sensitive evidence fields `guest_bytecode_executed`, `guest_process_verified`, `guest_process_pid`, `guest_uid_same_as_host`, keeping the original E11 denial flags for Activity/Application/Binder/account verification. Do **not** store the challenge, expected value, sensitive paths or APK/source account data. A process name is only a synthetic-process isolation boundary, not a different Android UID/kernel sandbox.
4. Update `scripts/run_guest_code_emulator.py` to require the two new positive fields only for the E12 proof and to reject if its result is an E11-only receipt. Verify the external ADB service process actually differs from host's process based on a reported private PID and a declared process name; since guest process may terminate after reporting, rely on server-side checks in code and strict receipt. No attempt to evade Android security or access third-party data.
5. Add a separate branch CI workflow that builds `native-runtime-probe` and `probe-payload` from source, verifies signatures, starts a new owned AVD, and runs the bounded probe. Do not reuse the prebuilt E11 APK hashes because source changed. Evidence and artifact must be from the same Git SHA.
6. Preserve E11 emulator runner safety: one owned AVD, serial-qualified ADB, strict run UUID and receipt verification, sanitised logcat, no account, no unapproved installation on a real phone, no force push or merge.

## Explicit acceptance criteria

- Kotlin/JVM compilation and APK build/lint pass on CI.
- AOSP Android 34 emulator boots and the APKs are installed with signature and file SHA checks.
- A fresh receipt, same E12 commit, contains `guest_bytecode_executed=true`, `guest_process_verified=true`, `private_resources=true`, `activation_pending=true`, while `guest_activity_executed=false`, `binder_provider_tested=false`, `social_accounts_tested=false`.
- If source/CI/boot/runtime fails, mark FAIL/BLOCKED with concrete evidence. Do not call a green build full engine completion. Follow-up E13: guest Application/Context and bounded component dispatch contracts, E14: Binder/intent/provider routing, E15: split/version activation/readback and physical device proof. Those are not completed by E12.

## Privacy, security and release boundary

No passwords, cookies, auth tokens, real data, third-party APK modifications or identity spoofing; no model/firmware gates removed; native hooks remain confined to the project-owned synthetic probe. Distinct process name is **not** distinct Android UID. `main`, original SpaceCore candidate and current clone database are untouched.
