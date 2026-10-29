# E32 — candidate source acquisition and provenance gate (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`. E30 found that the pinned SpaceCore candidate does not satisfy production split/16 KB requirements; E31 found no alternative in the currently inspected shortlist that is eligible for integration. E32 is a source acquisition and audit gate, not permission to import an arbitrary fork.

## Objective

Identify whether a viable engine can be sourced with a complete, inspectable and legally usable source/dependency set. Do not change production engine behavior in this phase.

## Candidate discovery rules

1. Start with upstream maintainers and exact canonical repositories, not copied APKs, repackaged AARs or forks with undocumented modifications.
2. For every candidate, capture repository URL, pinned commit, source tree completeness, current maintenance signals, root license, native and transitive dependency licenses, and build reproducibility requirements.
3. Reject candidates if any native prebuilt has unknown provenance, required source is missing, build instructions depend on private downloads with unclear rights, or security-sensitive code cannot be removed/audited.
4. Do not execute third-party Gradle/CMake scripts, install candidate APKs, load candidate binaries or upload proprietary project data while triaging.
5. Compare architecture, not README claims: package cluster representation, real component lifecycle, framework service mediation, native ABI/16 KB readiness, data isolation and update/readback contracts.

## Work sequence

1. Use GitHub repository search and public upstream metadata to find at most 5 plausible candidates. Do not widen into unrelated Android app cloners without source code.
2. For each candidate, pin the default-branch HEAD and inspect its tree/readme/license/source files. Record commit SHA before any detailed analysis so evidence is reproducible.
3. Inspect high-risk source paths first: APK install/copy/storage, package parser/parcel, resource loader, activity/service/provider dispatch, user/data paths, native build files, crash/logging/network hooks and dependency manifests.
4. Score each required contract PASS/FAIL/UNKNOWN and mark whether evidence comes from code, tests, or claims. Unknown is not a pass.
5. Select at most one candidate for a subsequent isolated E33 proof only if all minimum source/license/security gates pass. Otherwise record NO-GO and the missing prerequisite (for example, source release, native source, license grant or maintainer clarification).
6. If selected, write E33 plan before code. Require a new engine namespace, isolated data store, owned synthetic payloads, exact manifest/permission/ELF audits, negative security tests and a rollback plan. Never reinterpret existing SpaceCore journal data as belonging to a replacement engine.

## Deliverables

- `docs/phases/E32_CANDIDATE_SOURCE_PROVENANCE_RESULTS.md` with pinned candidates, file-level evidence, license/provenance status, contract matrix, rejection reasons and recommendation.
- Update status and product progress while preserving the weighted score at 28.00% / ~30% unless real product functionality is accepted.

## Stop conditions

If all candidates fail minimum criteria, stop. Do not create a mock provider, falsely set engine availability to Ready, or claim a working clone. Keep `importsSplitApks=false` and all production-acceptance flags false. State precisely what source/provenance or engineering capability is missing.

## Not claimed

E32 does not deliver a production clone engine, real guest Activity registration, account isolation, third-party app acceptance, 16 KB compatibility or a release APK.
