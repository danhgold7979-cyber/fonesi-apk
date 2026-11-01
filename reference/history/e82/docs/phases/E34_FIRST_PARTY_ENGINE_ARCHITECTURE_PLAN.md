# E34 — first-party engine architecture and provider-boundary plan (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`. E30–E33 found no eligible provider with complete source, legal/provenance evidence and verified production contracts. E34 defines a clean-room architecture and decision framework; it does not start a broad rewrite or claim that a virtualized guest engine is implemented.

## Decision to resolve

Compare two paths with explicit evidence and cost:

- **Path A: obtain a properly licensed provider** that supplies complete source/build inputs and documents split import, guest lifecycle, framework mediation, account isolation, update/readback and current Android/native compatibility.
- **Path B: first-party provider** built from a documented, testable architecture. This is a substantial systems project, not a small adapter patch. It must not rely on copied proprietary engine code, hidden framework APIs without a supported strategy, crash suppression, identity spoofing or misleading host-Activity demos.

Do not select a path based only on a green synthetic CI or API name.

## Required architecture contracts

1. **Provider capability negotiation:** explicit immutable capabilities for single APK, split clusters, guest components, framework mediation, ABI/page size, update semantics and isolation level. Unsupported features fail closed in UI/planner/runner. No optimistic defaults.
2. **Verified package cluster:** a model for package/version/signing lineage, base and split roles, split dependencies/configs, ABI/density/language variants, hashes and source snapshot generation. Validate every member before publication; reject mixed versions/signers and incomplete dependency graphs.
3. **Private immutable store:** content-addressed or generation-addressed storage, canonical path containment, no symlink traversal, atomic publish, readback and garbage collection only when no active task references the generation.
4. **Guest package/component registry:** explicit model for Application/Activity/Service/Receiver/Provider metadata and exported/permission constraints. A manifest parse is not registration. Component launch requires a real dispatch implementation and lifecycle state machine, with crash/process-death handling.
5. **Framework mediation boundary:** documented interfaces for package metadata, intent resolution, resources/configuration, classloader/native libs, services/Binder, content providers, URI permissions, notifications and process state. Each interface has owned synthetic fixtures and fail-closed behavior.
6. **Data isolation:** explicit storage namespace per clone; path, file descriptor, URI grant, database, preferences, notification and process boundaries. Threat model must state whether the provider is same-UID virtualization or OS-level isolation. Never call same-UID storage separation a kernel sandbox.
7. **Lifecycle and scheduler:** host-visible/unlocked activation policy, guest process lifecycle, service/receiver/provider handling, background restrictions and restart recovery. Journal recovery may repair metadata but must never initialize or launch an unverified guest by itself.
8. **Update transaction:** pin source snapshot, determine engine update scope, prevent concurrent conflicting updates, stage and verify all cluster members, commit atomically only if provider supports it, read back installed guest state, and preserve data. If rollback or session preservation is not guaranteed, state that explicitly.
9. **Native compatibility and supply chain:** ABI allowlist, ELF `LOAD` alignment checks for 4 KB/16 KB page sizes, source provenance, license inventory, reproducible native build and dependency/SBOM manifest.
10. **User-facing capability truth:** UI status distinguishes manager availability, provider initialized, clone created, guest process started, guest lifecycle verified, account isolation verified and release acceptance. Never collapse them into one “Ready” status.

## Phase sequence for a first-party implementation (future work, not yet authorized by E34)

- **E35 contract/spec:** capability schema, threat model, package-cluster format, provider interface and decision ADR.
- **E36 private store:** immutable verified APK cluster, atomic publish/readback, corruption/path traversal/symlink tests. No guest execution.
- **E37 package metadata:** owned APK manifest parser/registry with signer/version/split dependency checks. No claim of Android component registration.
- **E38 lifecycle feasibility prototype:** owned synthetic payload only, on a dedicated supported test device/emulator, with real lifecycle state evidence and no third-party apps/accounts.
- **E39 framework mediation prototype:** narrow interfaces with owned fixtures, explicit unsupported cases and process-death tests.
- **E40 isolation threat proof:** adversarial cross-clone storage/URI/notification/process tests. If same-UID isolation cannot meet requirements, stop or change architecture to an OS/container boundary.
- **E41 update transaction:** source pinning, atomic generation swap where supported, readback and data-preservation tests.
- **E42 compatibility/release gate:** ABI/page-size/target SDK/device matrix, supply-chain review, release signing and recovery/rollback plan.

This is a proposed roadmap only. Do not create placeholder phases as if code exists, and do not increment product completion until each user-facing acceptance dimension passes.

## E34 deliverables

1. Record the two-path decision, rough engineering risks and hard blockers in `docs/phases/E34_FIRST_PARTY_ENGINE_ARCHITECTURE_RESULTS.md`.
2. Define an explicit ADR template and provider interface contract before implementing any engine code.
3. Keep SpaceCore opt-in and preserve `supportsSplits=false`, `importsSplitApks=false`, `real_guest_activity_registered=false`, `kernel_uid_isolation=false`, and `production_clone_accepted=false` until evidence changes.
4. Update `docs/STATUS.md` and `docs/PRODUCT_PROGRESS.md` without inflating the 28.00% weighted / ~30% estimate.

## Not claimed

E34 does not implement a guest runtime, register third-party components, guarantee account/session isolation, execute social apps, or produce a release APK. It is an architecture/feasibility gate.
