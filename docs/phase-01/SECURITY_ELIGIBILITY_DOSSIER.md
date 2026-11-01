# T05/T06 eligibility dossier — read-only, 2026-11-01

## Outcome and limits

Six pinned candidates are recorded in [candidate-eligibility.json](candidate-eligibility.json). **Zero eligible for integration.** T05 is a reviewable research inventory, not yet accepted. T06 is rejection triage, not a full positive security audit. T07/T08 are blocked by missing eligible source/provider and runtime evidence. The accepted P0 source remains unchanged. No package, SDK, dependency, permission, account, profile or provider was activated.

Required selection is conjunctive: requirements fit + verified adoption/quality + maintenance/security + complete licensing/provenance + appropriate tests. Stars/forks are excluded as acceptance evidence. Vendor customer counts and advertised Android/app compatibility remain claims, not independently verified deployments or positive user-quality reviews. No candidate currently has sufficient independent adoption evidence **and** the mandatory security/legal fit; popularity cannot waive a failed boundary. Current default-branch commit date and repository pushed_at are separate metadata: tag/branch pushes and README edits do not establish implementation maintenance.

## Fresh causal security evidence: NewBlackbox

Pinned revision `89b59836c66f173756a4ae258cf379a957649820`; read-only inspection, not execution. Findings below justify exclusion pending full remediation/review; they do not allege observed data theft or successful privilege acquisition.

| Source and immutable blob | Observation | Required control / present decision |
|---|---|---|
| [Bcore manifest](https://github.com/ALEX5402/NewBlackbox/blob/89b59836c66f173756a4ae258cf379a957649820/Bcore/src/main/AndroidManifest.xml), `c8b9cecd8e9c8778a9a9dc33e94217f17995cb0e` | 323 uses-permission declarations, including network, overlay, location, microphone/camera and account-related names; no isolatedProcess=true in this manifest. Declarations are NOT granted authority. | Complete merged-permission/component review absent; same-UID/process names cannot prove hostile-guest containment. No permissions imported. |
| [Bcore build](https://github.com/ALEX5402/NewBlackbox/blob/89b59836c66f173756a4ae258cf379a957649820/Bcore/build.gradle), `857d57ef526e8a908967e2c23d9d643d2367978b` | ARM64/ARMv7 filters; NDK29.0.13846066; local jar/aar inclusion and reflection dependency. | Full component/source/license/SBOM and reproducible native build unknown. Nothing downloaded/built; current APK pins unchanged. |
| [Core initialization](https://github.com/ALEX5402/NewBlackbox/blob/89b59836c66f173756a4ae258cf379a957649820/Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java#L858-L1015), `766bfaeaa3a98a580bbc56caaedc105f986f4219` | Conditional log collection, VPN initialization and fallback/continue paths are present. Lines2067–2103 expose a log/device-info collection and LogSender pathway. Call-site consent/export destination/redaction completeness not established. | Initialization must fail closed; no uncontrolled diagnostics/export. This is a sensitive pathway requiring complete call-graph audit, not evidence of automatic export. No tokens/destinations copied into dossier. |
| [UID helper](https://github.com/ALEX5402/NewBlackbox/blob/89b59836c66f173756a4ae258cf379a957649820/Bcore/src/main/java/top/niunaijun/blackbox/utils/UIDSpoofingHelper.java), `1d694b5890eeab03c840eff8daea644c824c2250` | Explicit identity-substitution helper and fallback decision exist. | Conflicts with no identity-spoofing boundary; do not import or execute. Merely disabling a UI toggle would not prove exclusion from all paths. |

The recursive Git tree also contains opaque binary artifacts (for example app/libs AARs and Bcore asset JARs). Their presence alone is not malware evidence. Complete origins, licenses, source-to-binary correspondence and transitive scope remain unknown. Root Apache licensing does not settle those gaps. We stop positive eligibility here rather than run unsafe code to see whether it works.

## Other candidate decisions and source freshness

- **SpaceCore:** current source revision remains `3826a2f`. Pinned README describes a proprietary engine and spoof-related controls. Free-use wording is not complete engine/native provenance. No SDK binary retrieved; no positive license or security clearance.
- **Blacks-BlackBox:** current revision remains `40282a7`. Root Apache license previously checked; historical split/provenance concerns remain explicitly historical. Full current dependency/boundary audit missing, not silently inferred from another fork.
- **VirtualApp:** refreshed default branch `ba5abbc2774e9a039764aac07eae18a7fbadcdbc`. [Current README](https://github.com/asLody/VirtualApp/blob/ba5abbc2774e9a039764aac07eae18a7fbadcdbc/README.md) still distinguishes frozen2017 public implementation from current commercially authorized code. Recent changelog and customer claims do not supply inspectable licensed source or independently proven adoption. No purchase/contact/legal acceptance.
- **twoyi:** [pinned README](https://github.com/twoyi/twoyi/blob/dd052a07a812e742ec7583da393466ccc066a8b1/README.md) states discontinuation and unrebuildable ROM from published app source; API metadata confirms archived. App-only MPL metadata cannot clear a missing ROM/native supply chain. Not an eligible maintained replacement.
- **Island:** [pinned README](https://github.com/oasisfeng/island/blob/d63538212a9f417c180bdb9258c0f5461de53c27/README.md) describes DPC/profile-owner authority and sibling deagle dependency. This is a different architecture, not a drop-in arbitrary multi-instance runtime. No provisioning or device-policy permission authorized.

## Contract coverage and next decision

T05's license/source/API/ABI/SDK/NDK/base-split/GMS/shared-version fields all have either scoped evidence or explicit UNKNOWN in the matrix. T06 tracks initialization, permissions, dependencies/native origins, network/log exports, device identity and legal obligations. Each unresolved field blocks positive selection; a completed negative inventory does not complete T06's requirement for a safe candidate to build.

The retained109 security/reconciliation/bridge rows preserve exact source assertions and11 owned-fixture families. Mandatory signed-source/split/namespace/lifecycle/auth/journal/readback gates are not weakened. HOST and COMPILE cannot establish OS containment or eight-app behavior. All eight real apps remain UNKNOWN. Do not replace a missing provider with original-app/web launch, new package signatures, copied sessions, root, managed profiles or unsupported full compatibility claims.

Next useful result is independent dossier review and bounded T05 acceptance if criteria are satisfied. T06–T08 stay open/blocked absent new eligible evidence. Further repeated searches of unchanged excluded candidates add no progress; require materially new complete source/license/boundary/adoption evidence before reopening integration.
