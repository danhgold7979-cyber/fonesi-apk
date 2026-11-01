# E30 — provider feasibility and production-engine acceptance gate (plan before code)

Date: 2026-10-29. Continue on cumulative branch `phase/e21-android-split-code-proof-20261028`; do not create a new branch per technical phase. Latest synthetic milestone E29 is green, but product engine acceptance remains blocked. This phase is an evidence-based engineering decision gate, not a synthetic-test milestone.

## Objective

Determine whether the currently pinned production provider can meet the actual Fonesi requirements without unsafe shortcuts. Produce a source/API-evidence decision matrix and a concrete go/no-go decision before changing production engine behavior.

## Required provider contracts

1. **APK cluster identity:** read base + split APK set as one immutable cluster; verify package, version, signing certificate lineage, split names, dependencies, ABI/density/language configs, and consistent file hashes before publishing.
2. **Private code store:** atomic cluster publication and readback after restart; no path escape, symlink traversal, partial publication, or stale source mixing.
3. **Real component lifecycle:** actual guest Activity/Service/Receiver/Provider declarations, explicit lifecycle dispatch, intent resolution, permission/export enforcement, and component state after process death. A host Activity/View proof is not sufficient.
4. **Framework mediation:** PackageManager/ApplicationInfo/PackageInfo, resources/configuration, classloader, native libraries, content providers, Binder/system services, notifications, files/URI permissions and process recreation must be explicitly covered.
5. **Account/session isolation:** separate persistent guest data and identity for each clone; demonstrate that one clone cannot read another clone's files/preferences/database/URI grants/notifications. A different label or clone ID is not isolation.
6. **Update safety:** source snapshot pinning, serialization by actual engine update scope, safe handling of unknown running state, atomic update where supported, readback of actual installed guest version, and preservation of user data/session where the engine contract allows it.
7. **Platform compatibility:** ABI and native ELF LOAD alignment (including Android 16 KB page-size devices), target SDK 36 behavior, background restrictions, and supported Android/OEM ranges. Never lower target SDK or hide crashes to force a pass.
8. **Supply-chain/legal:** pinned source commit, reproducible dependency provenance, license compatibility for source and native prebuilt artifacts, audit of network/log/crash/spoof/anti-detection code, and a documented upgrade path.

## Pre-code investigation

- Re-read docs/ENGINE_SPLIT_AUDIT.md, docs/EXECUTION_PLAN.md, docs/PLAN.md, and the current app/src/candidate adapter.
- Inspect the exact pinned SpaceCore AAR and public API wrappers used by the candidate. Record API signatures and behavior evidence with source/bytecode paths; do not infer functionality from README claims.
- Re-check candidate engine availability and importsSplitApks flags in source and tests. Confirm the current implementation fails closed for split sources and unavailable engine states.
- Inspect eligible source engines only at pinned commits. Do not run untrusted build scripts or import their binaries before provenance/license/security review.
- Keep the product acceptance matrix distinct from technical E21–E29 milestones.

## Deliverables

1. docs/phases/E30_PROVIDER_FEASIBILITY_RESULTS.md with a decision matrix containing each contract, exact source/API evidence, test status, severity, decision, and remaining proof.
2. A clear decision: (A) the current provider can support the product with a bounded implementation plan, (B) it cannot support one or more mandatory requirements and a provider replacement is required, or (C) evidence is insufficient and production integration stays blocked.
3. If the decision is B or C, keep production flags disabled and list the smallest next investigation that could change the decision. Do not make an arbitrary provider switch.
4. If evidence supports A, prepare a separate implementation plan for the narrowest production contract; do not code the whole engine before defining data migration, rollback, and test boundaries.
5. Update docs/STATUS.md and docs/PRODUCT_PROGRESS.md with E30 status while keeping the product score at 28.00% weighted / ~30% estimate unless a real product acceptance dimension has actually improved.

## Acceptance criteria

- The decision is based on inspected source/API behavior, not commit count, CI green alone, or marketing claims.
- Each mandatory requirement is marked PASS, FAIL, or UNKNOWN with evidence and a follow-up.
- No real third-party clone capability is claimed without a registered guest lifecycle and account-isolation proof.
- importsSplitApks=false remains unchanged until the provider genuinely imports, executes and reads back a complete base/split cluster.
- No unaudited third-party engine or native binary is added.
- Existing E26–E29 acceptance limits remain visible.

## Current known blocker

The previous E1/E2 source/bytecode audit found that the pinned SpaceCore public install API accepts the host package's sourceDir and does not expose a verified complete base+split import/readback contract. The candidate's native libraries also lack a verified 16 KB-compatible rebuild path. New source evidence may refine the conclusion, but do not override it without reproducing the proof.

## Explicitly not claimed

This phase does not deliver a production virtualized Android guest by itself. It does not prove social login, Samsung ARM64 compatibility, split import, kernel UID isolation, session-preserving updates, final release signing or completion of the 8 product phases / 32 steps.
