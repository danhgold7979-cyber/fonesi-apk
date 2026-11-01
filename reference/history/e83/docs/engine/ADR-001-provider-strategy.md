# ADR-001 — production provider strategy

- **Status:** Proposed; no production provider accepted.
- **Date:** 2026-10-29.
- **Decision owners:** Fonesi project maintainer/reviewer.
- **Related:** `docs/engine/PROVIDER_CONTRACT.md`, `docs/engine/THREAT_MODEL.md`, `docs/engine/PACKAGE_CLUSTER_FORMAT.md`, `docs/engine/ACCEPTANCE_MATRIX.md`, E30–E34 phase results.

## Context

Fonesi needs to manage multiple independent app instances without root, discover and validate installed app sources, create named launch entries, update without losing data, and provide truthful status. Mandatory app targets include social/e-commerce apps, so real component lifecycle, framework mediation and account isolation are required; UI-level duplicate entries are insufficient.

The current SpaceCore candidate is opt-in and passes narrow single-APK checks, but the adapter requires one APK path and disables split support. The pinned public API/bytecode audit did not find a complete base+split import/readback contract, and its native set has no verified 16 KB rebuild path. E21–E29 prove signed synthetic code/resource/View/component-ticket behaviors and durable scheduling/recovery, but explicitly do not register a real guest Activity or prove third-party execution/account isolation.

E30–E33 examined alternative source engines. The strongest apparent split API, WaxMoon/MultiApp, delegates to an opaque `moon.jar` and has AGPL-3.0 obligations and stale engine source. ServenScorpion/VirtualApp bundles an opaque AAR without established source/license provenance. Audited BlackBox forks have known cluster/resource/security/native-provenance gaps. No alternative passed the eligibility gate.

## Options

### A. Continue with pinned SpaceCore and build around current APIs

- Pros: already integrated as a constrained opt-in candidate; existing single-APK checks and test suite can be retained.
- Cons: cannot satisfy split APK targets on current evidence; 16 KB support, guest lifecycle and account isolation remain unresolved. An adapter cannot implement capabilities absent from the engine.
- Decision: retain as a restricted experimental provider, not as the accepted production engine.

### B. Integrate an alternative engine immediately

- Pros: some forks expose package manager, activity stack and split-directory APIs.
- Cons: inspected candidates have opaque artifacts, incomplete split semantics, risky behavior, stale source or license/provenance gaps. Importing them would transfer unknown risk into Fonesi.
- Decision: reject immediate integration. Reconsider only with complete source, license, native provenance and a reproducible build.

### C. First-party in-process virtualization engine

- Pros: full control over source, capability contract, data model and supply chain.
- Cons: very high engineering cost and framework/OEM compatibility burden; guest lifecycle and Binder/service mediation are not simple app-layer features. Same-UID execution may not meet strong account-isolation requirements.
- Decision: do not commit to full rewrite yet. Use E35/E36 to define contracts and build a non-executing private package store; require explicit feasibility and isolation gates before guest execution work.

### D. Guest OS/container/VM architecture

- Pros: potentially clearer OS-level isolation boundary.
- Cons: changes product architecture, guest image model, performance, storage, integration and device requirements. Not a drop-in app-cloning engine.
- Decision: reserve as a requirements-change option only.

## Decision

1. Keep SpaceCore opt-in and do not enable split imports.
2. Do not integrate an alternative opaque AAR/JAR or unaudited fork.
3. Complete the provider contract, threat model, package-cluster format and acceptance matrix before engine implementation.
4. Next implementation may build a private immutable package-cluster store and validator only; it must not claim to execute or isolate guest apps.
5. After that, choose either a source-complete licensed provider or a narrowly scoped first-party feasibility prototype based on explicit tests and risk review.
6. If same-UID virtualization cannot meet the product's account-isolation requirement, stop the in-process path and reconsider a guest-OS/container architecture or revise the product requirement explicitly.

## Consequences

- Production clone readiness remains blocked. `supportsSplits=false`, `importsSplitApks=false`, `real_guest_activity_registered=false`, `kernel_uid_isolation=false`, and `production_clone_accepted=false` remain in force.
- Existing UI, update orchestration, Room journal, WorkManager and synthetic CI work is retained; no persisted clone data is silently migrated to another provider.
- The 28.00% weighted / ~30% rounded product estimate does not increase because E35 creates specifications rather than shipped functionality.
- Any future provider proposal must update this ADR with source revision, license/SBOM, exact acceptance evidence and rollback plan.
