# E83 — production split-provider contract and fail-closed adapter boundary (pre-code plan)

Date: 2026-10-30. Repository: `danhgold7979-cyber/fonesi-apk`.
**Product mapping:** Phase 1 steps 5–8 (engine selection, integration and controlled app proof), preparing Phase 3 step 15 and Phase 4 update safety. E83 is a technical milestone, not a new Product Phase.

## Exact baseline

- E82 source/test SHA: `a223af7e539bff78891786fc6e02d4ea83ccdb0a`; [Full workflow #41057227010](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41057227010) **SUCCESS**, 21/21 substantive steps.
- [E82 TEST-ONLY artifact #12977034713](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41057227010/artifacts/12977034713): exact source SHA and APK hashes; AOSP API 34 receipt confirms framework onCreate/onResume, non-null host Context/Window, one-shot factory authorization, host stub non-exported and host UID 10142. It explicitly records guest PackageManager registration=false, guest UID isolation=false, third-party execution=false, social accounts=false, production clone=false, physical device=false.
- E82 is a host-owned proxy Activity lifecycle proof only. It does not provide a guest package registration or guest UID.
- Existing E21/E22/E23 test-only Android receipts prove respectively a synthetic two-member isolated base/feature set, a synthetic base→alpha→beta dependency chain, and a synthetic French config split on AOSP API 34. None is a production guest provider.
- `ApkSetAuditor` and `PrivateApkStore` already verify/publish immutable signed multi-member clusters. `GuestSplitGraphPlanner` builds a deterministic private metadata DAG but explicitly is not a ClassLoader/Resources/PackageManager runtime.
- Candidate `RuntimeBindings.supportsSplits=false`; `SpaceCoreBackend.checkedInput` requires exactly one APK and calls `FCore.installPackageAsUser(File,...)`; `ManagedGuestEngine.capabilities.splitClusterImportSupported=false`. The pinned SpaceCore source/API audit in `docs/ENGINE_SPLIT_AUDIT.md` found no public base+split import/readback protocol. This restriction must remain until a provider proves the entire contract.

## Goal

Define and test an explicit provider boundary for installing a complete, cryptographically verified base/split cluster into SDK-owned guest storage. Make split support a set of independently verified capabilities, not a Boolean inferred from successful APK parsing, private copying, test classloaders, or the E82 host factory. Preserve current single-APK SpaceCore behavior and reject split installs before any irreversible SDK/user/registry mutation.

## Pre-code source findings and constraints

1. `InstalledSource` currently carries APK paths, package/version/signers; `ApkSetAuditor` returns exact base/split metadata, dependency/type graph and member SHA values.
2. `PrivateApkStore.publish/open` retains signed immutable clusters and exact member identity; this is not engine import.
3. `GuestBackend.install(userId, source)` has no cluster-specific receipt/readback API. `SpaceCoreBackend` rejects `apkPaths.size != 1` and only passes the base File to the pinned SDK.
4. `ManagedGuestEngine.create` freezes/preflights source before native user creation, but its split refusal and the backend capability must be explicitly preserved before any native mutation. Shared updates must reject unsupported clusters before dispatch.
5. Do not invent an SDK overload, claim the pinned SDK accepts splits, flatten/re-sign APKs, silently discard members, or use the original installed app as a fallback.

## Implementation sequence

1. Add a source-level contract plan and test matrix before code (this document).
2. Introduce a typed cluster-install provider contract in core only if it can remain backward-compatible for existing single-APK providers. Model exact expected cluster identity: package, long version, signer set, base SHA, sorted split names/member SHAs, dependency/config metadata and deterministic cluster digest.
3. Separate capability flags for cluster preflight, import dispatch, complete member readback, resource/code execution proof and atomic update/readback. Defaults remain false. A provider cannot report `splitClusterImportSupported=true` unless all required import/readback stages have accepted evidence.
4. Keep SpaceCore as single-APK only. It must reject a multi-member source before createUser, SDK install, update dispatch or mutation of Room state; keep `RuntimeBindings.supportsSplits=false`.
5. Add protocol tests with a fake split provider for complete-cluster identity and negative cases: missing/extra member, changed SHA, wrong package/version/signer, duplicate/unknown split, wrong dependency graph, incomplete readback, changed cluster during import, stale provider namespace, failed atomic update. These are core contract tests only and must be labelled as such.
6. Add explicit SpaceCore regression tests/static audit showing it still rejects every multi-member set before calling FCore and before irreversible mutations. Keep all E40–E82 audits and E21–E23 synthetic workflows unchanged.
7. Do not implement a fake production provider. If no candidate SDK with a documented and source-verified complete-cluster import/readback API exists, the correct E83 result is a fail-closed provider boundary plus an explicit blocker and a follow-on candidate evaluation plan.
8. Run cloud and candidate test/build/lint/APK audits, source-engine AAR/JVM and inherited E40–E82 audits on the exact source SHA. A contract/JVM PASS is not Android split runtime or product acceptance.

## Acceptance criteria

- Multi-member source cannot reach a single-APK provider's irreversible create/install/update path.
- Provider capabilities are independently represented and default false.
- Exact signed cluster identity is stable and read back after provider operations.
- Unsupported/incomplete providers fail closed without dropping split members or rewriting signatures.
- SpaceCore remains single-APK; `importsSplitApks=false`, `supportsSplits=false`.
- No change to product readiness until an actual production provider imports, reads back and executes the required signed split cluster on Android and subsequent device gates are met.

## Explicit exclusions

No arbitrary third-party guest Activity/Service/Provider lifecycle, independent OS UID/account isolation, Play Feature Delivery, notifications/Play Services, Samsung ARM64 acceptance, user account login, session-preserving updates or release APK. E82 host-stub proof does not satisfy any of these.

## Branch and progress controls

- Continue on `phase/e82-public-component-factory-feasibility-20261030`; PR #56 stays Draft and stacked on E81. Do not create a duplicate branch.
- Keep `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` unchanged.
- Product readiness remains 28.00%; accepted real third-party clones remain 0.


## E83 follow-up 1 — exact-source Kotlin test compilation failure and contract hardening plan (30/10/2026)

Baseline source/reporting HEAD before remediation: `0075521db86e20a4498cb2615c9a68b79beee0c8`, branch `phase/e82-public-component-factory-feasibility-20261030`, Draft PR #56. Full workflow [#41060651194](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41060651194) failed in `:core:compileTestKotlin` at `core/src/test/kotlin/vn/fonesi/core/EngineCapabilitiesTest.kt:96`: unresolved reference `assertEquals`. The E83 static audit and E78–E82 receipt regressions ran successfully before the Kotlin compile failure; later candidate/build/emulator gates were skipped. Authorization subgate [#41060651326](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41060651326) passed but is not the full E83 acceptance gate.

### Remediation scope, before source edits

1. Add the missing JUnit `assertEquals` import; do not alter or weaken the assertion.
2. Tighten `GuestApkClusterIdentity` structural checks to match the auditor's package/split naming constraints, require the base member first and split members in deterministic name order, and reject invalid feature/config relationships while retaining iterative cycle detection.
3. Add `GuestApkClusterReadback.requireMatches(expected)`: validate both identities, require exact package/version/signers/member metadata/digest equality, and require both SDK-owned-private-storage and complete-member-set evidence. Add positive and negative regressions for changed member SHA, package/version/signers, incomplete member set and non-private readback.
4. Require any backend that advertises a split-provider capability to implement `SplitClusterGuestBackend`; a partial Boolean claim without the typed API must fail closed.
5. Keep engine-level split import/code execution/atomic-update acceptance false until `ManagedGuestEngine` actually orchestrates the typed provider. Preserve `SpaceCoreBackend` one-APK guard and `RuntimeBindings.supportsSplits=false`.
6. Add a shared-update regression proving a multi-member source is rejected before any backend install or registry mutation. Retain create-time pre-SDK rejection.
7. Run the exact-source cloud/candidate workflow and inherited E40–E83 audits. E83 remains a contract-boundary milestone only; no production provider, guest split execution, UID/account isolation or product acceptance is claimed.

Stop if a real provider or SDK API is needed: do not invent an overload or enable a capability without source-verified SDK support and complete import/readback evidence.


## E83 follow-up 2 — identity completeness parity and parent-receipt packaging (30/10/2026)

### Exact-source baseline before this follow-up

- Branch `phase/e82-public-component-factory-feasibility-20261030`, source/reporting HEAD `13525e009984897f705b9f8dad18cfebf08a7709`, Draft PR #56 stacked on E81 Draft PR #55; `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` unchanged.
- Full exact-source workflow [#41063943128](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41063943128) **SUCCESS**, all 21 substantive steps. Authorization subgate [#41063943130](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41063943130) **SUCCESS**.
- Cloud and candidate each: 517 tests, 0 failures/errors/skips. Source-engine: 63 JVM tests, 0 failures/errors/skips. Signed synthetic payload: 14 JVM tests, 0 failures/errors/skips. Candidate/cloud build, lint and APK audits passed.
- E82 AOSP API 34 receipt is bound to this exact SHA and both APK hashes. It confirms factory authorization, framework onCreate/onResume, non-null host Context/Window, non-exported host stub, host package/UID 10142; it explicitly records guest PackageManager registration=false, guest UID isolation=false, third-party execution=false, social accounts=false, production clone=false and physical device=false.
- [E82 exact-source test-only artifact #12982089451](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41063943128/artifacts/12982089451) contains the receipt and reports. The E78 parent artifact [#12952618461](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40998462184/artifacts/12952618461) remains available separately.

This is a full exact-source technical pass for the current contract prototype and E82 host-stub regression. It is **not** the final E83 identity-contract acceptance: the source audit below found that the typed identity does not yet preserve every field committed by the APK auditor/private-store digest. Do not mark E83 final or change the product score until this follow-up passes.

### Source-audit findings requiring correction before more code

1. `ApkSetAuditor` uses split-name grammar `[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*`; `GuestApkClusterIdentity` currently allows a numeric-leading segment after a dot. This permits an identity the APK auditor would reject.
2. `ApkSetAuditor` accepts an empty `configForSplit` on a non-feature config split (it means no feature dependency), and validates `uses-split` dependencies on all split members. The current core model rejects empty config names and rejects dependencies on non-feature split members, so it is not behaviorally identical to the source auditor.
3. `AuditedApk` carries `requiredTypes`, `providedTypes`, `splitRequired`, `isolatedSplits`, verified SHA-256 and file length. The current `GuestApkClusterMemberIdentity` omits those fields. `PrivateApkStore.computeClusterDigest` commits all of them, while the core identity currently treats `clusterDigest` as an opaque 64-character string. Equality/readback can therefore omit metadata that the existing audited/private cluster digest already protects.
4. `EngineCapabilities` is less strict than `GuestBackendCapabilities`: it can accept `splitClusterCodeExecutionVerified=true` without accepted import, and `splitClusterAtomicUpdateVerified=true` without code-execution proof. Both models must enforce the same dependency chain.
5. E82 artifact #12982089451 correctly contains the E82 receipt, but the workflow's separate E78 parent-upload step warned that `app/build/reports/e78-emulator/e11-evidence.json` and `synthetic-ui-receipt.json` were absent. The E82 workflow does not itself run E78, so it must explicitly download and verify the exact E78 parent artifact instead of pointing at an unpopulated local path.

### Bounded implementation plan — commit this plan before source changes

1. Extend `GuestApkClusterMemberIdentity` to preserve the auditor's complete digest-relevant metadata: positive member byte length, `requiredTypes`, `providedTypes`, `splitRequired` and `isolatedSplits`, alongside split name, SHA, feature, config and dependencies. Missing byte length must fail closed.
2. Match `ApkSetAuditor` exactly: package length limit; same split-name grammar; empty configForSplit accepted only for non-feature config splits; all dependency targets must be feature splits; non-feature `uses-split` metadata remains representable; base constraints, required/provided split-type coverage, base `isSplitRequired` and iterative acyclic graph validation are retained.
3. Move the existing length-prefixed `fonesi-package-cluster-v1` digest algorithm into a shared core helper on `GuestApkClusterIdentity`. Structural validation must recompute and compare the digest rather than accept any syntactically valid SHA. Make `PrivateApkCluster.identity` map the actual `AuditedApkSet` metadata and measured file lengths into this shared identity; `PrivateApkCluster.clusterDigest` must delegate to it so existing cluster digest semantics remain byte-for-byte stable.
4. Align `EngineCapabilities` with `GuestBackendCapabilities`: code execution requires accepted cluster import; atomic update requires both accepted import and code-execution proof. Add positive/negative dependency-chain tests.
5. Add tests for numeric-leading split segments, empty configForSplit, non-feature dependencies, missing required types, base split-required without splits, metadata/digest tampering, byte-length validation, package length, and private-store identity/digest parity across input ordering/readback.
6. Strengthen `audit_e83_split_provider_contract.py` to require the canonical digest and metadata invariants, while continuing to assert that ManagedGuestEngine does not surface split capabilities, SpaceCore still rejects multi-member input before FCore, and RuntimeBindings.supportsSplits remains false.
7. Fix E82 evidence packaging: grant only the workflow's required `actions: read` permission, download the pinned E78 parent artifact from run #40998462184 by its exact artifact name, validate its E78 source SHA/result/APK hashes and false guest/UID claims, then place the verified parent receipt in the E82 artifact bundle. Do not treat a missing E78 parent artifact as a successful upload.
8. Run focused `:core:test` and `:apk-runtime:test`, the E83 static audit, then the complete exact-source cloud/candidate/source-engine/E82 workflow. Do not weaken tests, change the existing APK auditor's acceptance rules, invent a provider or enable split support.

### Acceptance and product boundary

- Core identity and private-store cluster digest use one canonical algorithm and preserve all metadata the APK auditor has already verified.
- A valid identity is exactly compatible with `ApkSetAuditor` semantics; an altered/missing field cannot pass readback.
- Capability dependency invariants are identical at backend and engine boundaries.
- E82 workflow artifacts contain both the new exact-source E82 receipt and the explicitly verified E78 parent receipt.
- SpaceCore remains one-APK only; `importsSplitApks=false`, `supportsSplits=false`; no production split provider, guest split execution, separate UID/account or physical-device acceptance is claimed.
- Product readiness remains **28.00%**; accepted real third-party clones remain **0**.
