# E26 — background-safe durable update-journal recovery (plan before code)

Date: 2026-10-28. Continue on the existing cumulative branch \`phase/e21-android-split-code-proof-20261028\`; do not create another phase branch. E21–E25 are green on code SHA \`d911ee266a92a0c85ec368dbd8df77c0dba6af62\`. The product roadmap remains 8 phases / 32 steps; readiness is estimated at ~30%, not measured.

## Purpose and safety boundary

The product already has a durable Room update queue and a write-ahead state machine, but no independent background recovery. E26 adds **journal-only recovery** when Fonesi is closed. It must not initialize SpaceCore, enumerate or import guest APKs, start a clone, read account contents, retry an uncertain update or mark an update successful without a foreground engine readback.

## Detailed implementation plan

1. **Separate runtime activation from process creation.** Remove candidate SDK initialization from \`FonesiApplication.attachBaseContext\`. Activate the candidate only from a visible, unlocked \`LockedActivity\` lifecycle, with a synchronized/idempotent \`RuntimeBindings.attach\`. WorkManager-created process startup must not initialize the closed-source engine. The cloud build remains bound to \`UnavailableCloneEngine\`.
2. **Core journal recovery state machine.** Add platform-independent \`UpdateJournalRecovery\` using the existing \`UpdateTaskStore\`, \`SourceSnapshotter\` and \`QueueLock\` ports. Under the same OS lock and CAS revision checks:
   - \`PREPARING\`: discard only that task's incomplete snapshot; if cleanup succeeds, transition back to \`QUEUED\`. If cleanup fails, leave the durable state unchanged and request retry.
   - \`APPLYING\` or \`VERIFYING\`: transition to \`REVIEW_REQUIRED/INTERRUPTED\`, retain the snapshot, and never rerun the import automatically.
   - \`REVIEW_REQUIRED\`: leave it unchanged; only the existing foreground runner may compare real engine readback and promote it to success.
   - All other states stay unchanged. Lock contention is retryable; pause is never cleared.
3. **WorkManager integration.** Pin a stable WorkManager runtime version; schedule one startup request and a low-frequency periodic recovery. The worker may open only Room, private update-snapshot paths and the OS queue lock. It must not call \`LocalServices\`, \`RuntimeBindings.engine\`, \`FCore.init\`, \`AndroidSourceCatalog.current\`, \`PackageManager\` or any guest API. A lock/cleanup failure returns \`Result.retry()\`; the worker never reports an APK update as complete.
4. **Manifest and dependency audit.** Update the exact merged-manifest permission/component allowlist only for documented WorkManager boot/job/lock components. Do not add exported Fonesi entry points, network access to the cloud build, broad package visibility or extra SDK capabilities. Verify cloud and candidate APKs separately.
5. **Tests.** Core tests cover PREPARING cleanup/requeue, cleanup failure, APPLYING/VERIFYING to REVIEW_REQUIRED, snapshot retention, lock contention, CAS race, pause preservation and idempotence. Robolectric tests prove worker startup never calls runtime initialization or engine methods, and that retry is returned when recovery cannot safely acquire the lock.
6. **CI and evidence.** Add an app-cloud workflow using the pinned Android toolchain, \`scripts/check.sh\`, merged-manifest/permission/component verification and candidate build/audit on manual dispatch. Run the relevant technical regressions after shared code changes. Record exact SHA and test totals; keep the production engine capability disabled.

## Acceptance criteria

- A forced process death during PREPARING cannot leave an unbounded orphan snapshot or lose the queued task.
- A forced process death after APPLYING never causes a blind second import; it remains REVIEW_REQUIRED until the foreground engine is available and readback verifies the outcome.
- A background worker cannot initialize the SDK, open a clone, read account data or perform an APK import.
- Cloud and candidate builds retain their explicit engine boundary; no exported component or permission is added without exact audit.
- No progress percentage increase is awarded for a synthetic worker test alone. E26 is a technical/product reliability slice, not product Phase 26.

## Remaining product gates

This work does not solve real guest Activity/Service/Provider lifecycle, guest PackageManager/Binder/permission mediation, strong per-account OS isolation, arbitrary third-party split imports, social login, Samsung ARM64 validation, session-preserving updates or release signing. Those remain open in product Phase 1/2/4/5/7.
