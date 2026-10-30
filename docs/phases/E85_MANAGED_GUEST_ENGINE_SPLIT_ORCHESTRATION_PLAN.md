# E85 — ManagedGuestEngine typed split-provider orchestration pre-code plan

Date: 2026-10-30. Repository: danhgold7979-cyber/fonesi-apk.

Product mapping: Phase 1 steps 7–8, preparing Phase 3 step 15 and Phase 4 shared-update safety. E85 is a generic engine orchestration contract milestone, not production provider integration.

## Baseline

- Development branch: phase/e82-public-component-factory-feasibility-20261030.
- E83 contract boundary passed exact-source CI #41080414348 on SHA 387a92d78bac40f93dd837f92f1e11d091332c75. E84 source audit found no acceptable production split provider.
- Core defines SplitClusterGuestBackend with preflightCluster, installCluster and inspectCluster; GuestApkClusterIdentity has a canonical v1 digest; GuestApkClusterReadback.requireMatches validates exact identity, complete member set and SDK-owned private storage.
- FileSourceSnapshotter already freezes every APK path, records SHA-256/byte length and verifies the complete immutable snapshot.
- ManagedGuestEngine currently rejects every multi-member source in checkSource and hardcodes all engine split capability fields false because it does not orchestrate the typed provider.
- SpaceCoreBackend requires exactly one APK; RuntimeBindings.supportsSplits remains false.

## Goal

Implement generic orchestration for a future SplitClusterGuestBackend without inventing a production provider. The engine must validate the frozen source and exact cluster identity before any native user/SDK mutation, require provider install receipt and independent private-storage readback, and preserve RESERVED/reconciliation state after any uncertain post-SDK outcome.

## Implementation sequence

1. Re-read the exact ManagedGuestEngine create/update flow, FileSourceSnapshotter and all affected tests before editing. Confirm that multi-file snapshots retain every member's name, size and SHA.
2. Permit a multi-member source only when the backend implements SplitClusterGuestBackend and reports complete cluster preflight, install dispatch and readback capability. Otherwise reject before snapshot, user reservation, native user creation or SDK mutation.
3. After freezing the source, call preflightCluster on the frozen snapshot. Validate the returned identity structurally and require exact package, version and signer match; require member count to equal snapshot file count; compare the multiset of every snapshot SHA-256/byte length with every identity member. Never trust file names alone.
4. For create, preserve the existing peer-scope and foreground authorization checks. Perform cluster preflight before createUser. After creating a reserved user, call installCluster, validate its returned readback with requireMatches, then call inspectCluster independently and validate again. Confirm the ordinary package identity readback before marking the registry row READY.
5. If any error occurs after the first SDK mutation, retain the RESERVED row and immutable snapshot for reconciliation. Do not uninstall, delete the snapshot or simulate rollback when the provider outcome is uncertain.
6. For shared updates, preserve the exact affected-clone set and require every peer to be stopped and READY. Preflight the new complete cluster before SDK mutation. Require accepted atomic-update capability before dispatching a multi-member update. Dispatch once through the typed provider, then inspect the full cluster for every affected virtual user and verify the same canonical identity before returning success.
7. Derive EngineAvailability.importsSplitApks and EngineCapabilities split-import flags only from a typed provider with the complete preflight/install/readback contract. Do not infer split code execution or atomic update from import/readback alone; those require their separate proof flags and dependency chain.
8. Add a test-only SplitClusterGuestBackend double. Cover successful multi-member create and shared update; missing/extra member; changed SHA/length/package/version/signer; invalid canonical digest; incomplete or non-private readback; provider preflight failure before mutation; install/readback mismatch after SDK entry; peer becoming busy; and atomic-update capability absent. Assert exact no-mutation or retained-RESERVED behavior.
9. Strengthen the E85 static audit so SpaceCoreBackend remains one-APK only, RuntimeBindings.supportsSplits remains false, and no production adapter is fabricated.

## Acceptance criteria

- Multi-member sources cannot reach a non-typed or incomplete provider.
- Every irreversible operation is preceded by frozen-source verification and typed cluster preflight.
- READY is written only after provider install receipt, independent private-storage readback and package identity readback all match the canonical identity.
- Uncertain post-SDK failures retain the reservation/snapshot for reconciliation and never trigger destructive rollback.
- Shared update verifies every affected virtual user and requires atomic-update proof before dispatch.
- The generic orchestration tests pass on exact source/test SHA and inherited E40–E84 audits remain green.
- No production provider, third-party guest execution, UID/account isolation or physical-device acceptance is claimed by a fake provider test.

## Explicit exclusions

Do not import the ISEKHON/VirtualApp fork, purchase the commercial VirtualApp SDK, change the pinned SpaceCore AAR, add a fake split adapter, enable candidate supportsSplits, or increase the product score. Those require a separately source-verified provider and its own license/native/runtime acceptance.

## Progress boundary

E85 is engine-side protocol orchestration only. Product readiness remains 28.00%; accepted real third-party clones remain 0 until an actual production provider passes split import, readback, guest execution and phone/device gates.
