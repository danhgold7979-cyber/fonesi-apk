# E84 — alternative split-provider feasibility and source/license audit plan

Date: 2026-10-30. Repository: danhgold7979-cyber/fonesi-apk.

Product mapping: Phase 1 steps 5–8. E84 is a technical source-feasibility milestone, not a new Product Phase and not product runtime acceptance.

## Baseline and reason

E83 established a typed, fail-closed split-provider contract, but no production provider is integrated. The pinned SpaceCore API exposes only a single-APK install path and the candidate explicitly rejects multi-member sources. Before attempting a provider fork, evaluate additional public and commercial candidates using exact source revisions and license/provenance evidence.

## Candidate set

1. asLody/VirtualApp public repository, exact revision ba5abbc2774e9a039764aac07eae18a7fbadcdbc.
2. ISEKHON/VirtualApp fork, exact revision b3c634ad7941765df3da84a207aca94b7861afae.
3. TeguFy/blackbox-android, exact revision bf1623b7d45157bbc87be46a29352dd6bbbde9fa.
4. Existing E1–E3 audit evidence for NewBlackbox, Blacks-BlackBox, renjana, ServenScorpion/VirtualApp and the pinned SpaceCore SDK remains part of the candidate comparison. Do not repeat a prior candidate under a new name or treat a fork's README as proof.

## Required source audit

For every candidate, record exact commit, license, dependency/native provenance, build toolchain and API/ABI claims. Inspect:

- Base/split parsing and persistence of the complete member list, split names, splitCodePaths, dependencies, required/provided split types and isolated-split flags.
- Cryptographic signature collection for every member. Any fake-signature, signature-spoof, fallback-to-original-app or re-signing path is a hard rejection.
- Private immutable code storage, byte-for-byte readback, package/version/signer continuity and complete-member verification.
- Update transaction semantics: stage the entire new cluster, preserve the previous version until verification, atomically switch or retain an explicit unknown/reconciliation state. Deleting the live version before a successful replacement is a hard rejection.
- Guest PackageManager/ApplicationInfo/Resources/ClassLoader handling and virtual-user data namespace. Distinguish virtual UID namespace from a real kernel UID.
- Native ABI selection and library copy integrity, 16 KB page-size readiness, Android API 26–36 compatibility, and physical-device acceptance gaps.
- Logging, network, crash handlers, device spoofing, hidden-API bypass, Xposed/SandHook and permissions. No unrequested telemetry, signature spoofing, crash suppression or account collection may be imported.
- Automated test coverage and whether build scripts can be safely executed after static audit.

## Safety and decision rules

1. Source review is read-only. Do not run an untrusted candidate's build scripts until its license, dependencies, native code and build behavior pass a first static review.
2. Do not purchase a commercial SDK, contact a vendor or accept a license on the user's behalf.
3. Do not vendor source or binaries until the license and every relevant third-party dependency/provenance are verified.
4. Do not weaken Fonesi's APK signature, cluster identity, atomic update, private storage or device acceptance requirements to make a candidate appear viable.
5. If no candidate passes, record the exact blockers and retain supportsSplits=false. A negative feasibility decision is a valid E84 result.

## Acceptance

- Exact revisions and license/provenance evidence recorded for each candidate.
- Split import, complete readback, signature preservation, atomic update, namespace isolation and Android/ABI support evaluated from source, not marketing claims.
- One candidate is selected only if all hard gates have evidence; otherwise explicitly record no acceptable provider.
- No production provider is integrated and no product score is increased by source inspection alone.
