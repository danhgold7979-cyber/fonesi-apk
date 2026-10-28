# Fonesi Engine Provider Contract — E35 draft

Status: SPECIFICATION ONLY. No provider is currently accepted for production. The pinned SpaceCore adapter remains opt-in and split support remains disabled.

## 1. Contract principles

- The provider is an untrusted capability boundary until its implementation, dependencies and runtime behavior have passed acceptance.
- Unsupported behavior must be explicit and fail closed. A method returning without an exception is not proof that a guest operation succeeded.
- All mutating operations require a caller-issued operation ID, immutable source generation, foreground authorization policy, and durable journal transition.
- Host UI lifecycle and guest lifecycle are distinct. Host Activity creation must never be reported as guest Activity creation.
- Provider identity is namespaced. A replacement provider must not read or mutate another provider's persisted clone records unless an explicit, tested migration is performed.

## 2. Capability snapshot

A provider must expose a read-only capability snapshot with at least:

- `providerId`, `providerVersion`, `sourceRevision`, `dependencyManifestDigest`;
- `ready`, `initializationState`, `initializationFailureCode`;
- supported API range, ABI allowlist and supported page sizes;
- `supportsSingleApk`, `supportsSplitClusters`, `supportsGuestActivity`, `supportsGuestService`, `supportsGuestReceiver`, `supportsGuestProvider`;
- resource/configuration, native-library, intent-resolution and framework-service mediation levels;
- `dataIsolationLevel` with enumerated values `HOST_UID_NAMESPACE`, `SEPARATE_OS_UID`, `SEPARATE_GUEST_OS`, `UNKNOWN`;
- update scope (`PER_CLONE`, `PER_PACKAGE`, `GLOBAL_PACKAGE`, `UNKNOWN`), atomicity and readback guarantees;
- unsupported component/API lists and known platform restrictions.

`UNKNOWN` is not a supported capability. UI/planner must not enable operations whose required capability is false or unknown.

## 3. Operations

### Inspect source

Input: `VerifiedPackageCluster` plus operation ID. Output: exact package identity, version, signer set/lineage when available, cluster member hashes, split dependency graph, manifest component catalog, ABI requirements, permissions and an explicit compatibility decision. It must not query the installed original package as a substitute for private source metadata.

### Stage/install

Input: immutable cluster generation and target clone ID. Output: operation ID, provider package instance ID, installed identity readback, storage namespace ID and explicit install result. A successful return requires reading the installed guest identity back from the provider. Partial or ambiguous install returns `UNKNOWN/REVIEW_REQUIRED`, not success.

### Launch/stop/query lifecycle

Launch must resolve a declared guest component from the private registry and return a provider-owned guest instance ID. Lifecycle events must identify whether they come from the actual guest runtime or only the host proxy. Stop/query must be scoped to the exact provider instance and clone.

### Update

Input: pinned source generation and the provider's declared update scope. Before mutation, query affected instances and running state. If scope or running state is unknown, defer. Output includes before/after package identity, cluster generation, data-preservation status and readback receipt. Do not promise rollback or session preservation unless provider guarantees and tests it.

### Remove

Removal is scoped to a provider instance and clone data namespace. It must not delete another clone's data, shared code generation still referenced by other instances, or the host app. Data deletion requires explicit user authorization and a durable audit record.

## 4. Common result model

Every mutating operation returns one of:

- `SUCCEEDED_VERIFIED`: operation and postcondition independently read back;
- `FAILED_NO_CHANGE`: provider proves no mutation occurred;
- `FAILED_ROLLED_BACK`: provider proves rollback to prior verified generation;
- `DEFERRED`: safe to retry later, no mutation occurred;
- `REVIEW_REQUIRED`: mutation may have occurred but state is ambiguous;
- `UNSUPPORTED`: capability absent;
- `SECURITY_REJECTED`: signature, identity, path, dependency or isolation check failed.

Do not convert exceptions, timeout, process death or missing readback into `SUCCEEDED_VERIFIED`.

## 5. Invariants

- Base and split members belong to the same package/version/signer identity and validated dependency graph.
- Every source path is canonicalized and contained under an approved root; symlinks and path traversal fail closed.
- Source generation is immutable while a task references it.
- No clone launch, source install, account import or update occurs in the background recovery worker.
- No credentials or source-app session data are copied by default.
- All clone operations are authorized while foreground access policy is satisfied.
- Provider capability changes require re-running acceptance tests and updating the capability snapshot digest.

## 6. Required test interface

A provider is not accepted until it passes owned-fixture tests for: identity mismatch, signer mismatch, missing/extra split, dependency cycle/missing dependency, corrupt APK, path traversal, symlink escape, partial publish, restart/readback, duplicate operation ID, process death at each transaction boundary, concurrent conflicting updates, wrong-clone access, guest lifecycle process death, component permission/export enforcement and unsupported ABI/page size.

## 7. Current implementation mapping

Current `SpaceCoreBackend` is only a candidate adapter. It has single-APK identity/hash checks and post-install private-copy checks, but `checkedInput` requires exactly one APK and `RuntimeBindings.supportsSplits` is false. It does not satisfy this complete contract. Keep all production acceptance flags false until a provider passes the matrix.
