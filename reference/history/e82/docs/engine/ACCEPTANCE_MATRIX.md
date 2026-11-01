# Fonesi Engine Acceptance Matrix — E35 draft

Status date: 2026-10-29. This matrix separates implementation evidence from acceptance. E21–E29 are synthetic/test infrastructure milestones and do not satisfy production guest-engine rows.

| ID | Requirement | Current status | Evidence currently available | Required proof to pass |
| --- | --- | --- | --- | --- |
| P-01 | Single APK package/version/signer preflight | PARTIAL PASS | `SpaceCoreBackend.checkedInput` and `ApkSetAuditor` check one APK's metadata/hash. | Owned fixtures for corrupted APK, signer/version mismatch, race after audit and private readback. |
| P-02 | Complete base/split cluster import | FAIL | `SpaceCoreBackend.checkedInput` requires exactly one path; `RuntimeBindings.supportsSplits=false`; pinned SDK audit found no complete public cluster import/readback contract. | Real provider install of base+feature+config splits; verify package/version/signer/dependencies/resources/native libs after restart. |
| P-03 | Immutable private cluster store | PARTIAL | Source snapshots and E27 task-scoped recovery exist; E27 verifies cleanup/review semantics. | Full cluster publication, per-member hash readback, atomic visibility, generation pinning and GC reference safety. |
| P-04 | Real guest Activity lifecycle | FAIL | E24/E29 host Activity/View lifecycle proof; receipt says `real_guest_activity_registered=false`. | Owned guest Activity launched by provider; framework lifecycle events, intent resolution and process-death recovery. |
| P-05 | Guest Service/Receiver/Provider lifecycle | UNKNOWN/FAIL acceptance | No accepted proof in current technical milestones. | Separate owned fixtures for each component type, permissions/export enforcement and background/process restart. |
| P-06 | Guest PackageManager/framework identity | UNKNOWN | E25 private manifest catalog is preflight only; no full guest PackageManager/Binder mediation. | Verify guest PackageInfo/ApplicationInfo/component resolution without consulting installed original as evidence. |
| P-07 | Resource/configuration/native library loading | PARTIAL synthetic only | E21–E24 signed synthetic DEX/resources/View proofs. | Real owned fixture with split resources, configuration changes, classloader, ABI libraries and 4 KB/16 KB ELF audit. |
| P-08 | Per-clone account/session isolation | FAIL acceptance | Product score records 0% accepted; no adversarial third-party clone tests. | Cross-clone file/db/preferences/URI/notification/process tests at the provider's actual isolation boundary. |
| P-09 | Update scope and readback | PARTIAL | Durable update queue, recovery, identity checks and WorkManager scheduling tests pass. | Provider-defined shared/per-clone scope; real update, restart, data preservation and actual guest version readback. |
| P-10 | Crash/process-death transaction safety | PARTIAL | E26/E27 journal recovery tests cover interrupted states, idempotence, lock contention and unsafe snapshot cleanup. | Provider integration fault injection at each mutation boundary; no false success or lost last-known-good generation. |
| P-11 | Background scheduling policy | PARTIAL PASS | E28 unique startup/periodic scheduling CI passes. | Physical-device/OEM tests and proof that provider/guest mutations obey foreground/unlocked policy. |
| P-12 | ABI, target SDK and 16 KB page sizes | FAIL for broad claim | Candidate runtime has page-size guard; pinned native set lacks a verified 16 KB rebuild. | Reproducible native builds and ELF LOAD alignment for each shipped ABI, target SDK 36 and supported device matrix. |
| P-13 | Source/license/dependency provenance | FAIL for alternative providers | E30–E33 found opaque JAR/AARs, stale sources or unresolved native/license provenance. | Exact source pins, full license/SBOM, binary provenance and reproducible builds. |
| P-14 | User-facing truthful capability state | PARTIAL | Candidate UI reports engine initialization separately; several real capabilities remain disabled. | UI reflects provider capability snapshot and never claims a clone is ready without verified postconditions. |
| P-15 | Release APK/signing and supported devices | FAIL acceptance | CI builds and audits debug/test artifacts only. | Release key management, signed artifact provenance, supported-device matrix, rollback and recovery acceptance. |

## Acceptance rules

- PASS requires reproducible evidence on the exact source SHA and the stated provider version.
- Synthetic fixtures can pass only the synthetic test row; they cannot pass a production guest lifecycle or account-isolation row.
- UNKNOWN is not PASS. A capability that is UNKNOWN must remain disabled.
- A source cluster is not an installed guest; an installed guest is not a running component; a running component is not proof of account isolation.
- Product progress is updated only when a weighted product acceptance dimension improves with actual evidence. Current weighted estimate remains 28.00% / ~30% rounded.
