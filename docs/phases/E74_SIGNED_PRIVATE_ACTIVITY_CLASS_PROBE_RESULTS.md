# E74 — opt-in signed PRIVATE synthetic Activity class probe results

Date: 2026-10-29. Repository `danhgold7979-cyber/fonesi-apk`, original **Product Phase 1 steps 7–8**, preparatory Phase 3 step 15. Branch `phase/e74-signed-private-activity-class-probe-20261029`, [Draft PR #48](https://github.com/danhgold7979-cyber/fonesi-apk/pull/48) stacked on E73. The [pre-code plan](E74_SIGNED_PRIVATE_ACTIVITY_CLASS_PROBE_PLAN.md) was committed before source changes.

## Implementation

A dedicated opt-in `e74` mode in `SyntheticGuestUiProbeActivity` calls `LoadedGuestCode.preflightExplicitActivityClass("vn.fonesi.payload.PayloadActivity", TRUSTED_INTERNAL)` on a verified signed PRIVATE APK in the synthetic guest-named **host** process. It checks exact package/cluster, actual PRIVATE-defining DexClassLoader, rejects host classloader fallback and reopens signed APK bytes before setting its success marker. It does **not** instantiate, attach, register or start the guest Activity. Existing E24 signed PRIVATE View and **host** Activity lifecycle remain.

The owned-emulator runner accepts `--proof-level e74` and requires exact APK SHA-256, fresh run UUID, dedicated host process, explicit true private Activity class marker and explicit false real guest Activity/UID/account/third-party flags. An Android runtime PASS requires an actual bounded emulator receipt; CI did NOT execute that runner on Android. Added E74 source audit and opt-in native-probe/payload APK builds with lint and static signed/no-permission ABI audit.

## Exact-source verification

**E74 technical BUILD/AUDIT PASS** at code SHA `8cee1ba5b1afefaa1df17d6dc01a90d283019f55`, [GitHub Actions #40917389636](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40917389636) **SUCCESS**: exact SHA, E40–E74 audits, **510 tests per cloud and candidate gate**, eight E73 source-engine JVM class tests, native source ARM64 AAR/ELF checks, candidate APK signature/permissions and standalone native host probe/payload APK assemble/lint, including `run_native_probe.py --audit-only`. [Test-only artifact #12917297460](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40917389636/artifacts/12917297460). The logs explicitly say **Android execution NOT VERIFIED**. E73 source SHA `1297292cf1b3afad71edea9bf24504d15322d35f` passed [CI #40916252411](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40916252411). E73 report HEAD `3d4b53fa6702a8f8b9fad67190fb2bd6a4308df8` has independent CI [#40917685335](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40917685335), pending when this report was written.

This report/integration commit has a new SHA and requires its **own** exact-source CI. No E74 Android/emulator/phone runtime test was executed; no third-party guest accepted.

## Blockers

No guest Android ActivityThread/Service/Provider/Binder execution or OS UID/account isolation; production `supportsSplits=false`/`importsSplitApks=false`. Physical Samsung/other phone, third-party social accounts, update data retention and release signing unaccepted. **Weighted product 28.00%**, accepted real third-party clones **0**. Keep Draft and `main` unchanged.
