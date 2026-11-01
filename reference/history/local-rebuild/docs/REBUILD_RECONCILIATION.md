# Divergent-head semantic reconciliation

Compared all 14 non-ancestor heads against `2fac016e3b28e4d9f21390c221cad1df7a382c86`, read-only. All-ref restore verified separately. No wholesale cherry-pick required; preserve all history and the following regression oracle.

| Head | Semantic result |
|---|---|
| E12 `5e43dd4` | API28 process-name guard retained in GuestCodeProbeService; keep fallback regression. |
| E44 durable `b5bd4d8` | RoomLegacyUpdateSourcePolicy, TaskCodec, UpdateTask, LocalServices, RoomUpdateTaskStore, UpdateTaskClusterOwnershipRepository identical; latest adds enqueuer/runner defenses. Preserve marker/owner transaction rollback, pre-marker owner guard and authority-sensitive dedup. |
| E46 `c30fe2b` | ForegroundAutoUpdater production source identical. Latest intentionally allows unrelated-package update rather than global starvation; preserve private-source non-cancel/non-execute and unrelated progress. |
| E47 `2fbc156` | rejectPrivateSourceConflicts remains inside enqueue transaction; latest also blocks preview. Preserve mixed-group all-or-nothing consent and pre-marker owner checks. |
| E51 engine-scoped `31df232` | Namespace filtering retained; E54/E55 strengthen eligible scheduling and namespaced controls. Preserve foreign snapshots/journal, same-provider/package FIFO and review barriers. |
| Alternate E75 `c0a2dbb` | Superseded by E80/E81 main-thread Instrumentation construction, exact counts and restart proof. Do not restore old preparation-thread construction; retain stricter assertions below. |

Documentation-only heads E11/E48/E49/E50/E55 SDK/E68/E69/E76 retain exact historical reports; no unique executable merge needed.

## Unique assertions to carry into fresh runtime harness

- Unattached synthetic Activity must have null baseContext **and** Application **and** Window (latest E80 only checks baseContext).
- Receipt Android API must be integer type, not `34.0`; boolean values must be strict booleans.
- Ordinary app process UID must be positive, not zero.
- Canonical run UUID, full lifecycle transcript, exact source/APK/process identity, failure-receipt rejection and false production/guest/kernel-UID claims remain mandatory.

These checks improve synthetic receipt validation; they do not establish production isolation.

## Frozen regression families

PrivateClusterRetainedEnqueuerTest authority dedup; StorageIntegrationTest strict marker codec/pre-marker references; UpdateTaskClusterOwnershipRepositoryTest atomic rollback; UpdateRunnerTest no original-catalog fallback/foreign-state mutation; ForegroundAutoUpdaterTest no private-task cancellation plus unrelated progress; UpdateRequestServiceTest transaction/preview guards; E75 strict receipt assertions combined with stronger E80/E81 execution constraints.

Source replacement can proceed with this oracle and verified recovery, but each replacement still needs equivalent tests; having a backup does not justify dropping protection.
