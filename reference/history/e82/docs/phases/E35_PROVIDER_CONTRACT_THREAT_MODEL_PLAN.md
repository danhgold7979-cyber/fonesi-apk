# E35 — provider contract, threat model and acceptance ADR (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`. E30–E33 did not identify an eligible provider; E34 recommends a contract/feasibility gate before either a licensed-provider integration or first-party runtime rewrite. E35 is specification work, not implementation.

## Objective

Create a single source of truth for what the engine must guarantee, what it may explicitly not support, how each guarantee is tested, and what evidence is required before the UI can claim the capability is available.

## Deliverables

1. `docs/engine/PROVIDER_CONTRACT.md`: typed provider operations/capabilities and error semantics.
2. `docs/engine/THREAT_MODEL.md`: trust boundaries, assets, attackers, same-UID risks, filesystem/URI/process threats and account-isolation claims.
3. `docs/engine/PACKAGE_CLUSTER_FORMAT.md`: immutable base/split package model, identity/signing/dependency/config fields, source snapshot and content hashes.
4. `docs/engine/ACCEPTANCE_MATRIX.md`: one row per contract with status PASS/FAIL/UNKNOWN, evidence, test fixture, device matrix and release gate.
5. `docs/engine/ADR-001-provider-strategy.md`: compare licensed-provider, first-party in-process runtime and guest-OS/container paths with explicit constraints and a decision.

## Contract requirements

### Provider capabilities

The provider must expose an immutable capability snapshot that includes: supported APK source kinds; split cluster support; accepted ABIs/page sizes/Android API ranges; Activity/Service/Receiver/Provider support; intent resolution; resource/configuration support; framework-service mediation; per-clone storage/isolation level; update scope/atomicity; package/version/signer readback; background execution constraints; and known unsupported APIs.

### Package cluster

The model must identify package name, long version code, signer set/signing lineage where available, base APK, split APKs, split dependencies/configuration type, ABI/density/language targeting, file length and SHA-256 for every member, audit timestamp/source generation and immutable store generation. No caller-provided path may bypass canonical private-root validation. Mixed package/version/signer sets and missing dependencies must be rejected.

### Lifecycle

The contract must distinguish host manager activity from guest Activity lifecycle. Guest component states include discovered, validated, staged, installed, launch-requested, created, resumed, stopped, destroyed, crashed and unknown. Service/Receiver/Provider support must be independently declared; no Activity-only proof can imply all component types work.

### Isolation and security

Threat model must explicitly state whether the provider runs guest code under the host UID. If so, do not claim kernel-level isolation. Tests must attempt cross-clone access to files, databases, preferences, URI grants, notifications and process/service state. Secrets and credentials must never be copied from the source app by default. No device identity spoofing, anti-detection behavior, or crash suppression may be used as an acceptance substitute.

### Update transaction

Pin the source generation; serialize by actual provider scope; preserve all base/split members and signer identity; stage before mutation; use atomic publish if the provider supports it; do not destroy the last known-good generation until readback passes; record actual guest package/version/signer; retain snapshots on unknown state; and never claim session preservation without a dedicated test.

### Legal and supply chain

Pin source commits, record root and transitive licenses, provenance for every native prebuilt, dependency hashes, reproducible build steps, network/log/crash behavior, and release obligations. AGPL or GPL obligations require project-specific legal review; root repository license alone is not enough.

## Implementation gates after E35

- **E36**: private immutable package-cluster store and validation only; no guest execution.
- **E37**: provider interface/capability negotiation and truthful UI states.
- **E38**: owned synthetic guest lifecycle feasibility on a pinned emulator/device, only if a provider's architecture is legally and technically eligible.
- **E39**: framework mediation prototype with explicit unsupported cases.
- **E40**: adversarial account-isolation proof; stop or change architecture if required isolation cannot be met.
- **E41**: update transaction and readback proof.
- **E42**: device/ABI/page-size/release acceptance matrix.

Do not create implementation commits for E36–E42 before the E35 contract and ADR are reviewed against the product requirements. These are future gates, not completed work.

## Acceptance criteria

- Each capability has a machine-checkable status and evidence reference.
- Unsupported capabilities default to false and fail closed.
- `importsSplitApks=false`, `real_guest_activity_registered=false`, `kernel_uid_isolation=false`, and `production_clone_accepted=false` remain until independently verified.
- The ADR selects a path only if legal, source, security, lifecycle and isolation prerequisites are satisfied.
- Product score remains 28.00% weighted / ~30% rounded unless actual user-facing functionality passes its acceptance gate.
