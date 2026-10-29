# E29 — signed private component-to-host dispatch proof (plan before code)

Date: 2026-10-28. Continue on the cumulative branch `phase/e21-android-split-code-proof-20261028`; do not create another product/phase branch. E21–E25 prove signed synthetic split/code/resource loading, a host View lifecycle and private component-manifest preflight. E26/E27 prove journal recovery; E28 scheduling CI must be green before E29 implementation begins.

## Product-wide boundary

The current production engine is still not accepted. SpaceCore remains single-APK with `importsSplitApks=false`; a private manifest parsed by PackageManager does not register a guest component with Android. This phase must not claim guest Activity registration, arbitrary APK execution, PackageManager virtualization or account isolation.

## Purpose

Join the existing E25 private synthetic component catalog with the E24 signed private DEX/View lifecycle proof through an explicit, test-only dispatch ticket. This is an integration proof for project-owned synthetic payloads only. It must demonstrate that component selection comes from the verified private manifest/catalog and exact private payload identity, rather than querying or launching the installed original application.

## Pre-code inspection

1. Read E24/E25 runners, result schemas, source payload manifest, private APK store/publish/readback path, synthetic component catalog, host Activity manifest/process, and current E21–E28 CI contracts.
2. Identify the exact immutable fields already emitted by E25 (package, version, signer, component class, private payload SHA and proof UUID). Reuse existing fields where present; do not invent a new source of truth.
3. Confirm the host Activity receives an explicit, bounded proof mode and UUID; reject implicit intents, unknown proof modes, stale receipts, mismatched package/class, unexpected signer/hash and missing component preflight.
4. Keep all dispatch code under the opt-in test-only source/runtime path. The normal manager, cloud build and production clone adapter must not dispatch this payload or report a clone launch.

## Implementation sequence

1. Add a typed, immutable synthetic dispatch ticket that binds the E25 verified component record to the E24 private payload hash, test UUID and exact host-process target. The ticket must not accept caller-supplied file paths or installed package metadata.
2. Add a narrow dispatch validator that consumes only the verified catalog receipt and private-store readback result. It must fail closed on mismatched package/version/signer/component/hash/UUID, duplicate or unknown component names, stale receipt, and any request to use the installed original.
3. Route the validated ticket to the existing dedicated test host Activity through an explicit internal intent. The host revalidates the ticket immediately before private-code load, then runs the existing synthetic lifecycle proof and reports a separate `synthetic_component_dispatch_verified=true` flag.
4. Keep acceptance flags explicit: `real_guest_activity_registered=false`, `third_party_component_executed=false`, `kernel_uid_isolation=false`, and `production_clone_accepted=false`. Do not rename a host Activity lifecycle as a guest Activity lifecycle.
5. Add negative tests for stale UUID, wrong private payload hash, signer/package/class mismatch, absent preflight, and attempted installed-original lookup. Use only project-owned signed synthetic payloads.
6. Add a dedicated E29 workflow using the pinned SDK/JDK and owned AOSP34 emulator. Require the E24/E25 regressions, source/APK/manifest/permission/ELF audits, exact SHA-bound receipt and the explicit false acceptance flags. Never pass if the emulator cannot run; record BLOCKED separately from PASS.
7. After CI, write exact results with code SHA, workflow ID, artifact/hash and limitations. Do not increase product readiness for a synthetic dispatch bridge.

## Acceptance criteria

- Only a verified E25 catalog record and matching immutable private payload receipt can produce a dispatch ticket.
- Host dispatch is explicit, UUID-bound, package/class/hash-bound and rejects stale or altered metadata.
- The synthetic private code/View lifecycle runs only in the dedicated test host process.
- No call to installed-original launch/resolve APIs is used as proof; no guest component is registered with Android by this phase.
- The exact CI SHA has green E24/E25 regressions and a green E29 Android proof; all product-acceptance flags remain explicitly false.
- E26/E27 journal recovery and E28 scheduling checks remain green on the same cumulative branch.

## Explicitly not claimed

No arbitrary third-party APK launch, real guest Activity/Service/Receiver/Provider registration, ActivityThread hooks, guest PackageManager/Binder/permission mediation, Play Services, independent per-account OS UID sandbox, social login, split production import, Samsung ARM64 compatibility, session-preserving updates, release signing or full product completion.

## Next production decision after E29

This bridge does not close the main engine blocker. The next product-level step must either implement and independently verify the actual provider contracts (guest component lifecycle, package/resource/native loading, framework service mediation and account isolation) from an audited/licensed engine source, or document that the selected provider cannot meet the product requirements and continue source-engine evaluation. Do not promote the synthetic bridge into a production capability.
