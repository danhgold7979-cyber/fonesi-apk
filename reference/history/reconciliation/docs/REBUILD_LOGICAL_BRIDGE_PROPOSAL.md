# B2 proposal: Java17 logical contract boundary (not yet authorized implementation)

Prerequisite evidence: B1b published `509d71ba89fee571f734455b3980b5c8f38c0022`, tree `91a68b840543eeadf8bceb149004eb291c7134e6`, exact-head run **41119246112 SUCCESS**: 11,471 desired identity checks,208 original kernel checks,42 immutable v1 oracle checks/99 observations; installed-JDK17 presentation and Android assemble/lint/packaged gates pass. This is host/compile evidence, not device or provider execution.

## Smallest next independent deliverable

A separate dependency-free Java17 logical module in a distinct package/source set, initially **not imported by Android**. Port only B1b identity text, provider/evidence/request assessment and immutable operation identity/state/readback transitions. Do not port filesystem journal, dispatcher, auth adapter, storage migration, or lease namespace implementation in this slice. Preserve current Java21 host implementation and both historical/current provenance manifests; label this honestly as a reviewed compatibility port, not entirely new product functionality.

Proposed source surface uses ordinary final value classes/enums, explicit constructors/accessors/equality/hash and defensive collection copies. Avoid records/Java21 bytecode/new collection factories/streams; target Java17 language output while auditing library calls against minSdk26 independently. This choice reduces language/backport uncertainty; it is not a claim that records are intrinsically unsupported on Android.

## Contract and test matrix before implementation

- Exact B1b scalar/Cc/fixed-whitespace rules for every provider ID/revision/environment entry point,256 UTF-16/768 UTF-8 bounds, exact NFC/NFD/case preservation. Historical malformed surrogate/NUL/edge-whitespace acceptance stays rejected. Never copy unsafe v1 observations into desired positives.
- Equality/hash consistency and round-trip field access; defensive copies; null element rejection; no collection mutation leaks. Record-style hash parity where externally observable must be explicitly tested, not assumed from `Objects.hash`.
- Provider/revision/environment/cluster/scope binding and capability dependency/hostile-guest denial cases; unknown evidence cannot grant capabilities. Preserve denial reason and exception-class contracts or flag changes for review.
- State/revision matrix, stale consent/CAS rejection, cancellation, dispatched recovery→REVIEW_REQUIRED, exact affected-peer readback identity/revision/cluster checks. Constructing VERIFIED is not persistence permission or proof.
- Plain installed-JDK17 `--release17 -Xlint:all -Werror` suite plus differential Java21 host oracle checks using deterministic cross-process fixtures (no loading Java21 classes into JDK17). Freeze reviewed valid/negative contract vectors; compare in CI, never regenerate automatically. Independent review must audit fixture completeness and distinguish desired behavior from accidental legacy behavior.
- Keep existing valid Unicode/v2 codec/high-bit UUID/scope-hash tests running against current host unchanged. No codec/hash production port in B2; exact framed byte/hash parity is a later separately planned logical serialization slice, not implicitly completed here.
- Source API inventory plus explicit forbidden-modern-helper tripwires; Java17 compilation is not minSdk26 runtime validation. Android compile-only integration can be proposed separately after the pure module passes; no change to current app dependencies/source sets in this slice.

## Ownership and durability boundaries (not solved by B2)

Values are data, not authority. Provider objects do not prove provider identity; evidence is not attestation verification. UI selection/controller tokens must never become operation authorization. There is no provider operation, journal path or mutation callback in the proposed module.

Before future adapter integration: trusted manager credential-protected/no-backup root ownership, hostile same-UID guest exclusion, authenticated consent, complete peer enumeration, durable DISPATCHED-before-mutation, file/SQLite CAS/locking/fsync/unknown-commit semantics and independent private readback each require separate evidence. Invalid historical records retain bytes and require quarantine; no remap/deletion/replay. Android/OEM runtime behavior cannot be inferred from Linux tests.

## Completion / stop conditions

Stop B2 on unexplained equality/classification/exception drift, unsafe oracle acceptance, or a dependency on provider/storage/device choice. Complete only with precise source/API audit, deterministic parity/negative suite, independent review and exact-head hosted JDK17 CI. No device/emulator/SDK licenses/install, new dependencies, permissions, provider enablement, old-source deletion or claim of engine completion. Root must approve this proposal before implementation.

## Root approval recorded before B2 code — 2026-10-30 20:03 UTC

Root approved the bounded dependency-free Java17 logical module in separate package/source set, not imported by Android. Preserve exact B1b identity, capability/state, exception and defensive-copy semantics and all host sources/manifests/history. Use deterministic reviewed/frozen cross-process oracle vectors; CI must compare, not regenerate. API26 library audit is distinct from compilation. Filesystem journal/dispatcher/auth/provider/codec ports are excluded. Unexplained parity drift blocks completion; root publishes after independent review.

Implementation note: enum identity-based hash values are not deterministic across JVM processes. Test value hash algorithms/equals consistency within each runtime, and freeze only deterministic fields/classifications and deterministic string/UUID-only hashes across runtimes. Do not treat enum numeric hash differences as contract drift or fake deterministic identity.
