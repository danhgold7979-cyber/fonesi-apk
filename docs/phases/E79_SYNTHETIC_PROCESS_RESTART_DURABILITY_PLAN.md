# E79 — synthetic scoped data durability across actual host-process death/restart (pre-code plan)

Date: 2026-10-30. Repository: `danhgold7979-cyber/fonesi-apk`.

- Product mapping: **Phase 1 steps 7–8**, controlled-app portion of step 8 (create/open/read a test data scope). E79 remains a technical sub-milestone, not a new product Phase.
- Predecessor: **E78 technical PASS** at exact code/test SHA `57d60cb80d6cece692b2898afd15d8668fd14883`, [CI #40998462184](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40998462184). The E78 receipt proves two signed PRIVATE synthetic Application instances attached with public Instrumentation and two same-UID scopes persisted across Context-wrapper reconstruction. It does not prove data survives process death.
- E78 reporting/docs head: `6fe0ad05de49c04ff862fa49825562babdbeb255`; its docs-only exact-source CI [#41000533354](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41000533354) was running when this plan was created. Verify that run before treating the reporting head as independently CI-verified.
- `main` remains `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`. E78 branch/PR #52 remains Draft. New E79 branch stacks on E78.

## Goal

Prove that the existing **project-owned signed synthetic** `SyntheticGuestApplication` and two `SyntheticGuestScopedContext` storage scopes retain consistent SharedPreferences, files and SQLite markers across an actual Android host-process death and fresh process start on the owned AOSP API 34 emulator. The second process must reload the private signed APK, reconstruct the two Context wrappers, attach fresh Application instances through public Instrumentation, run one `onCreate` per scope in the new process, and read back the exact values written by the prior process.

This is a same-package/same-UID synthetic durability test. It is not a guest OS process, a guest UID/security boundary, third-party app execution or production clone acceptance.

## Source findings and constraints

- `SyntheticGuestScopedContext` places file/SQLite data below host `noBackupFilesDir/source-guest-scopes/<uuid>` and preferences in host SharedPreferences files with a `fonesi-synthetic-<uuid>-` namespace. These should survive process death while remaining in the host app's UID.
- `GuestCodeLoader` allows one load attempt per process; its static guard resets only when Android starts a new process. The E79 second stage must force-stop the entire probe package, confirm the dedicated process is gone, and start a fresh process rather than reusing the Activity or classloader.
- The current `SyntheticGuestApplication.onCreate` insists `starts == 0` and writes `starts=1`. That is correct for E78 fresh scopes but prevents testing the same scope in a second process. E79 must replace this with a strict per-process marker: one invocation per scope per process, monotonically increasing persisted start count, last process PID and a fresh per-process UUID token. A duplicate `onCreate` in the same process must fail; a new process must have a different token and increment the count exactly once.
- The process token must be generated once per payload classloader/process, not once per Application instance. Two distinct scopes in the same process may each record the same process token; the same scope may not accept a duplicate callback for that token.
- Use only public `Instrumentation.newApplication` and `callApplicationOnCreate`; no `ActivityThread`, `LoadedApk`, hidden API reflection, PackageManager registration or guest Activity launch.
- Markers are random 128-bit hex test values, not credentials. Receipts should record marker hashes or otherwise keep values out of workflow logs; only pass the exact values back through the test runner for readback.
- Candidate production integration remains one full APK only (`SpaceCoreBackend` rejects `apkPaths.size != 1`; `RuntimeBindings.supportsSplits=false`). Do not change it in E79.

## Ordered implementation

1. Extend the synthetic payload Application lifecycle marker. Generate a canonical random process token once per payload process/classloader. On `onCreate`, reject if that same token is already recorded for the scope; otherwise increment persisted `starts`, persist `last_pid` and `last_process_token` atomically, and verify commit success. Fresh E78 scopes must still report `starts=1`.
2. Extend the controlled host Activity with E79 `write` and `verify` stages. Both stages must re-open the same signed PRIVATE payload image, perform the E77 Activity class/constructor preflight and E78 Application class preflight, then attach two distinct Application instances on the main thread.
3. E79 write stage: create two fresh canonical UUID scopes and random markers; verify all stores start empty; call `onCreate` once per scope; write/read SharedPreferences, files and SQLite; verify A/B cross-readback; write a bounded stage receipt containing source/APK hashes, run UUID, PID/UID/process, both scope UUIDs, marker digests, Application process token and `starts=1`.
4. The runner must read the exact write-stage receipt from `run-as`, issue `am force-stop vn.fonesi.nativeprobe`, and prove the dedicated `:fonesi_guest_ui` process is absent before continuing. A timeout, still-running process or ambiguous PID must fail closed.
5. E79 verify stage: relaunch the host Activity in a fresh dedicated process with a new run UUID, the two existing scope IDs and the non-secret test markers. Reload the signed private code image and construct new Context wrappers. Attach two fresh Application instances with public Instrumentation and call `onCreate` once for each. Verify the persisted start count advanced from 1 to 2, the new PID differs, the process token differs, and the same preference/file/SQLite markers are returned exactly.
6. Verify the two scopes still do not cross-read; reconstruct each Context wrapper again within the new process and repeat readback. Re-open/re-hash the signed private APK cluster after both stages. Do not silently recreate missing scope files or replace an inconsistent database.
7. Add `validate_e79_receipts(write, verify, source_sha, host_apk_sha, payload_apk_sha)`. Require exact source/APK hashes, canonical distinct scope UUIDs, matching run/process/API identity, different PIDs and process tokens, `starts=1` then `starts=2`, exact marker digests, complete A/B storage readbacks, and explicit false fields for ActivityThread/PackageManager registration, real guest Activity, third-party execution, kernel UID isolation, social accounts and production clone acceptance.
8. Add Python tests for successful two-stage receipt, missing stage, source/APK mismatch, reused PID/token, non-incrementing start count, changed marker digest, duplicate scope IDs, cross-scope leak and forbidden overclaims. Add source-engine/payload tests for duplicate onCreate in one process and one callback in a new process token.
9. Extend the E78 audit/workflow to E79. Run E40–E79 audits, cloud/candidate gates, source-engine AAR/JVM and host tests, signed host/payload APK build/lint/static audit, then boot AOSP API 34 and run the two-stage force-stop/relaunch proof. Software emulation remains allowed only for bounded synthetic E77/E78/E79 proof; older guest-execution modes stay fail-closed without KVM.
10. Produce a final E79 receipt only after the second process has completed and exact marker readbacks pass. Upload sanitized receipts and test XML. Update E79 results, STATUS, PRODUCT_PROGRESS and EXECUTION_PLAN only after exact-source CI succeeds.

## Acceptance criteria

- Exact-source GitHub Actions succeeds with zero test failures/errors/skips and the complete AOSP API 34 process-death/restart proof.
- The first process writes two scopes and exits through an explicit package force-stop; the runner confirms it is no longer running.
- The second process has a different PID and different per-process token; each scope's persisted Application start count advances exactly once from 1 to 2.
- Both scopes return exact previous SharedPreferences/file/SQLite markers after process restart and Context reconstruction; no partial state or cross-scope read is accepted.
- Receipt binds both APK hashes and exact source SHA and explicitly denies ActivityThread/PackageManager registration, real guest Activity, third-party execution, UID/account isolation and production clone acceptance.
- No change to production split support, no secrets, no real social app, no `main` merge.

## Risks and fail-closed behavior

- `am force-stop` may not terminate the dedicated process or PID observation may be ambiguous; fail rather than calling a same-process retry a restart.
- Android may reuse a PID. The process token is the authoritative new-process discriminator; also require a distinct PID for this bounded emulator run. If either is not distinct, do not pass.
- If SharedPreferences/files/SQLite diverge or disappear after process death, retain the failure and diagnose durability/commit ordering; do not reseed the scope during verify stage.
- If the new Application lifecycle marker cannot distinguish duplicate callbacks from a new process, revise the design and tests before implementation; do not weaken duplicate-callback rejection.
- E79 remains synthetic same-UID proof. Physical Samsung testing, real third-party components/accounts, ActivityThread/PackageManager/Binder/permission mediation, complete split provider/import and production acceptance remain open.
