# E33 — source acquisition and production-engine go/no-go (plan before any import)

Date: 2026-10-29. Continue on cumulative branch `phase/e21-android-split-code-proof-20261028`. E30–E32 have not found a provider eligible for production integration. This phase is to obtain or independently locate auditable source/provenance for the strongest leads, not to import an opaque engine binary.

## Objective

Resolve whether the candidate providers can supply the complete source and dependency evidence needed for a secure, maintainable production engine. If evidence cannot be obtained from public pinned source, record NO-GO and prepare a first-party/licensed-provider architecture decision rather than simulating success.

## Leads from E32

1. **WaxMoon/MultiApp + opensdk**: MultiApp commit `3de55af64ec925de390cbf30bb82f279a51945e0`; opensdk commit `eaab1645e57c0be6d3c1b0f5bf586984f4de141f`. Public `HackApi.installApkFiles` describes a directory containing split APKs, but delegates into `moon.jar`. Root license AGPL-3.0; README says repository is no longer maintained since 2023.
2. **ServenScorpion/VirtualApp**: commit `23aa5ec82a62c5128006e5c1eaa9ca7fe15b96d8`; source tree includes `app/sdk/lib-release.aar`, but source/license/provenance for that AAR is not established.
3. **BlackBoxing/BlackBox**: commit `a0a330732c51dd9476c618ede31ec7d4a168a5d5`; root Apache-2.0, but the existing source audit records base-centric split/resource gaps and security/native-provenance concerns.

## Steps

1. Search canonical public GitHub repositories and documented upstream releases for source releases, submodules, native source, build instructions and dependency manifests for the leads above. Pin all reviewed commits; do not trust a fork's default branch name or README alone.
2. Check whether `moon.jar` and `lib-release.aar` are generated from included source or downloaded as opaque artifacts. Inspect the complete repository tree and build tasks; record exact missing sources/dependencies. Do not download/run/load those binaries into the product.
3. For WaxMoon, record AGPL-3.0 implications for the intended distribution model. Do not assume AGPL is compatible or incompatible without legal review; treat this as an explicit approval gate. Do not copy the SDK until obligations and source availability are clear.
4. For every newly discovered candidate, inspect root and bundled licenses separately, recent source changes vs README-only changes, source completeness, install/cluster/resource/component/data paths, native artifacts and sensitive network/log/crash/spoof behavior.
5. If a complete source release is found, produce a provenance manifest with exact SHA-256 for source archives and dependency binaries, origin URL/commit, license and build recipe; only then draft a sandboxed E34 proof plan.
6. If no candidate provides complete source, license and native provenance, stop engine-import work and report the required maintainer/source package or the decision to build a first-party provider. Do not create a fake backend or set runtime readiness flags to pass tests.

## Acceptance criteria

- Every candidate is marked PASS/FAIL/UNKNOWN for source completeness, legal eligibility, native provenance, split cluster support, component lifecycle, account isolation and update/readback.
- No third-party binary is executed or imported during this phase.
- No unverified provider is wired into `RuntimeBindings`.
- `importsSplitApks=false` and all production acceptance flags remain unchanged.
- Status and product progress documents continue to distinguish technical milestones from the 28.00% weighted / ~30% product estimate.

## Deliverables

- `docs/phases/E33_SOURCE_ACQUISITION_RESULTS.md` with exact source pins, provenance evidence, unresolved dependencies, and a go/no-go decision.
- If the result is NO-GO, `docs/phases/E34_FIRST_PARTY_ENGINE_ARCHITECTURE_PLAN.md` should define the minimum provider contracts and phased design without claiming that the engine is implemented.

## Not claimed

E33 does not deliver a production engine, launch third-party applications, prove real guest lifecycle, prove account isolation or increase product readiness by itself.
