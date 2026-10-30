# E77 — execute signed PRIVATE Activity constructor preflight on AOSP emulator (pre-code plan)

Date: 2026-10-30. Exact repository `danhgold7979-cyber/fonesi-apk`. Branch `phase/e77-emulator-activity-preflight-proof-20261030` from E76 source `8abf642a4387b9e80cdc7288c0b84c3b4161a296`, [Draft PR #50](https://github.com/danhgold7979-cyber/fonesi-apk/pull/50). E75 source SHA `f6da373ae11806cce727fab0f07433d521a2c594` passed [CI #40927202073](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40927202073) SUCCESS (510 cloud/candidate tests/gate). E76 exact-source CI [#40929596439](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40929596439) was still running at planning; E77 must run all E76 regressions and cannot assume its result.

Original Product **Phase 1 steps 7–8**, preparing Phase 3 step 15. E77 adds a bounded **Android emulator runtime** check for E75 metadata preflight only; it does not accept guest Activity execution.

## Goal and safety boundaries

E74's existing runner can boot a dedicated owned AOSP API 34 x86_64 emulator, install exact signed synthetic host/payload APKs, launch a host probe Activity, validate a fresh UUID receipt, verify the exact PRIVATE DexClassLoader and signed private payload, and confirm no guest Activity was attached/registered. Its proof-level `e74` does not separately assert constructor preflight. Since E75 adds `getConstructor()`, E77 should exercise that exact path on Android rather than treating JVM reflection as device proof.

## Ordered implementation

1. Commit this plan and EXECUTION_PLAN entry before code. Preserve E76 tests; report E76 CI accurately.
2. Extend the controlled probe receipt with `private_activity_constructor_preflight_verified`, set only after `LoadedGuestCode.preflightExplicitActivityClass` returns the exact PRIVATE Activity class after E75 constructor check. Require it only for proof-level `e77`. Keep `actual_guest_activity_attached=false`, `real_guest_activity_registered=false`, `third_party_component_executed=false`, `production_clone_accepted=false`, and same host UID/kernel non-isolation.
3. Extend `scripts/run_guest_code_emulator.py` with proof-level `e77`: validate a fresh run UUID, exact source commit, exact APK SHA-256 for both signed artifacts, API 34 x86_64 emulator identity, actual host Activity lifecycle, PRIVATE classloader and constructor marker, then emit `PASS_E77_SIGNED_PRIVATE_ACTIVITY_CONSTRUCTOR_PREFLIGHT_ONLY`. Do not interpret this as guest Activity execution or UID isolation.
4. Add `scripts/audit_e77_emulator_activity_preflight.py` and a dedicated pinned workflow that builds/signs the opt-in host/payload APKs, runs source and signed-manifest audits, installs the AOSP API 34 image through the existing pinned tool helper, launches the owned emulator with bounded boot/probe timeouts, captures the receipt, verifies source/APK hashes and explicit false guest/UID/account flags, and uploads only sanitized receipt/test artifacts. Keep cloud/candidate and source AAR tests; runtime emulator failure must fail the gate, never downgrade to audit-only.
5. Add JVM tests for strict e77 receipt fields/runner proof-level validation if separable. Do not modify prior E74 acceptance or weaken existing proof levels.
6. Commit and open stacked Draft PR #51. Exact-source CI must be SUCCESS with a genuine emulator receipt. Report Android emulator evidence separately from Samsung/physical-device acceptance. Update results/status/progress only after success; report commit needs its own CI.
7. Leave `main` unchanged.

## Exclusions

This emulator exercises the project-controlled synthetic host Activity and a signed PRIVATE payload class; it does **not** instantiate/attach/launch the guest Activity, virtualize ActivityThread/Instrumentation/Binder/PackageManager, isolate Android UID/accounts, enable full split APK provider, or test social logins. Physical Samsung SM-A525F remains a separate acceptance gate. Product readiness stays **28.00%**, accepted real third-party clones **0**.


## Runtime adjustment after first CI attempt (30/10/2026)

The first E77 workflow attempt `#40931231012` passed audit, cloud/candidate and source-engine checks and installed the AOSP image, but the emulator runner stopped before boot because the GitHub-hosted runner has no accessible `/dev/kvm`. This is a **runner capability blocker**, not a guest-preflight assertion failure. E77 now allows a narrowly scoped `--allow-software-emulation` only when `proof-level=e77`, uses QEMU `-accel off`, and increases the bounded boot timeout to 600 seconds. All older proof levels still fail closed without KVM; no audit-only fallback is allowed. E77 is not PASS unless the new exact-SHA workflow actually boots Android and validates the full receipt. If software emulation cannot boot within the limit, report the limitation and leave Android runtime unverified rather than claim success.


## Follow-up remediation — bounded Android UI fingerprint collection (pre-code, 30/10/2026)

The exact E77 source `6593c0b24103f049ab851bcef3378dd35b74bb27` reached a booted AOSP API 34 emulator and installed both signed synthetic APKs in [CI #40932749422](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40932749422), but failed before Activity dispatch at `Android UI configuration did not stabilize before E24/E25`. The uploaded receipt confirms `android_boot_verified=true`, `android_apks_installed=true`, software emulation, and no constructor/guest receipt. E76 exact-source CI [#40929596439](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40929596439) is SUCCESS.

The observed failure is specifically the inherited pre-lifecycle stability gate. Its current implementation starts seven independent ADB shell processes for each fingerprint and allows only 40 seconds to obtain four identical consecutive snapshots. The no-KVM software-emulation path can make that repeated process/transport overhead exceed the budget. The artifact did not preserve per-sample snapshots, so this is a bounded implementation diagnosis, not a claim that a particular Android setting was observed changing.

### Ordered corrective work (plan before code)

1. Replace the seven serial ADB invocations per sample with **one** bounded `adb shell sh -c` read that collects the same seven values: `wm size`, `wm density`, system `font_scale`, system `user_rotation`, and the three existing locale properties. Preserve the complete normalized output as the fingerprint; do not drop fields or compare a weaker subset.
2. Keep the existing requirement of four identical consecutive fingerprints (three equal transitions). Increase only the bounded stability window from 40 to 120 seconds and bound the single ADB read to 20 seconds. Do not turn a timeout or command failure into success.
3. Persist sample count, consecutive-stable count and the last normalized fingerprint in the sanitized evidence on each sample and on timeout, so the next failure identifies whether the values changed or collection was too slow.
4. Extract the fingerprint reader into a small deterministic helper. Add Python unit tests using a fake ADB callback for one-call collection, all seven fields, bounded arguments, empty output rejection and non-zero command rejection. Add the test path to the E77 workflow and run it before the emulator stage; extend the E77 static audit to enforce the bounded window and unchanged stability threshold.
5. Re-run the full E77 exact-source workflow: E40–E77 audits, cloud/candidate checks, E76 non-initialization regressions, source-engine AAR/JVM checks, signed APK build/lint, then actual AOSP API 34 boot, installation, host Activity dispatch and strict E77 receipt. Emulator failure remains a failure; no audit-only fallback.
6. Update E77 results, STATUS and PRODUCT_PROGRESS only from the resulting exact-source CI. Product readiness remains 28.00% unless product acceptance evidence changes; no guest Activity, UID/account, split-provider or physical-device claims are introduced. Keep PR #51 Draft and `main` unchanged.

This is an E77 corrective implementation, not a new product Phase or a new E-number. E78 must not start until E77's exact-source acceptance gate is resolved.
