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
