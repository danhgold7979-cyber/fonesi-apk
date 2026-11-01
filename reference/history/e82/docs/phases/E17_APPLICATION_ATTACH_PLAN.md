# E17 — Attach a project-controlled synthetic Android Application using public framework API

Date: 2026-10-28. Branch: `phase/e17-synthetic-application-attach-20261028`. Start only from E16 Android-tested SHA `c5706a4259b3e7a8ddcd29b9dddb178b33259ae5`; `main` remains `c055df6`. This is the pre-code plan.

## Master plan reconciliation

Reviewed `docs/ROADMAP.md` (8 phases/32 steps), `docs/EXECUTION_PLAN.md`, latest status and E10–E16 integration/test reports. Previous real Android 34 synthetic proof:
E11 [40640306977](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977) private DEX/Resources;
E12 [40641518802](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641518802) private guest bytecode;
E13 [40641656320](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641656320) two native file/SQLite processes;
E14 [40643847891](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40643847891) scoped synthetic Context A/B;
E15 [40644938313](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40644938313) internal Android Binder;
E16 [40646052743](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40646052743) Binder-dispatched signed private guest method and Resources.

The next bounded gap is a real `Application` object with `attachBaseContext` and `onCreate` invoked from the guest's privately loaded class, not just a static method. The public `Instrumentation.newApplication(ClassLoader, String, Context)` API can instantiate+attach an `Application` to a supplied Context. But that does **not** register a virtual package with ActivityThread/PackageManager nor permit arbitrary guest Android Activities/Services.

## Implementation slices

1. Add `probe-payload/SyntheticGuestApplication.java` with a harmless `onCreate` that verifies its base `Context` reports project-owned payload package and signed private resource, then writes a bounded synthetic start marker via a scoped SharedPreferences name. No account secrets, network, third-party code or attempt to start Android components. Declare it as the payload's `android:name` application class; no additional permissions.
2. Preserve E16 `exchange` and `exchangeGuest` outputs. Add `exchangeGuestApplication(String nonce)` to AIDL as a separate versioned E17 method, called only **after** E16 private code proof succeeded. Keep a verified `LoadedGuestCode` handle in the dedicated same-UID Binder process, without loading/swapping a second code image.
3. In `SyntheticGuestChannelService`, enforce caller UID/process and a one-time application attempt. Construct new `SyntheticGuestScopedContext` with a canonical random UUID and the existing privately verified image. Use `new Instrumentation().newApplication(code.classLoader, SyntheticGuestApplicationName, scopedContext)`, confirm object classloader and `getBaseContext() == scopedContext`, then invoke `Application.onCreate()` once. Read back exactly one synthetic start marker from the scoped Context. Re-check signed private image evidence and pending-not-active state from E16 before returning. Report versioned E17 response with the guest method's SHA-256 proof and same remote PID/UID.
4. Host `SyntheticChannelProbeActivity` uses `exchange`, `exchangeGuest`, then `exchangeGuestApplication` on the same AIDL channel and verifies all three digests, same remote PID/UID, and a one-time Application start. Its diagnostic contains `synthetic_guest_application_on_create_verified=true` but `guest_activity_executed=false`, `full_guest_application_lifecycle_verified=false`, `third_party_binder_virtualized=false`, `social_accounts_tested=false`.
5. Extend owned-AOSP emulator runner `--proof-level e17` and assert E16 + E17 flags. Keep other proof levels backward-compatible. Signed APK build/lint/static component+permissions audit and AVD runtime must all pass from the same commit. No physical phone actions.
6. CI must not bypass runtime failures, hidden API restrictions, lint or permission constraints. If `Instrumentation.newApplication` refuses a restricted Context, capture the concrete stage and stop; do not use hidden `ActivityThread` APIs or turn the test into a stubbed success.

## What E17 does NOT prove

Even if the public-framework synthetic `Application` is attached and its `onCreate` executes, this is **not** equivalent to Android installing/launching an arbitrary guest package. It does not register Activity/Service/Provider/Binder contexts, implement permission mediation, support isolated split resources, provide a distinct UID/kernel sandbox, or guarantee social-app account isolation or Android ARM64/One UI compatibility. Keep the source engine opt-in and the real app's clone capability gated.
