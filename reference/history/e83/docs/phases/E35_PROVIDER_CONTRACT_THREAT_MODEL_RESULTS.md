# E35 — provider contract and threat model results

Date: 2026-10-29. E35 specification deliverables are committed on `phase/e21-android-split-code-proof-20261028`.

## Completed

- `docs/engine/PROVIDER_CONTRACT.md`: capability snapshot, operation semantics, verified result states and invariants.
- `docs/engine/THREAT_MODEL.md`: assets, trust boundaries, threat actors, isolation levels and adversarial acceptance tests.
- `docs/engine/PACKAGE_CLUSTER_FORMAT.md`: immutable base/split cluster envelope, member identity, dependency graph, hashing, private storage and atomic publish requirements.
- `docs/engine/ACCEPTANCE_MATRIX.md`: 15 acceptance rows, current status and required proof.
- `docs/engine/ADR-001-provider-strategy.md`: compare pinned provider, alternative engine, first-party in-process engine and guest-OS/container architecture.

## Decision

No provider is currently accepted for production. SpaceCore remains opt-in and single-APK only. An opaque third-party JAR/AAR will not be imported. A first-party full virtualization rewrite is not yet authorized by evidence; the next narrow step is a private package-cluster store and validator that does not execute guest code.

## Current acceptance counters

- E29 synthetic dispatch: PASS; it does not register a real guest Activity.
- Real third-party applications accepted: 0.
- Real guest Activity/Service/Receiver/Provider acceptance: not achieved.
- Cross-clone account/session isolation: not achieved.
- Production split APK import/readback: not achieved.
- Product estimate: 28.00% weighted / ~30% rounded; unchanged.

## Next phase — E36

Implement only the immutable private package-cluster store and validator after a detailed E36 implementation plan. Initial implementation must support metadata and file integrity only; it must not advertise split support in the current provider, execute code, or alter `RuntimeBindings.supportsSplits`.
