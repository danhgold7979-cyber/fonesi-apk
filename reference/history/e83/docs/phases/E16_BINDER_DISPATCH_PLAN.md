# E16 — Bounded Binder-dispatched private synthetic guest code (plan before code)

Date: 2026-10-28. Branch: `phase/e16-binder-dispatched-private-code-20261028`, forked from E15 Android-verified commit `ebf6fe11ca86697ca9aa7252af5d51642e7fb510`. Keep `main@c055df6` and all earlier phases untouched.

## Review master plan and evidence first

Rechecked `docs/ROADMAP.md` 8 phases/32 tasks, `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, E10–E15 plans, `GuestCodeLoader`, `PrivateApkStore`, `GuestImageActivationLedger`, source native process hooks and AIDL IPC. Verified GitHub Actions:
- E11 signed private DEX/resource on real AOSP34: [40640306977 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977).
- E12 synthetic private **guest method** execution: [40641518802 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641518802).
- E13 original/A/B native synthetic file+SQLite routing: [40641656320 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641656320).
- E14 synthetic restricted scoped Context data A/B: [40643847891 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40643847891).
- E15 internal AIDL Binder IPC host to guest-named process: [40644938313 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40644938313).

E15 only returned a **host-implemented** SHA-256 response. E12 separately invoked private guest code inside a started service. Missing substrate: the host's **bounded Binder command** dispatching *private verified guest bytecode in a remote dedicated process* and reading its result, without a fake host implementation or original APK fallback.

## E16 implementation and safety gates

1. Extend project-owned `ISyntheticGuestChannel.aidl` with a second explicit read-only method `exchangeGuest(String nonce)`, same bounded 32-lower-hex challenge, separate versioned E16 response contract. Keep E15 `exchange` unchanged.
2. Add a reusable *test-only* `SyntheticSignedPayloadSource` for reading the installed project-owned `vn.fonesi.payload` original base/split paths and verified signing identity; no third-party APKs or tokens, no data copy from a login app.
3. Inside the E15 non-exported `:fonesi_guest_channel` service, on its Binder worker thread only, one-shot: verify PID/process name/UID; obtain private canonical host roots, publish+reopen the full signed synthetic APK set via `PrivateApkStore`, verify its exact `GuestImageRef`, stage an E10 `GuestImageActivationLedger` reference (pending only, **not confirmed/installed**), configure native routing to project-owned empty synthetic files, enable it, then load `GuestCodeLoader` once and invoke `vn.fonesi.payload.ProbeEntrypoint.execute(nonce)` through the private DEX classloader. Verify resource marker. Return only version, resulting digest, PID, UID. Refuse any host-classloader fallback or repeat attempt/hot-swap.
4. Extend host `SyntheticChannelProbeActivity` to call E15 `exchange` then E16 `exchangeGuest`, verifying the E16 digest from the guest bytecode's *distinct E12 domain separator*, same remote PID/UID, fresh nonce, and same Binder interface descriptor. Report positive `private_guest_bytecode_via_binder_verified` and `private_guest_resources_via_binder_verified`, with `guest_application_attached=false`, `third_party_binder_virtualized=false`, `social_accounts_tested=false`.
5. Add `--proof-level e16` to the owned Android emulator runner. Reject E15-only receipts; check exact run UUID, all E15 fields, and new E16 flags. Preserve E11–E15 proof modes.
6. Strict manifest/ELF audit should still require exactly 3 host Activities, 4 internal Services and no permissions. Branch CI rebuilds and signs both APKs at exact commit, runs core/APK/source JVM suites, lint/static audit and real AOSP34 KVM runtime, uploads bounded sanitized artifacts on failure/success.
7. On failure, record exact stage/cause in this plan **before** source correction, rerun without changing assertions, downgrading Android SDK or bypassing cryptographic validation.

## Explicit acceptance and non-claims

- PASS requires a fresh Android IPC reply proving the **project-controlled guest method** was loaded and executed from a separate private signed APK cluster *inside the remote Binder process*, not from host business logic.
- It does NOT instantiate an arbitrary Android Application or Activity, mediate Android framework Binder, act as a real package manager, permit real social accounts, guarantee kernel/UID isolation, or support split classloader isolatedSplits. Same host UID remains a major isolation limitation.
- Do not integrate this synthetic channel into the production SpaceCore provider or release APK until later actual guest lifecycle/component and phone gates are met.
