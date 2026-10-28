# E24 — project-owned private DEX screen + Android host lifecycle relay (pre-code plan)

Date: 2026-10-28. Reconciled `docs/ROADMAP.md` (8 product phases / 32 steps), `docs/PRODUCT_PROGRESS.md` (~30% engineering estimate), `docs/STATUS.md`, and E21–E23 signed synthetic APK/classloader/resource proofs. E23 branch-head CI PASS [40659090086](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40659090086), E21/E22 regression also green at `e32839d118bbee346de30aa116d0bb124539f784`.

## Decision and safety boundary

A signed private DEX can execute methods and read private Resources (E11–E23), but this does not mean Android ActivityThread recognizes a guest package or can start third-party Activities. Android framework registers host components, not arbitrary APK Activities. An E24 test must **not** claim arbitrary guest Activity launch from a stand-in proxy. The narrow next proof is a real Android-hosted screen whose View implementation and ordered lifecycle callbacks execute from a strictly project-controlled signed/private APK.

No hidden ActivityThread API, targetSdk lowering, accessibility bypass, real social APK/credentials or installed-original code fallback. No changes to SpaceCore production provider or `importsSplitApks=false`.

## Implementation sequence and acceptance

1. Add a synthetic-only DEX class in `probe-payload`: validate fresh 32-hex challenge and UI thread, create a real `TextView` with nonce-dependent digest marker, accept ordered `CREATE → START → RESUME → PAUSE → STOP → DESTROY` callbacks, provide a bounded deterministic transcript. The class must never be packaged in the native probe host.
2. Add a test-only host `SyntheticGuestUiProbeActivity` executing in a dedicated `:fonesi_guest_ui` process, with explicit launch/run UUID. It stages a cryptographically signed copy via `PrivateApkStore`, enables native routing only for dedicated private inert paths, loads code/resources on a background thread via existing `GuestCodeLoader`, then calls the project-owned synthetic View factory on the main thread. Observe Android host lifecycle and relay the events in order to the private class; accept only actual attached View and a safe main-thread finish. Write a UUID-bound private success receipt after `onDestroy` with true flags only when the real View attached and all callbacks succeeded. Crash, missing receipt, stale ID, wrong code origin, wrong event sequence or invalid signer must fail.
3. Exact manifest allowlist update: one additional exported **test-only** Activity with dedicated `:fonesi_guest_ui` process; no services or dangerous permissions added; amend `run_native_probe.py` to audit exact component/process and ELF.
4. Extend `run_guest_code_emulator.py` with `--proof-level e24`. Require exact SHA-256 for new signed probe + payload, fresh isolated owned AOSP34 x86_64/KVM emulator, explicit host-Activity launch, strict private JSON receipt. Do not read accounts, grant permissions, or target physical devices.
5. Add `e24-guest-ui.yml` workflow to cumulative E21 branch, using pinned Android toolchain and actual emulator. Require core/APK/source regression, build/lint/exact audit, fresh signed payload, real UI attachment/lifecycle receipt; upload only synthetic debug artifacts. Preserve E21–E23 regressions.
6. Record exact green CI results and `docs/STATUS.md` only after evidence. Maintain weighted product readiness at about 30% until a real guest component provider and device/account gates are proven.

## Explicit nonclaims

This only proves **host Activity + private synthetic View factory/lifecycle relay**. It does not instantiate/attach arbitrary guest Activity, register a guest package with Android, virtualize Service/Provider/Binder, enforce separate kernel UIDs or prove Facebook/Zalo/TikTok compatibility. Production clone engine and Samsung ARM64 trials remain separate mandatory acceptance gates.
