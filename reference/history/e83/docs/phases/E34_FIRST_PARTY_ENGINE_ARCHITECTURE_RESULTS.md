# E34 — first-party engine architecture decision results

Date: 2026-10-29. Based on E30–E33 provider feasibility and provenance reviews, the production engine remains blocked. This decision records the architecture direction and the conditions required before implementation.

## Decision

**No production provider switch and no broad first-party engine rewrite is approved by evidence at this point.** The next safe step is E35: define the provider contract, threat model and acceptance decision record. In parallel, keep seeking a properly licensed provider with complete source/build provenance. If none is available, use E35 to scope a narrow first-party feasibility prototype before committing to a full runtime rewrite.

This avoids two incorrect shortcuts: importing an opaque AAR/JAR because its API looks promising, or assuming that a synthetic Activity/View proof is close to a complete Android guest runtime.

## Path comparison

| Path | Current evidence | Benefits | Hard blockers / risks | Decision |
| --- | --- | --- | --- | --- |
| Licensed provider with complete source | No eligible provider identified in E30–E33. WaxMoon's split-directory API delegates to opaque `moon.jar`; ServenScorpion ships opaque `lib-release.aar`; audited BlackBox forks have cluster/security/native-provenance gaps. | Could reuse a mature component/runtime architecture if source, license and current platform support are proven. | Source and native provenance, legal obligations, split semantics, guest lifecycle and account isolation are unresolved. | Keep searching; do not integrate until all gates pass. |
| First-party in-process virtualization engine | Fonesi already has app UI, durable registry/update orchestration, synthetic DEX/View tests and package metadata models. | Full control over threat model, supported feature set and supply chain. | Large scope: Android component dispatch, hidden/framework API compatibility, resources, Binder/services, native libraries, process lifecycle and same-UID data isolation are systems-level work. Same-UID virtualization may not satisfy a strong account-isolation requirement. | Do not commit to a full rewrite before E35 contract and E38/E40 feasibility gates. |
| Full guest OS/container/VM architecture | Vectras-VM is an example of a different QEMU-based model, not a drop-in provider. | Potentially clearer OS-level guest boundary. | Different product architecture, image sourcing, performance, integration, update model and device compatibility; major scope change. | Only consider if the product requirements permit a guest-OS model. |

## Mandatory engineering rules

- Keep `RuntimeBindings.supportsSplits=false` and the production adapter's single-APK guard.
- Keep `real_guest_activity_registered=false`, `kernel_uid_isolation=false`, and `production_clone_accepted=false` until independently verified evidence changes those values.
- Never equate a clone registry entry, a second host Activity, a private DEX-loaded View or an engine user ID with a functioning, isolated third-party guest.
- Any future engine must declare exact supported Android versions, ABIs, page sizes and component types. Unsupported cases must fail closed.
- Do not migrate or reinterpret current SpaceCore registry/journal data as if it belonged to a replacement provider.

## Next phase

**E35 — provider contract, threat model and acceptance ADR.** Define the immutable package-cluster schema, provider capability model, lifecycle contract, isolation threat model, update transaction, legal/supply-chain requirements and test gates. Only after E35 review should an owned synthetic package-store prototype or a licensed-provider proof be selected.

## Product completion

The score remains **28.00% weighted / ~30% rounded estimate**. E34 changes architecture decisions, not shipped functionality. Real third-party clone applications accepted: **0**. Full 8 product phases / 32 steps remain incomplete.
