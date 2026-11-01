# E14 — Restricted synthetic guest Context and scoped data readback

Date: 2026-10-28. **Plan written before source edits.** Branch: `phase/e14-scoped-guest-context-20261028`; exact baseline E13 `0045c2a03cc65323af447e162a261d9ab6e0b161`. Main remains `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`.

## Master plan and current gates

Checked `docs/ROADMAP.md` (8 phases/32 steps), `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, E10/E11/E12/E13 code/CI, the private APK verifier/store, `GuestCodeLoader`, `NativeRuntime`, and synthetic probe components.

- E11 Android 34 signed private DEX/resources proof: [CI 40640306977 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977).
- E12 real *synthetic bytecode execution* in the dedicated Android guest loader process: [CI 40641518802 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641518802).
- E13 real *synthetic two-process A/B native file and SQLite routing* preserving the original: [CI 40641656320 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641656320). Both guest-named processes have the **same Android UID** as the host.
- Still NO demonstrated Android guest `Application.attach`, real `Activity` lifecycle, Intent/Binder/Provider mediation, multi-account security isolation or third-party app compatibility; E14 is a prerequisite, NOT the completion of phase 1/2/3.

## Risk and design limits

A regular `ContextWrapper` inherits many host API methods and native code runs at the host UID. It is **not** a security boundary for arbitrary third-party guest code, and can be bypassed through Java reflection, APIs not overridden, direct native syscalls or shared UID. Do **not** advertise a ContextWrapper as full Android virtualization or support social-account guest operation.

E14 builds an explicit **synthetic-only Context prototype**, not a complete production Context. It may execute *only* the project-owned disposable payload. The existing real-source engine provider and SpaceCore candidate are unchanged. It does not attach a guest Application or dispatch guest Android components.

## E14 implementation slices

1. Add `engine-source/SyntheticGuestScopedContext.kt`: an opt-in ContextWrapper for one verified `LoadedGuestCode` and one canonical clone UUID. Require dedicated `:fonesi_guest_...` process and `NativeRuntime.ENABLED`. Root lives under host-owned canonical private no-backup directory; reject traversal, external paths, symlinks and malformed names before any IO.
2. Provide controlled `getFilesDir`, `openFileInput`, `openFileOutput`, `getCacheDir`, `getNoBackupFilesDir`, `getDatabasePath`, `getSharedPreferences(MODE_PRIVATE)`; keep persistent per-clone metadata under namespace per UUID. Bind package/classloader/resources/assets to the signed private code image, not host asset path. Reject non-private flags and unsupported `getSystemService`, `getPackageManager`, `getContentResolver`, host component dispatch. Explicitly call out inherited platform APIs that still require implementation; this is NOT an untrusted-code sandbox.
3. Add `probe-payload/ScopedProbeEntrypoint.java` implementing synthetic read/write for SharedPreferences, private file and a SQLite database. No credentials, network, account data or third-party package use. Its `read` must not implicitly write data.
4. Extend `GuestCodeProbeService` **after E12 signed private code execution**: instantiate two controlled prototype contexts (different UUIDs) over one verified image; write A, assert B empty, write B, read A unchanged and B distinct; assert no change to host original marker. This serial A/B prototype must not be confused with independent guest processes or account switching support.
5. Extend `run_guest_code_emulator.py` to support `--proof-level e14`, require all E12 assertions PLUS `synthetic_scoped_context_verified=true`, `private_file_state_verified=true`, `shared_preferences_state_verified=true`, `sqlite_state_verified=true`, `same_android_uid=true`, while Activity/Binder/social account flags remain false. Verify fresh same-run UUID and strict SHA-bound artifact.
6. Create pinned Android 34 KVM CI workflow on this branch: test Core/APK/source JVM, build/lint/sign new probe + payload, boot owned AVD, run e14 and retain sanitized report/APKs. No test is considered PASS until the job and Android receipt actually succeed.
7. If tests fail, inspect logs, add a cause-and-scope section to this plan before making code fixes; never weaken assertions, disable lint or lower SDK to force success. Preserve all prior E11/E12/E13 branches and results.

## Acceptance

- Source and test code reviewed; Gradle build/lint passes, binaries verified.
- Actual Android 34 AOSP synthetic payload read/write through restricted context passes A/B/non-interference, with signed private DexClassLoader and Resources revalidated.
- Receipt explicitly says `guest_application_attached=false`, `guest_activity_executed=false`, `binder_provider_tested=false`, `social_accounts_tested=false`, `kernel_uid_isolation=false`.

**Remaining after E14:** a valid Android guest Application/Activity component dispatcher, Binder/Intent/Provider routing, system service/permission mediation, split/isolated split classloader and resource graph, crash/restart/update across real guest versions, host-owned source provider integration, Android ARM64/Samsung and real app/account compatibility. None is silently closed by synthetic scoped storage.
