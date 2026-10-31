# Fonesi APK — master rebuild plan

Date: 2026-10-30. Rebuild authorized; reviewed checkpoints published on `rebuild` only, no release or main merge. This ordered plan supersedes execution ordering, not the original product requirements or historical evidence. Red is a conversation marker only.

## Frozen product scope

No-root Android manager for independent clean instances of Facebook, Messenger, Zalo, TikTok, Threads, X, Shopee and YouTube. Preserve naming/group/style, direct instance shortcuts, original-app-sourced signed base/split updates, batch/shared-scope consent, pause/recovery, access lock, configuration-only backup and diagnostics. No original session copying, no identity spoofing, no web/original-app substitutes, no hidden requirement removal. User credentials never enter chat/logs.

The latest historical provider/specification reference is E82 branch `2fac016e3b28e4d9f21390c221cad1df7a382c86`, not main. E83 contract technical evidence is bound to `58d52f4aa05c8c8d5256e7125b522815418b1e02`. E84 selected no eligible production provider. E35 `docs/engine/THREAT_MODEL.md` at that ref assumes hostile guest code; preserve adversarial isolation requirements, do not downgrade to cooperative namespaces.

## Ordered gates

| Gate | Deliverable | Required evidence / stop boundary |
|---|---|---|
| R0 Recovery — ACCEPTED (narrow gate, 2026-10-30) | All refs + hashes + independent restore + divergent-head oracle | Fresh empty-repository restore reverified64 refs/hash/fsck;14-head semantic ledger retained. See REBUILD_LEGACY_CLEANUP_PROPOSAL.md. This does not accept source replacement or any other gate. |
| R1 Specification — ACCEPTED (documentary gate, 2026-10-31) | Requirements, threat boundaries, acceptance ledger and detailed steps | Root accepted reviewed candidate `13ac0bdcf45af2f0a4769991873e9bf7f8977f08` and independent re-review: atomic map plus three dispositions in REBUILD_R1_DECISIONS.md. HOST/COMPILE/ANDROID/PROVIDER/PHONE remain separate; missing implementation remains explicit. This accepts no R3–R7, product/runtime, source replacement, release or environment action. See REBUILD_R1_CLOSURE_REVIEW.md. |
| R2 Independent kernel — ACCEPTED (bounded Linux/JVM gate, 2026-10-30) | New capability decisions, durable operation journal and trusted shared-update dispatcher | Detailed R2.1–R2.3 audit plus G1/G2 closure independently reviewed; exact-head run41133142565 PASS. No Android/provider/whole-app concurrency or clone acceptance. |
| R3 Persistence/integration | Fresh storage adapter and migrations 1–6 compatibility, immutable source ownership | SQLite transaction/crash tests, namespace-safe recovery; old guest data never silently reset. |
| R4 Engine feasibility | Reviewed provider and exact signed cluster lifecycle | Source/license/native provenance; hostile cross-instance tests; synthetic Android execution then third-party proof. Provider unresolved blocks acceptance, not R2/R3/R5 independent work. |
| R5 Manager | Complete native UI/shortcut/config/access flows | Disabled unsupported actions, accessible UI, auth/foreground race tests, launcher/SAF device checks later. No requested red redesign. |
| R6 Updates | Frozen signed snapshots, atomic provider operation/readback, affected-peer consent | All boundary crash/failure tests; actual guest version/data/session readback. No downgrade-as-rollback promise. |
| R7 Acceptance/release | Required-app × Android/ABI/page-size × device matrix; signed upgrade | Synthetic before accounts; Samsung and other OEM; stability/resource measurements; release key under owner control. No publish/store/vendor contact without scope authority. |

## Isolation and provider decision

A host UID and two folders do not meet hostile-guest isolation by themselves. Neither a contract object nor its evidence fields are an authenticity verifier. Evidence must be independently inspected and bound to provider revision, artifact, environment, operation and claim scope. Synthetic host Activity lifecycle cannot enable third-party execution or account isolation. A new VM/container or paid provider is a material decision requiring clear scope/license/cost handling; do not silently substitute it.

## Migration / source replacement

Old source recovery is verified; later-branch tests/protocols remain regression oracles. New implementation starts in an independent module to avoid mixing main-era semantics into fresh code. Retain legal notices, trusted wrapper/build infrastructure, signed-fixture specifications and all historical refs. Remove superseded active implementation only after its replacement has equivalent or stronger guards and recovery is verified. Rebuilding repository code never authorizes deleting device data, reusing guest IDs or replaying another provider's journal.

## Reporting

Separate historical CI, fresh local tests, Android synthetic receipts, real-provider execution and physical-device acceptance. Report counts per suite/configuration without double-counting. No claim of full completion, no fabricated provider, no inflated percentage. Record each plan before code and fix against evidence, not by weakening tests.

## Current runtime evidence boundary

Exact rebuild run41148595270/image20261018.96.1 observed no installed emulator or system images; KVM exists without current-account read/write access. See REBUILD_RUNTIME_GATE_HANDOFF.md for retained source/job-bound evidence and prerequisites, not provisioning permission. Compile PASS is not Android runtime acceptance; R4 provider remains independently unresolved.
