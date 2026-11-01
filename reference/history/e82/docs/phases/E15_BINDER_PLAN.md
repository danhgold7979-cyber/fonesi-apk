# E15 — Internal synthetic Binder IPC and lifecycle handshake

Date: 2026-10-28. Branch: `phase/e15-internal-binder-channel-20261028`, based **exactly** on E14 Android-verified `b6731bd47f4310c7896a0b7ee070246846f368fe`. This plan is committed before source implementation.

## Master roadmap and last verified evidence

Checked `docs/ROADMAP.md` (8 phases / 32 tasks), `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, the private APK source component, existing probe app manifest/Gradle, E11–E14 workflows and reports.

- E11 signed private DEX/resources, Android 34 emulator: [CI 40640306977 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977).
- E12 actual **synthetic private guest bytecode**, dedicated process: [CI 40641518802 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641518802).
- E13 two separate native A/B synthetic process PIDs with file/SQLite routing, original unchanged: [CI 40641656320 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641656320).
- E14 bounded prototype `SyntheticGuestScopedContext`: **synthetic A/B logical data**, file/preferences/SQLite in real Android 34: [CI 40643847891 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40643847891).
- Still no real guest Android Application/Activity lifecycle, Binder interception/mediation, external app/Play Services, security isolation, multi-user account validation, update/restore or Samsung ARM64 proof. Prior ContextWrapper does **not** create an Android UID boundary.

## Problem and narrow scope

The existing Android probes use a started Service that writes a diagnostic file and exits. A real clone engine also requires bounded, authenticated **host↔guest-process IPC** with lifecycle/error propagation. E15 establishes only an **internal Binder transport and explicit host-to-guest synthetic lifecycle handshake**, with the component confined to the disposable test APK. It does NOT virtualize ActivityManager, PackageManager, Binder system services, ContentProviders or any third-party guest Activity.

## Implementation slices

1. Add `native-runtime-probe/src/main/aidl/vn/fonesi/nativeprobe/ISyntheticGuestChannel.aidl`: one versioned read-only `String exchange(String nonce)` for a 32-character lowercase hex nonce. Explicitly enable AIDL in that module's Gradle. Keep all generated code out of Git.
2. Add `SyntheticGuestChannelService` in named process `:fonesi_guest_channel`, `exported=false`. Accept Binder calls only from the host's own Android UID, require an exact declared process name on Android 28+ and API26 fallback only when it has an observed PID; refuse invalid nonce, never reflect/copy account data. Response includes version, SHA-256 of a domain-separated nonce, actual service PID and UID. Do not initialize the source native hook or load an arbitrary guest APK.
3. Add explicitly launched `SyntheticChannelProbeActivity` to bind `BIND_AUTO_CREATE`, require a real remote proxy (not same-process local interface), challenge/response digest, different process PID, same Android UID, exact service component, and a bounded timeout. Write a sanitized `files/binder-probe/latest.json` report with unique run UUID and denial flags. Unbind safely; service death or missing receipt MUST be FAIL, not success.
4. Extend `scripts/run_guest_code_emulator.py` with `--proof-level e15`, launching the explicit Activity on a fresh, owned Android 34 AOSP emulator. Read exactly the run UUID that runner supplied; verify `remote_binder_verified`, `challenge_response_verified`, distinct host/remote PID, and explicit same-UID/denial flags. E11/E12/E13/E14 assertions must remain unchanged and continue to run at their respective levels.
5. Update existing **strict** `scripts/run_native_probe.py --audit-only` to exact 3 host/probe Activities and 4 internal guest-prefixed Services; reject any unexpected component or exported Service. Preserve no dangerous permissions and native ELF audit.
6. Create E15 pinned CI workflow: Java/Kotlin/JVM tests, signed APK build/lint/audit, fresh KVM AVD, Android IPC receipt, artifact/report on success and failure.
7. Record actual failure cause and plan adjustment before each correction. Do not ignore lint, weaken tests, change minSdk/targetSdk or use a third-party account to force a green result.

## Acceptance and limits

An E15 PASS demonstrates only that Android Binder can deliver a checked challenge from host Activity to a private Service in a **different process** and return a correctly authenticated same-UID synthetic response. The E15 service does **not** use the private guest classloader nor attach an Android guest Application. It is a component needed for later explicit IPC dispatch, not evidence of full Binder virtualization.

Still required: real guest context/lifecycle and component registration, Intent/Binder/Provider mediation, split APK resources and class loader graph, failure recovery, Android ARM64/One UI, account separation, app-by-app validation, signed release. E15 is **not** production-ready and must not be merged as a full clone product.
