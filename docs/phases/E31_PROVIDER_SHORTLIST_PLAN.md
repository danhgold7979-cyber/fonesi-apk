# E31 — provider replacement shortlist and threat-model decision (plan before code)

Date: 2026-10-29. Continue on `phase/e21-android-split-code-proof-20261028`; do not make another branch for this gate. E30 concluded that the pinned SpaceCore candidate is not accepted for production requirements. E31 evaluates alternatives, but must not import or execute a candidate engine until source, license, native provenance and security behavior have been reviewed.

## Goal

Produce a defensible short list (or no-go decision) for a production provider capable of real guest lifecycle, complete base/split handling, private code/resource/native storage, framework-service mediation and per-clone account isolation. Keep this separate from the synthetic E21–E29 proofs.

## Candidate set from existing source audit

1. **Pinned SpaceCore 3826a2fa...** — already integrated as opt-in candidate; known split-import/API and 16 KB native blockers. Baseline/control only, not assumed eligible.
2. **ALEX5402/NewBlackbox 89b59836...** — inspectable Java/native source, but existing audit records base-only code paths/resource loading, incomplete cluster metadata, fallback behaviors, crash/log/spoof paths and unresolved prebuilt provenance.
3. **Black00Z/Blacks-BlackBox 40282a7...** — existing audit records partial `.apks` handling but incomplete cluster/dependency/signer/readback semantics and inherited risky handlers/prebuilt dependencies.
4. **VirtualApp / twoyi / other existing candidates** — include only if exact pinned source and license/provenance can be inspected. Do not treat old READMEs, commercial SDK promises, or no-source checkouts as a candidate that can be integrated.

## Investigation steps

1. Re-read `docs/ENGINE_SPLIT_AUDIT.md`, `docs/EXECUTION_PLAN.md`, `docs/PLAN.md`, `third_party/spacecore/NOTICE.md`, and the E30 decision matrix.
2. For each candidate, record exact source revision, source availability, root license and native/dependency license/provenance separately, last maintainable build setup, Android/ABI/page-size evidence, and known security-sensitive code paths.
3. Map each candidate against the E30 contracts: split cluster identity, private code store, real component lifecycle, framework mediation, account/session isolation, update/readback, platform compatibility and supply chain.
4. Use `PASS`, `FAIL`, or `UNKNOWN` only with file/API evidence. Do not run untrusted Gradle/CMake scripts or load third-party binaries during source triage. No new dependencies or APKs in this phase.
5. Decide whether any candidate is eligible for a sandboxed, owned-fixture proof. If none meets minimum source/license/security gates, report NO-GO and specify the missing source/provenance required to revisit.
6. If one candidate is eligible, define a separate E32 proof plan: isolated namespace, test-only build flavor, owned synthetic payloads, immutable source pin, migration boundary, manifest/permission/ELF audits and negative security tests. Do not modify the production provider in E31.

## Minimum eligibility gate

- Inspectable source at a pinned commit and license for the full source tree.
- Provenance/licenses for every native prebuilt or a reproducible build from inspectable source.
- No unexplained data exfiltration/log sender, device-identity spoofing, anti-detection behavior, or crash handler that hides failures.
- Plausible design for base/split package cluster and guest component lifecycle; no reliance on merely copying split files into a base-only engine.
- Plausible account isolation boundary and testability on a supported Android version/ABI.

## Deliverables

- `docs/phases/E31_PROVIDER_SHORTLIST_RESULTS.md` with candidate matrix, file-level evidence, eligibility decision and remaining unknowns.
- Update `docs/STATUS.md` and `docs/PRODUCT_PROGRESS.md`; preserve 28.00% weighted / ~30% estimate unless an actual user-facing product acceptance dimension improves.
- If there is no eligible provider, explicitly keep production integration blocked rather than inventing an implementation plan that assumes unsupported APIs.

## Not claimed

E31 does not build an engine, execute third-party guest code, test social accounts, prove guest component registration, prove account isolation, or deliver a production APK. It is a source/license/security feasibility gate.
