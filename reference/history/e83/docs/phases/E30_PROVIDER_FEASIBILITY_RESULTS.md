# E30 — provider feasibility and production-engine acceptance results

Date: 2026-10-29. Scope: inspected the current candidate adapter and the pinned SDK evidence already recorded in docs/ENGINE_SPLIT_AUDIT.md, then compared them with the product contract in docs/PLAN.md. This is source/API review; it is not a physical-device acceptance run.

## Decision

**Decision B/C: the pinned SpaceCore provider is not accepted for production clone requirements.** The source/API evidence shows hard gaps in split-cluster import and 16 KB native compatibility; real guest component registration and per-clone OS-level account isolation remain unproven. Keep the provider opt-in and keep unsupported capabilities disabled. Do not switch to an unaudited fork merely to remove the block.

## Evidence matrix

| Contract | Evidence inspected | Status | Decision / next proof |
| --- | --- | --- | --- |
| Base + split APK cluster import | `app/src/candidate/java/vn/fonesi/app/runtime/SpaceCoreBackend.kt`: `checkedInput` requires exactly one `apkPaths` entry; `RuntimeBindings.supportsSplits=false`. `docs/ENGINE_SPLIT_AUDIT.md` records the pinned public install API does not expose a complete base+split import/readback contract. | **FAIL** | Keep `importsSplitApks=false`; do not drop, flatten, merge or re-sign splits. Need an audited provider with cluster-aware install/readback. |
| Package/version/signer/hash preflight | `SpaceCoreBackend.checkedInput` uses archive metadata, `ApkSetAuditor.verify`, signer/version equality and SHA-256; post-install checks guest metadata and an SDK-owned private copy. | **PASS — single APK only** | Preserve these checks. They do not generalize to split clusters until the provider exposes cluster identity and readback. |
| Private code copy | `SpaceCoreBackend.ownedCode` checks the reported source path is a file under `context.dataDir`, and excludes the create/update snapshot roots; `install` verifies independent copy and hash. | **PASS — narrow path invariant** | Need real-device/restart evidence and atomic private cluster store before production acceptance. |
| Real guest Activity/Service/Receiver/Provider lifecycle | E21–E29 receipts explicitly report the host Activity and synthetic View, with `real_guest_activity_registered=false`. E29 ticket dispatch does not execute the declared `PayloadActivity` as a framework guest Activity. | **FAIL / NOT IMPLEMENTED** | Need provider-mediated component registration/dispatch and Android lifecycle proofs for project-owned payloads before third-party app tests. |
| Framework services and package identity | Current E29 bridge only validates a private catalog and dispatch ticket. It does not provide guest PackageManager/Binder/permission/URI/provider mediation. | **UNKNOWN / BLOCKING** | Inventory and implement actual provider contracts, then run owned component fixtures with explicit permission and service tests. |
| Per-clone account/session isolation | Candidate creates engine users and persistent registry IDs, but E21–E29 proofs do not test guest filesystem/database/preferences/URI grants or cross-clone reads using real guest processes. `docs/PRODUCT_PROGRESS.md` marks this acceptance dimension 0%. | **UNKNOWN / NOT ACCEPTED** | Require a threat model and adversarial cross-clone tests at the actual isolation boundary. Do not claim separate accounts based on IDs or labels. |
| Native ABI and 16 KB page size | `RuntimeBindings.attach` calls `ProbePolicy.rejectionReason` with OS page size; `docs/ENGINE_SPLIT_AUDIT.md` says the pinned native library set is ARM/4 KB aligned and lacks corresponding source for a verified 16 KB rebuild. | **FAIL for 16 KB / restricted candidate** | Keep runtime guards. Need source/provenance and rebuilt ELF alignment audits for every shipped ABI before broad Android compatibility claims. |
| Update scope/readback/data preservation | `ManagedGuestEngine` and `SpaceCoreBackend` have update orchestration and post-install package/version/signer checks; the real app's session-preserving update behavior has not been accepted on device. | **PARTIAL / NOT ACCEPTED** | Continue only after provider update scope and data semantics are proven with owned fixtures, then supported third-party apps on a device. |
| Supply chain and license | Pinned AAR SHA and NOTICE are recorded in `docs/ENGINE_SPLIT_AUDIT.md`; source-engine candidates have unresolved native prebuilt/provenance and behavior-audit issues. | **PARTIAL** | Do not vendor another provider until source commit, dependency/license inventory, native provenance, logging/network/crash/spoof audit and reproducible build are complete. |
| Cloud/candidate build boundary | E26–E28 cloud/candidate CI audits pass on their tested SHAs; E29 AOSP34 signed synthetic ticket dispatch passes on `10fc6b623a42...`. E29 receipt retains false production acceptance flags. | **PASS — test artifact only** | Preserve explicit false flags; a green synthetic CI does not change the provider decision. |

## Implementation decision

1. Keep SpaceCore candidate opt-in, constrained by its API/ABI/page-size guards. Do not enable split support.
2. Do not migrate persisted clone metadata to another provider until provider identity, schema namespace and data migration/rollback have been designed and tested.
3. Do not import NewBlackbox/BlackBox forks as a shortcut: prior source review found base-only storage/resource paths, incomplete cluster metadata, fallback behaviors that are not valid acceptance evidence, crash/log/spoof components and unresolved native binary provenance.
4. The next implementation gate is **E31 — provider replacement shortlist and threat-model decision**. Compare only pinned, inspectable source candidates against the same contract matrix; produce license/provenance and technical feasibility evidence before any code or binary is imported.

## Product completion

The weighted product estimate remains **28.00% / ~30% rounded**. E30 is a decision gate, not a newly accepted user-facing product capability. Real third-party clone acceptance remains **0 applications**; full 8 product phases / 32 steps remain incomplete.
