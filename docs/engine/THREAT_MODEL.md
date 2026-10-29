# Fonesi Engine Threat Model — E35 draft

Status: SPECIFICATION ONLY. No production guest engine is accepted. This document defines the minimum threat model before any provider integration or first-party runtime prototype.

## 1. Assets to protect

- User credentials and sessions inside each guest app.
- Each clone's private files, databases, preferences, caches, URI grants, notifications and process state.
- Original installed app data and package identity.
- APK source bytes, signing identity, split dependency graph and update generation.
- Fonesi access-control state, provider configuration, clone registry and update journal.
- Integrity of the Fonesi host process, source snapshots and release artifact.

## 2. Security principals and trust boundaries

1. **Host manager UI**: trusted only after foreground access is unlocked. UI strings, clone names and IDs are not security boundaries.
2. **Provider engine**: untrusted until source/dependency provenance and runtime tests pass. Its APIs may return stale or incomplete state.
3. **Guest app code**: third-party code and data; assume it can be malformed, buggy or hostile to other clones. Do not assume it cooperates with the virtualization layer.
4. **Original installed app**: separate source of package metadata and APK bytes; never use it as evidence that a private guest instance is installed or running.
5. **Filesystem and APK inputs**: untrusted. Reject path traversal, symlinks, mutable files during hashing, corrupt ZIP/APK entries, mixed versions/signers and incomplete split sets.
6. **Android framework/OEM behavior**: external platform boundary. Hidden APIs, background scheduling, permission behavior and process lifecycle can vary by OS/OEM.
7. **Build/dependency supply chain**: source repositories, AAR/JAR/native archives and transitive dependencies must be pinned and audited separately.

## 3. Threat actors and failure modes

- A guest app tries to read or modify another clone's files, preferences, databases, URI grants or notifications.
- A malformed source APK or split set attempts path traversal, symlink escape, ZIP bomb/resource exhaustion, duplicate split names or mismatched signer/version metadata.
- A process dies after partial install/update but before journal commit; a retry could duplicate or corrupt mutation.
- Two updates race against one package when the engine uses a shared global code store.
- An opaque provider binary performs network/logging/crash/spoof behavior not visible in source.
- The UI or planner treats a synthetic host Activity/View as a real guest launch.
- A test or report incorrectly turns unknown/unsupported provider state into a successful clone status.
- A stale WorkManager task runs after the app is locked or after the provider was disabled.

## 4. Isolation claim levels

- `HOST_UID_NAMESPACE`: separate logical paths under one Android UID. This is not kernel isolation; guest code with host-process access may cross namespaces unless mediation is comprehensive.
- `SEPARATE_OS_UID`: distinct Android OS UID/sandbox, independently verified by the platform. A different engine user ID does not prove this.
- `SEPARATE_GUEST_OS`: separate guest OS/container/VM boundary with a documented host/guest channel.
- `UNKNOWN`: no isolation claim permitted.

The product must not advertise independent account isolation until the required level is stated and adversarial cross-clone tests pass at the actual provider boundary. If same-UID mediation cannot meet the product threat model, stop or change to an OS/container architecture.

## 5. Required controls

- Immutable verified package cluster with per-file SHA-256 and package/version/signer consistency checks.
- Canonical private-root containment and symlink rejection at every open/copy/delete boundary.
- Atomic publish and readback; no partially written generation can become active.
- Unique operation IDs, state transitions with compare-and-swap/revision checks, idempotent recovery and safe `REVIEW_REQUIRED` handling.
- Explicit provider capabilities; unknown or unsupported operations fail closed.
- No automatic credential/session copying from original apps.
- No hidden identity spoofing, anti-detection, or crash suppression as compatibility controls.
- No unreviewed telemetry/log upload; logs must exclude secrets, package data and session material.
- Foreground/unlocked policy for mutations; background worker may reconcile journal metadata only.
- Pinned source revisions, full license inventory, native artifact provenance, reproducible build and signed release artifacts.

## 6. Acceptance tests

1. Clone A cannot read Clone B's private files/preferences/database or obtain its URI grants.
2. Guest notification/service/provider state cannot be confused across clones.
3. A stale or malformed cluster is rejected before any write.
4. Symlink/path traversal cannot escape the private store, including during cleanup.
5. Process death at each install/update transaction boundary leaves either a verified old generation or a review-required state, never a false success.
6. Guest lifecycle claims are backed by actual provider lifecycle evidence, not host UI events.
7. A disabled/locked manager cannot trigger provider initialization or guest mutation.
8. The release dependency/native manifest matches reviewed source and licenses.

## 7. Explicit exclusions

This model does not claim that the current product satisfies these controls. E21–E29 synthetic tests verify narrow code-loading, metadata and journal behavior only. Real third-party app execution, account isolation, target-device compatibility and release acceptance remain unverified.
