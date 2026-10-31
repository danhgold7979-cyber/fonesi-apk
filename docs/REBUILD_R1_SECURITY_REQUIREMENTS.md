# R1 security and provider requirements — atomic review ledger

**Current authoritative status — R1 DOCUMENTARY gate ACCEPTED by root on 2026-10-31**, based on reviewed candidate `13ac0bdcf45af2f0a4769991873e9bf7f8977f08` and independent re-review. Acceptance covers the specification/evidence map and ADR-R1-01, STYLE-R1-01 and EXEC-ID-R1-01 only. It is not product architecture, runtime or feature acceptance. R3–R7, Android/provider/phone evidence, source replacement and release remain open; no code, dependency, workflow, environment, license, provider or device action is authorized. No APK delivery is needed for this documentation update. Earlier candidate/pending wording below is historical preparation, superseded only for R1 documentary status.

## Historical candidate preparation and review

2026-10-31. **Documentary proposal, not R1 acceptance or implementation authorization.** This ledger atomizes the specified security/provider sources and divergent-head protections. It does not replace the separate product/UI/automation/configuration requirements ledger. No provider is accepted; no new Android/provider/phone result is claimed. Historical implementation is an oracle, not fresh implementation acceptance.

## Pins, citations and evidence vocabulary

Every `T:L`, `A:L`, `C:L`, `S:L`, `K:L` or `D:L` citation below expands to the exact immutable SHA/path/line range in this table; line numbers are from `git show SHA:path | nl -ba` (not current working-file line numbers).

| Ref | Immutable source |
|---|---|
| T | `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/engine/THREAT_MODEL.md` |
| A | `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/engine/ACCEPTANCE_MATRIX.md` |
| C | `58d52f4aa05c8c8d5256e7125b522815418b1e02:docs/engine/PROVIDER_CONTRACT.md` |
| S | `58d52f4aa05c8c8d5256e7125b522815418b1e02:docs/phases/E83_PRODUCTION_SPLIT_PROVIDER_CONTRACT_PLAN.md` |
| K | `58d52f4aa05c8c8d5256e7125b522815418b1e02:core/src/main/kotlin/vn/fonesi/core/ManagedGuestEngine.kt` |
| D | `f76c2f9388a5cc625c0252d9f0623340aa56debd:docs/REBUILD_RECONCILIATION.md` — full 14-head semantic reconciliation, not just main |

Fresh evidence mapping below is pinned to **`c0740ef2602e63d2b35cd441c7526e843906daf9`**. Evidence codes identify concrete source/tests, not acceptance percentages:

- **H-policy**: `rebuild-kernel/src/main/java/vn/fonesi/rebuild/CapabilityPolicy.java:10–72`; `KernelTest.java`, `PolicyModeTest.java` in the matching test package. Exact provider/revision/environment/cluster/scope and claim dependencies; strong-mode allowlist is a necessary condition, **not attestation**.
- **H-protocol**: same main package `OperationProtocol.java:11–65`; `KernelTest.java`, `JournalTest.java`. Exact operation/peer binding and reachable state/revision transitions.
- **H-journal**: same package `OperationJournal.java`; `JournalTest.java`, `CanonicalJournalTest.java`, `PackageBoundsTest.java`. Linux/JVM persistence/CAS/corruption checks, not an Android adapter or hostile-writer authentication.
- **H-dispatch**: same package `SharedUpdateDispatcher.java`; `DispatcherTest.java`. Trusted injected probe/mutation/readback callbacks, durable dispatch and cooperating-process scope lease; callbacks are not a production provider.
- **H-identity**: same package `IdentityText.java:3–29`, `IdentityTextTest.java`; portable equivalents under `rebuild-logic/src/main/java/vn/fonesi/rebuild/logical/`. Provider/revision/environment scalar validation is **not** the grammar for opaque catalog IDs.
- **H-codec**: `rebuild-logic/src/main/java/vn/fonesi/rebuild/logical/OperationCodec.java:23–74`, `ScopeKey.java`; `rebuild-logic/codec-oracle/shared/CodecContractTest.java`, logical contract tests and frozen oracle manifests. Owned bounded canonical bytes and scope-key parity, in-memory only; not the E83 APK-cluster digest algorithm.
- **H-storage**: `rebuild-storage/storage_contract.py`, `test_storage_contract.py`, `test_catalog_identity_projection.py`, pinned `spec/legacy-room/1.json` through `6.json`. Synthetic SQLite retention/quarantine transactions, **not** actual user migration.
- **H-manager/C-manager**: `rebuild-android/app/src/main/java/vn/fonesi/rebuild/model/ManagerModel.java` and sibling `ManagerController.java`, model/controller contract tests; `rebuild-android/app/src/main/java/vn/fonesi/rebuild/ManagerActivity.java`, `catalog/{CatalogReader,CatalogPage,CatalogLoader,CatalogRequestGate,CatalogOwnership}.java`; page/gate/ownership and actual-loader-host tests. Host stand-ins do not execute Android SQLite/Looper. Android compile/lint/packaged checks show disabled operation controls and no permissions, not runtime authentication/isolation.
- **C-package**: `rebuild-tools/verify_debug_apk.py`, `test_verify_debug_apk.py`, `test_android_scaffold.py`, `test_android_api_boundary.py`; fresh debug packaging only, no release receipt.
- **NONE**: no fresh implementation/evidence closing the requirement. Historical oracle remains retained.

`H` means HOST-only proof; `C` means COMPILE/static/packaged evidence. For **every row**, actual ANDROID, PROVIDER and PHONE acceptance remains unexecuted unless a later exact receipt is linked. The latest hosted inventory proves only that the designated SDK on that runner lacked emulator/system images; see `REBUILD_RUNTIME_GATE_HANDOFF.md`. It does not make independent engineering impossible.

### How each row is evaluated

Each row has one separately reviewable obligation. `P` is the positive proof; `N` is a negative proof; `R` is race/recovery proof. They are future acceptance criteria, **not claims these experiments were run**. Unless specified otherwise, a failed P, accepted N, stale/ambiguous R, missing exact source/provider receipt or unverified evidence layer means **STOP acceptance and keep dependent operation disabled**. If mutation may already have happened, retain bytes/ownership and enter REVIEW_REQUIRED; do not infer FAILED_NO_CHANGE, delete, repair, replay, or claim success. No denied historical malformed-envelope reproduction is requested or authorized by this ledger.

## 1. Threat boundaries, isolation and lifecycle

| ID / source | Atomic obligation | P / N / R acceptance criteria | Fresh evidence and remaining gate |
|---|---|---|---|
| SEC-001 T:16; C:9,71 | Authorize every guest mutation only while foreground access is unlocked. | P authorized foreground mutation; N locked/background request has zero provider calls; R revoke between each preflight and dispatch, then restart without replay. | H-dispatch checks injected authorization; actual authentication/Android/provider gate OPEN. |
| SEC-002 T:17; C:7 | Treat provider-returned state as untrusted until provenance and runtime acceptance. | P independently corroborated state; N stale/incomplete response denied; R provider revision changes invalidate evidence before use. | H-policy identity checks only; actual provenance/attestation NONE. |
| SEC-003 T:18,37–42 | Do not treat host-UID directories as hostile-guest isolation. | P verified stronger boundary plus adversarial evidence; N same UID/UNKNOWN with claimed isolation denied; R boundary changes revoke acceptance. | H-policy D3 truth table; provider/platform adversarial gate OPEN. Directory0771 exception is not isolation. |
| SEC-004 T:8,26,59; A:14 | Deny clone A access to clone B private files. | P each clone accesses own files; N A reads/writes B denied; R after rename/delete/restart ownership remains separate. | NONE at provider boundary. |
| SEC-005 T:8,26,59 | Deny cross-clone database access. | P own database persists; N cross-clone query/write denied; R concurrent update/crash preserves separation. | H-storage only metadata retention; guest DB gate NONE. |
| SEC-006 T:8,26,59 | Deny cross-clone preference/cache access. | P own settings/cache work; N other clone access denied; R cleanup/restart cannot alias namespaces. | NONE at provider boundary. |
| SEC-007 T:8,26,59 | Prevent cross-clone URI grant acquisition/use. | P intended grant works; N foreign grant denied; R revoke/restart does not restore foreign access. | NONE. |
| SEC-008 T:8,60; A:14 | Keep guest notification identity and routing separate. | P notification opens exact clone; N foreign clone cancellation/action rejected; R process recreation preserves target identity. | NONE. |
| SEC-009 T:60; A:11 | Keep guest Service identity/lifecycle separate and enforce permissions/export rules. | P owned service lifecycle receipt; N foreign/unauthorized call denied; R restart/background handling retains owner. | NONE. |
| SEC-010 A:11; C:20,76 | Enforce guest Receiver lifecycle and permission/export boundaries. | P intended owned receiver delivery; N unauthorized delivery denied; R process restart has no duplicate/misrouted delivery. | NONE. |
| SEC-011 T:60; A:11 | Keep guest ContentProvider identity/lifecycle and permissions separate. | P own provider data; N wrong clone/export violation denied; R restart preserves authority binding. | NONE. |
| SEC-012 T:19,31,64; A:10; C:10,40 | A guest Activity claim requires actual provider/framework guest lifecycle, not host View/Activity events. | P owned guest component receipt; N host proxy alone never counts; R launch/process-death/recreation binds same instance. | C-manager is host UI only; no guest acceptance. |
| SEC-013 A:12; C:32,40 | Resolve PackageInfo/ApplicationInfo/components from private guest identity, never original app as fallback evidence. | P actual private registry/framework readback; N original installed app alone denied; R original uninstall/change cannot substitute identity. | NONE. |
| SEC-014 T:9; C:48 | Preserve original installed app data/identity during clone operations. | P independent original remains usable; N clone removal/update cannot touch original; R interrupted cleanup retains original. | NONE; device/provider gate OPEN. |
| SEC-015 T:51; C:70 | Do not automatically copy original credentials/session material. | P clean new account region; N source secrets excluded; R recovery/import cannot reintroduce session copying. | No provider/import implementation; absence of feature is not acceptance. |
| SEC-016 T:52 | Do not introduce hidden identity spoofing/anti-detection/crash suppression. | P reviewed behavior inventory; N prohibited SDK hooks rejected; R dependency/config upgrade triggers renewed audit. | NONE for provider; no provider selected. |
| SEC-017 T:53 | Keep logs free of credentials, package-private data and session material. | P allowlisted diagnostics; N sensitive fields absent; R crash/error paths stay filtered after restart. | C-manager generic errors limited; full diagnostic/provider audit OPEN. |
| SEC-018 T:30,53 | Do not perform unreviewed telemetry/log upload. | P declared reviewed dataflow; N unexpected endpoint/upload absent; R dependency/config changes cannot silently enable upload. | C-package no INTERNET permission, not native/provider telemetry proof. |
| SEC-019 T:54; C:69 | Background recovery may reconcile metadata but never initialize/launch/install/update/import guests. | P metadata-only recovery; N background provider callback count zero; R stale worker after lock/disable/restart stays inert. | H-protocol recovery + H-storage inert flags; Android worker/provider implementation OPEN. |
| SEC-020 T:65 | Disabled manager/provider cannot initialize guest runtime. | P enabled reviewed initialization; N disabled state zero initialization; R disable during scheduled/startup event prevents callback. | C-manager unsupported actions disabled; no actual provider init path to accept. |

## 2. Provider identity, capabilities, operation results and source safety

| ID / source | Atomic obligation | P / N / R acceptance criteria | Fresh evidence and remaining gate |
|---|---|---|---|
| SEC-021 C:11 | Namespace persisted clone records by provider; replacement cannot adopt foreign records without tested migration. | P exact provider owns record; N same clone ID in another provider remains foreign; R upgrade retains foreign bytes/no dispatch. | H-storage composite identity/quarantine; H-manager exact CatalogKey; actual provider migration OPEN. |
| SEC-022 C:17 | Bind provider snapshot to ID/version/source revision/dependency-manifest digest. | P all four verified bindings; N missing/mismatched binding denied; R upgrade invalidates cached snapshot. | H-policy ID/revision subset only; full snapshot NONE. |
| SEC-023 C:18 | Represent readiness, initialization state and failure code separately. | P explicit initialized result; N ready flag alone grants no capability; R failed/interrupted init remains nonready. | H-policy not a full init model; NONE. |
| SEC-024 C:19,24 | Expose verified API/ABI/page-size support and explicit unsupported restrictions. | P exact supported environment; N unknown/unsupported disabled; R environment change re-evaluates capability. | H-policy opaque environment match only; real support matrix OPEN. |
| SEC-025 C:20–26; A:27 | Keep capabilities explicit, individually verified and default unsupported/unknown. | P exact required claim; N absent/UNKNOWN denied; R capability revision change invalidates pending authorization. | H-policy + logical parity; no source of trusted provider evidence. |
| SEC-026 C:21 | Declare resource/config/native/intent/framework mediation levels separately. | P evidence per mediation feature; N successful parsing does not enable runtime feature; R configuration/process changes rerun proof. | NONE; synthetic historic proofs not carried as acceptance. |
| SEC-027 C:23,44 | Declare exact update scope, atomicity and readback guarantees. | P complete affected set consent; N unknown scope/running state defers; R peer set/state changes reject stale consent. | H-dispatch exact peers/stopped/complete enumeration; provider scope adapter OPEN. |
| SEC-028 C:72 | Capability changes require renewed acceptance and snapshot digest update. | P new revision has exact receipts; N old digest cannot authorize new implementation; R in-flight evidence change aborts safely. | H-policy identity comparison subset; snapshot digest lifecycle NONE. |
| SEC-029 T:46,61; A:7; C:32 | Validate package/version/signer identity before any write. | P authentic owned APK matches expected identity; N corrupt/mismatched identity denied; R post-audit mutation detected before publish/dispatch. | Historical auditor retained; fresh full APK verifier NONE. H-policy digest syntax is insufficient. |
| SEC-030 T:20,27; C:76 | Reject malformed ZIP/APK inputs and resource exhaustion. | P bounded valid owned fixture; N corrupt/duplicate/overbudget archive denied without partial active output; R interrupted parsing retains no active generation. | Historical oracle only; fresh parser NONE. Future test scope requires review. |
| SEC-031 T:47,62; C:67 | Enforce approved-root containment/symlink rejection at every open/copy/delete boundary. | P private contained path; N traversal/symlink escape denied; R replacement during operation cannot escape, cleanup remains contained. | H-journal cooperating trusted-root checks; C-manager metadata path guards; full provider store/cleanup OPEN. |
| SEC-032 T:48; A:9 | Publish complete source generations atomically. | P all members visible together; N partial set never active; R fault at each publication boundary yields old verified or review state. | H-journal atomic journal publication is not APK-cluster publication; fresh cluster store NONE. |
| SEC-033 C:68; A:9 | Keep referenced source generation immutable. | P pinned task reads unchanged generation; N changed member denied; R concurrent update cannot replace referenced bytes. | H-dispatch injected frozenSourceVerified only; actual immutable store OPEN. |
| SEC-034 C:48; A:9 | Garbage collection/removal cannot delete shared code still referenced by any instance. | P unreferenced generation removed; N live/foreign reference blocks delete; R concurrent reference creation/cleanup is atomic. | H-storage preserves owner rows; real source GC NONE. |
| SEC-035 T:49; C:9 | Issue unique operation IDs and commit durable dispatch before external mutation. | P one callback after durable DISPATCHED; N duplicate ID/uncertain write no callback; R crash after dispatch cannot replay. | H-journal/H-dispatch; Android durability/provider bridge OPEN. |
| SEC-036 T:49; A:16 | Enforce reachable CAS state/revision transitions. | P four legal durable transitions; N other21 state pairs leave bytes unchanged; R competing CAS has one winner. | H-protocol/H-journal G1 tests; Android adapter OPEN. |
| SEC-037 T:63; C:62 | Ambiguous exception/timeout/process death becomes review-required, never verified success. | P readback verifies exact operation; N missing/stale/partial readback not success; R interrupted DISPATCHED retained/no blind retry. | H-protocol/H-dispatch/H-journal; actual provider faults OPEN. |
| SEC-038 C:54 | SUCCEEDED_VERIFIED requires independent postcondition readback. | P exact operation/revision/peers/private generation; N mismatch never VERIFIED; R stale receipt after retry rejected. | H-protocol verifies injected readback, not real installed bytes. |
| SEC-039 C:55 | FAILED_NO_CHANGE requires proof no mutation occurred. | P provider proves unchanged state; N timeout alone insufficient; R uncertain boundary goes review-required. | H-dispatch preflight subset only; full provider result model OPEN. |
| SEC-040 C:56 | FAILED_ROLLED_BACK requires verified prior generation restoration. | P actual rollback readback; N downgrade/exception not proof; R crash during rollback remains review-required. | NONE; no rollback/session-preservation promise. |
| SEC-041 C:57–60 | Keep deferred, unsupported, security-rejected and ambiguous outcomes distinct from success. | P explicit outcome and retained state; N unsupported/unknown cannot enable action; R restart preserves uncertainty rather than relabeling success. | H-policy/H-dispatch partial vocabulary; full provider result mapping OPEN. |
| SEC-042 C:40 | Stop/query targets exact provider instance and clone. | P only intended instance affected; N wrong provider/clone rejected; R stale instance ID after replacement rejected. | No actual stop/query adapter; H-policy identity necessary only. |
| SEC-043 C:48 | Destructive removal requires explicit user authorization and durable audit. | P consent-bound target removal; N absent/wrong consent no delete; R interrupted delete reconciles exact namespace, never neighboring data. | NONE; operation UI remains disabled. |

### Additional explicit typed-output and principal protections

These clauses are separately required even when the enclosing operation is otherwise authorized. An accepted method return or a generic success Boolean cannot replace a missing output field.

| ID / source | Atomic obligation | P / N / R acceptance criteria | Fresh evidence and remaining gate |
|---|---|---|---|
| SEC-044 C:32 | Source inspection exposes verified signer set and signing lineage when available, with unavailable lineage explicit. | P verified set/available lineage bound to source; N fabricated, inconsistent or silently assumed lineage rejected; R source signing change invalidates inspection. | NONE fresh typed source inspector; H-policy digest is insufficient. |
| SEC-045 C:32 | Source inspection returns exact per-member hashes. | P all audited members and measured hashes returned; N missing/extra/changed member rejected; R byte mutation invalidates inspection before write. | Historical auditor only; fresh inspector NONE. |
| SEC-046 C:32 | Source inspection returns the validated split dependency graph. | P exact complete graph returned; N omitted/invalid dependency cannot disappear from result; R graph/source change invalidates result. | Historical K graph oracle; fresh inspector NONE. |
| SEC-047 C:32 | Source inspection returns the private manifest component catalog. | P declared components bound to inspected generation; N original-package lookup or omitted component cannot substitute; R changed generation invalidates catalog. | NONE; metadata manager catalog is not manifest catalog. |
| SEC-048 C:32 | Source inspection returns ABI requirements. | P measured declared/native requirements bound to source; N unknown/unsupported ABI not labeled compatible; R native member replacement invalidates result. | NONE fresh source inspector. |
| SEC-049 C:32 | Source inspection returns permissions from the inspected private source. | P complete source permission inventory; N original app grants or host grants not substituted; R manifest change invalidates inventory. | C-package checks fresh host manifest only; guest inspector NONE. |
| SEC-050 C:32 | Source inspection returns an explicit compatibility decision separate from metadata parsing success. | P supported/unsupported/unknown decision has stated evidence; N parsing alone cannot yield supported; R platform/provider change re-evaluates decision. | H-policy necessary claim checks only; typed source inspector NONE. |
| SEC-051 C:36 | Install result returns the exact caller operation ID. | P result binds initiating operation; N missing/wrong operation rejected; R stale result from earlier install cannot verify new operation. | H-protocol operation readback binding; actual install adapter OPEN. |
| SEC-052 C:36 | Install result returns a provider package instance ID. | P provider-owned installed instance identified independently; N missing/foreign/original-app instance rejected; R interrupted install/restart reconciles same instance without allocating a substitute silently. | NONE fresh install adapter. |
| SEC-053 C:36 | Install result returns a storage namespace ID bound to the intended clone/provider. | P installed instance and private namespace agree; N foreign/shared-wrong namespace rejected; R namespace change after restart invalidates success. | NONE; opaque CatalogKey is not provider storage authority. |
| SEC-054 C:40 | Launch returns a provider-owned guest instance ID resolved from a declared private guest component. | P actual instance/component binding returned; N host Activity identity or original-app fallback rejected; R late result after stop/replacement cannot bind a different clone. | NONE; host manager Activity is not guest launch proof. |
| SEC-055 C:44 | Update result records verified before and after package identities. | P both identities match intended transition; N missing/mismatched before or after rejected; R concurrent prior-generation change invalidates result. | H-dispatch peer generation check is partial, not full package identity result; provider adapter OPEN. |
| SEC-056 C:44 | Update result records the resulting pinned cluster generation. | P actual installed generation matches dispatched source; N stale/ambiguous generation rejected; R restart readback identifies same committed generation or review-required. | H-protocol supplied digest binding only; actual provider generation receipt OPEN. |
| SEC-057 C:44 | Update result explicitly reports data-preservation status without inferring session preservation. | P independently observed preservation status/limits; N successful code update cannot imply preserved data/session; R crash/restart verifies retained data or reports uncertainty. | NONE; no session-preservation promise. |
| SEC-058 C:44 | Update result includes independently bound readback receipt. | P receipt names exact operation/provider/affected instances/generation; N absent/stale/partial receipt cannot verify success; R restart/duplicate callbacks cannot reuse receipt for another update. | H-protocol/H-dispatch injected receipt binding; provider receipt implementation OPEN. |
| SEC-059 C:48; T:12 | Clone removal must preserve the Fonesi host application and its unrelated state. | P only authorized clone namespace removed; N host package/store/control state is never a removal target; R interrupted cleanup cannot delete host or another task's journal. | NONE for actual removal; journal/store guards are not a clone removal implementation. |
| SEC-060 A:14; T:8,18,37–42 | Verify cross-clone process-state isolation at the actual provider boundary. | P each guest process identity/state scoped correctly; N clone A cannot inspect/control clone B process-private state; R process death/reuse/restart cannot transfer identity or access. | H-policy stronger-mode prerequisite only; actual adversarial process isolation NONE. |

## 3. E83 complete-cluster obligations (not the operation journal wire format)

The following identities are APK-auditor/provider contracts. Do **not** substitute UUID peer ordering, operation-codec hashes, or the kernel's syntactically valid64hex cluster field for these proofs. `K` records exact historical source behavior; it is not blanket permission to reproduce defects or silently port it.

| ID / source | Atomic obligation | P / N / R acceptance criteria | Fresh evidence and remaining gate |
|---|---|---|---|
| SPL-001 S:30,97; K:33–52 | Preserve package, nonnegative long version and signer set in expected cluster identity. | P exact auditor identity; N changed package/version/signer denied; R source replacement invalidates expected identity. | NONE fresh complete identity; H-policy binds only supplied digest. |
| SPL-002 K:84–104; S:98,101 | Match auditor package/split naming rules and package length limit; do not invent a split-name length cap. | P allowed underscore-leading/special android/package cases per pinned auditor; N numeric-leading split segment or overlength package rejected; R decoding/re-encoding retains identity. | NONE. Kernel operation package grammar differs: do not claim complete APK acceptance parity. |
| SPL-003 K:92–104 | Require exactly one base first and unique splits in deterministic name order. | P canonical base+sorted members; N extra/missing base, duplicate/unsorted splits denied; R input ordering cannot alter canonical member identity silently. | Historical K only. |
| SPL-004 K:94–99; S:97 | Preserve verified per-member SHA and positive measured byte length. | P exact bytes/length; N changed SHA/zero/missing length denied; R concurrent file mutation detected. | Fresh full cluster verifier NONE. |
| SPL-005 K:96–97 | Require every member signer set to match cluster signer set. | P all authentic members same identity; N mixed signer denied; R signer/source replacement before import rejected. | NONE fresh; actual cryptographic validation also SEC-029. |
| SPL-006 K:105–107 | Enforce base feature/config/dependency constraints and base splitRequired presence. | P plain base with required splits present; N feature/config/dependent base or required-but-missing split rejected; R incomplete publication not activated. | Historical K only. |
| SPL-007 K:98–109 | Preserve required/provided split types and require complete coverage. | P every required type provided; N missing/invalid type denied; R provider readback cannot drop type metadata. | NONE fresh. |
| SPL-008 K:113–117; S:98 | Preserve valid empty configForSplit for non-feature config splits; reject feature config and invalid feature targets. | P base-config empty marker and valid feature target; N feature-with-config/nonfeature target rejected; R readback preserves null/empty semantics. | NONE fresh; no normalization permitted. |
| SPL-009 K:115–136; S:98 | Count uses-split edges on feature and non-feature members; require existing feature targets and acyclic graph. | P valid deep DAG; N missing target/cycle denied; R graph mutation invalidates identity before dispatch. | NONE fresh; iterative historical oracle retained. |
| SPL-010 K:39–44,63–80; S:97 | Preserve splitRequired and isolatedSplits flags in identity/digest. | P exact flags retained; N altered/omitted flag fails readback; R source flag changes invalidate generation. | NONE fresh. |
| SPL-011 K:55–81,137–138; S:99,108 | Recompute length-prefixed fonesi-package-cluster-v1 digest over all auditor metadata, not opaque claimed SHA. | P canonical known bytes/private-store parity; N field/digest tampering denied; R ordering/reopen produces identical digest. | H-codec is DIFFERENT protocol; fresh APK digest port NONE. |
| SPL-012 K:150–155; S:66,109 | Validate expected and observed identities and require exact equality. | P all members/metadata equal; N any changed/missing/extra member rejected; R stale provider readback cannot verify new generation. | H-protocol only opaque cluster/peer readback; full readback NONE. |
| SPL-013 K:154,165–166 | Readback must originate from SDK-owned private storage, never original app. | P independently read installed private bytes; N original/caller-supplied list fails; R original changes/removal cannot affect installed proof. | H-protocol privateStorageVerified is trusted callback assertion only. |
| SPL-014 K:155; S:31 | Require readback of the complete installed member set. | P all base/feature/config members accounted; N missing/extra/incomplete set denied; R partial install after crash remains review-required. | NONE at provider boundary. |
| SPL-015 K:11–29; S:31,68,100,102 | Split import requires preflight+dispatch+complete readback; execution requires import; atomic update requires execution+import. Engine-level split acceptance remains false until real typed orchestration and required evidence, not capability-object construction alone. | P valid dependency chain plus actual engine orchestration evidence; N each incomplete combination or capability-only claim denied; R capability revision change withdraws dependent claims. | H-policy tests dependency subset; full separate backend/engine orchestration NONE, acceptance false. |
| SPL-016 S:67; K:160–166 | A split-capable backend must implement typed preflight/install/inspect API. | P typed complete contract; N Boolean-only claim denied; R provider replacement rechecks API/capabilities. | No fresh provider; boundary OPEN. |
| SPL-017 S:24,32,40,69 | Reject unsupported multi-member sources before native user creation, install/update dispatch or registry mutation. | P supported owned contract case only; N single-APK provider receives zero irreversible calls; R source changes at enqueue/preflight revalidated. | H-policy no unsupported claim grant; no source ingestion/provider adapter yet. |
| SPL-018 S:25,43–44,68 | Never drop/flatten/re-sign split members or silently invoke original app fallback. | P exact signed set preserved; N unsupported set refused unchanged; R retry cannot downgrade to base-only. | Historical SpaceCore guard retained; fresh provider NONE. |
| SPL-019 S:35,45; A:8 | No production split acceptance until actual provider imports, reads back and executes signed base/feature/config cluster after restart. | P provider-owned full fixture receipt; N private copy/classloader/host proxy alone fails; R restart verifies resources/code/native dependencies. | NONE; historical E21–E23/E82 synthetic receipts not production acceptance. |
| SPL-020 S:103,111 | Historical parent receipts must be verified by exact source/artifact/result/APK binding before inclusion. | P correctly bound E78/E82 receipt pair; N missing/mismatched artifact fails evidence packaging; R rerun does not substitute mutable latest artifacts. | Historical requirement retained; new runtime harness not yet accepted. |

## 4. Provenance, platform and evidence integrity

| ID / source | Atomic obligation | P / N / R acceptance criteria | Fresh evidence and remaining gate |
|---|---|---|---|
| EVD-001 T:22,55; A:19 | Pin provider/dependency sources and complete license/SBOM/native provenance. | P every shipped artifact mapped to reviewed source/license; N opaque/unlicensed dependency blocks; R dependency update re-audited. | Official scaffold dependency pins only; provider manifest NONE. |
| EVD-002 A:18; T:55 | Reproduce native builds and verify shipped ABI/ELF page-size compatibility. | P each supported ABI and relevant4KB/16KB environment; N unsupported ABI/alignment denied; R rebuild matches reviewed sources. | Debug scaffold has no native engine; NONE for provider. |
| EVD-003 A:13 | Verify split resources/configuration/classloading/native execution with owned runtime fixtures. | P each feature on actual runtime; N missing/config-wrong/native-wrong component fails; R rotation/config/restart preserves binding. | Historical synthetic only; fresh ANDROID/PROVIDER gate OPEN. |
| EVD-004 A:17; T:21 | Validate scheduling/foreground restrictions on actual Android/OEM devices. | P allowed lifecycle schedule; N background/locked mutation denied; R Doze/reboot/process death does not bypass policy. | H-dispatch injection only; PHONE gate OPEN. |
| EVD-005 A:20,25–28 | UI evidence tiers must distinguish source, installed guest, running component and isolation. | P correct tier labels; N no ready clone from parse/build/host events; R stale capability/results cannot upgrade displayed tier. | H-manager/C-manager disabled operations and stale-token tests; real-provider UI acceptance OPEN. |
| EVD-006 A:25–26 | Bind every acceptance receipt to exact source/provider/environment; synthetic proves only synthetic row. | P reproducible exact receipt; N wrong SHA/provider/synthetic substitution denied; R artifact refresh retains immutable identity. | Frozen host vectors and exact compile logs; runtime receipt harness still OPEN. |
| EVD-007 A:21; T:66 | Release artifact/signature/dependency manifest must match reviewed sources/licenses and supported-device matrix. | P signed release upgrade/retention evidence; N debug/test artifact not release; R interrupted upgrade has recovery proof. | C-package debug-only; release/signing/PHONE NONE. |
| EVD-008 D:18 | Unattached synthetic Activity must have null baseContext, Application and Window. | P all three absent before attach; N any preattached field rejects receipt; R restart repeats precondition. | Retained historical assertion; new runtime harness NONE. |
| EVD-009 D:19 | Receipt API is integer and Boolean fields strict booleans. | P typed receipt; N34.0/string flags rejected; R decode/re-encode preserves types. | Historical oracle retained; new runtime receipt parser NONE. |
| EVD-010 D:20 | Ordinary app receipt UID must be positive, not root. | P positive observed platform UID; N0/negative/unverified rejected; R new process identity remeasured. | Historical oracle retained; no new app execution. |
| EVD-011 D:21 | Receipt run UUID is canonical and bound to exact source/APK/process/lifecycle transcript. | P complete bound transcript; N stale/partial/failure receipt rejected; R restart/run mismatch cannot inherit success. | H-codec UUID handling is not Android receipt verification; runtime harness OPEN. |
| EVD-012 D:21–23; S:9–10 | Synthetic receipts must retain false production/guest/kernel-UID claims and never imply account isolation. | P explicitly narrow host proof; N elevated synthetic claim rejected; R parent receipt aggregation cannot erase exclusions. | Documentary guard; no fresh runtime receipt. |
| EVD-013 D:12,18–21 | Use stronger E80/E81 main-thread Instrumentation/count/restart constraints, not superseded preparation-thread construction. | P exact thread/count/restart receipts; N old construction or missing events rejected; R recreation repeats same invariants. | Historical source oracle only; fresh harness NONE. |

## 5. Divergent-head persistence, authority and scheduling protections

The fourteen heads are reconciled in D:3–14. Six rows carry executable differences; eight documentation-only heads retain their historical reports. The following obligations must survive replacement even when a different implementation is chosen. Backup verification alone does not close any row (D:29).

| ID / source | Atomic obligation | P / N / R acceptance criteria | Fresh evidence and remaining gate |
|---|---|---|---|
| REC-001 D:7 | Preserve API28 process-name guard and compatible fallback. | P correct process identity per supported API; N unavailable API not invoked; R process restart re-resolves safely. | Fresh scaffold does not use old probe; replacement runtime guard NONE. |
| REC-002 D:8,27 | Marker and owner changes commit/rollback atomically. | P both durable together; N either failed write leaves both unchanged; R crash at either boundary yields retained coherent ownership. | H-storage transaction rollback subset; real queue marker/owner adapter NONE. |
| REC-003 D:8,10,27 | Reject invalid/pre-marker ownership references before enqueue mutation. | P known valid ownership; N orphan/foreign/premarker reference denied; R owner changes during transaction abort atomically. | H-storage preserves/quarantines owners, not enqueue authorization. |
| REC-004 D:8,27 | Deduplication must preserve authority, not merge tasks solely by package/source resemblance. | P same authorized identity dedups; N different authority remains distinct/rejected; R concurrent enqueue cannot upgrade consent. | H-dispatch duplicate operation/peer binding partial; new enqueuer NONE. |
| REC-005 D:9,27 | Foreground automatic update must not cancel or execute private-source tasks. | P eligible normal source progresses; N private task unchanged/unexecuted; R automation refresh/restart cannot seize ownership. | No new automatic updater; H-storage task quarantine does not implement scheduler. |
| REC-006 D:9 | An unrelated package may progress instead of global starvation. | P unrelated eligible task executes; N same blocked scope stays blocked; R concurrent unrelated work preserves both scopes. | H-dispatch scope lease permits independent scopes; full scheduler fairness/FIFO OPEN. |
| REC-007 D:10,27 | Detect private-source conflicts inside enqueue transaction and preview. | P conflict-free preview/enqueue agree; N conflict denies both; R conflict inserted between preview/commit aborts. | NONE fresh preview/enqueue adapter. |
| REC-008 D:10 | Mixed-group consent is all-or-nothing, never partial implicit authorization. | P exact whole affected set; N added/removed/foreign peer denies before callback; R group changes invalidate consent atomically. | H-dispatch/H-protocol direct binding negatives; UI/provider mixed-group transaction OPEN. |
| REC-009 D:11,27 | Preserve foreign namespace snapshots and journals without mutation or original-catalog fallback. | P own namespace read; N foreign state not adopted/deleted; R upgrade/recovery retains foreign bytes. | H-storage exact typed preservation/quarantine; H-policy identity guards; actual queue/source integration OPEN. |
| REC-010 D:11 | Maintain same-provider/package FIFO eligibility. | P earlier eligible task precedes later; N later task cannot bypass blocked predecessor; R restart/order race preserves sequence. | H-dispatch lease is not FIFO scheduler; NONE fresh scheduling implementation. |
| REC-011 D:11 | Unresolved review state blocks later mutations in the same provider/package scope. | P unrelated scope unaffected; N same scope no callback; R provider revision change does not escape barrier. | H-dispatch history scan and revision-independent scope key; Android/provider adapter OPEN. |
| REC-012 D:11,27 | Controls and scheduling eligibility must be namespace-aware. | P control affects intended provider tasks; N foreign cancel/resume rejected; R provider switch cannot operate stale namespace. | H-manager exact display CatalogKey does not authorize tasks; controls/scheduler NONE. |
| REC-013 D:8,27; T:49 | Decode legacy marker/task authority strictly, retaining ambiguous bytes rather than inventing authority. | P known schema/codec projection; N unknown schema/state/owner quarantined; R failed migration rolls back target and preserves source bytes. | H-storage schema1–6 synthetic tests; actual Android migration OPEN. |

## 6. Rebuild corrections that constrain future ports

These are approved rebuild decisions, not claims that E35 originally specified a wire codec. Preserve their decision provenance and historical unsafe observations; do not use differential agreement with a defective oracle as correctness.

- **BRG-001** H-identity: provider ID/revision/environment require exact well-formed Unicode scalars, no Cc, fixed leading/trailing whitespace rejection including NBSP,256UTF16/768UTF8 bounds. P valid Unicode retains exact hash identity; N invalid alias inputs rejected; R persisted invalid identities retained/quarantined, never remapped/dispatched. Portable parity exists; Android authority/storage bridge OPEN. Opaque catalog `engine_key`/`clone_id` remain exact TEXT, including empty values; do not impose this grammar on them.
- **BRG-002** H-codec/H-journal: bounded owned input, checksum, semantic validation, expected operation UUID and exact canonical re-encode equality. P canonicalv2 byte parity; N noncanonical/unknown-version/wrong-operation rejected; R caller mutation/recovery never silently rewrites old bytes or replays dispatch. Host and in-memory portable proof exists; Android journal adapter OPEN. This document requests no denied-envelope reproduction.
- **BRG-003** H-policy: the approved D3 stronger-isolation allowlist is necessary for adversarial claim/request and protected third-party operations. P independent truth table agrees with intended contract; N HOST_UID_NAMESPACE/UNKNOWN cannot gain permission from caller-supplied claims; R evidence identity changes deny. Host truth table is not a provider attestation implementation.

## E35 acceptance-matrix coverage index

This index is not a grouped acceptance shortcut; the linked atomic rows retain their own open gates.

| P-row / exact A line | Atomic rows |
|---|---|
| P-01 /7 | SEC-029–030,044–050; SPL-001–005 |
| P-02 /8 | SPL-001–019 |
| P-03 /9 | SEC-031–034; SPL-011–014 |
| P-04 /10 | SEC-012,054 |
| P-05 /11 | SEC-009–011 |
| P-06 /12 | SEC-013 |
| P-07 /13 | EVD-003; SPL-007–010 |
| P-08 /14 | SEC-003–011,014–015,060 |
| P-09 /15 | SEC-027,033,035–042,051–058; SPL-012–019 |
| P-10 /16 | SEC-032,035–040; REC-002,011,013 |
| P-11 /17 | SEC-001,019–020; EVD-004; REC-005–012 |
| P-12 /18 | SEC-024; EVD-002 |
| P-13 /19 | SEC-022; EVD-001 |
| P-14 /20 | SEC-023–028; EVD-005 |
| P-15 /21 | EVD-007 |

All eight T:59–66 acceptance clauses map respectively to SEC-004–007, SEC-008–011, SEC-029/SPL-001–018, SEC-031, SEC-032/035–040, SEC-012, SEC-001/019–020, EVD-001/007. The assets T:7–12 are protected by those rows plus SEC-014–018,021–060; this is not proof that an actual provider currently protects them.

## Review and closure rules

1. Each ID above remains individually traceable; grouped app/product coverage cannot substitute for these security exits. Positive, negative and race/recovery cases must be tied to exact implementation and receipts before accepting that layer.
2. `NONE`, partial H evidence and C-only evidence remain open at ANDROID/PROVIDER/PHONE. No percentage or blanket security acceptance follows from test counts. Historical A:29/S:55,113 weighted estimates are deliberately not adopted as fresh progress.
3. Preserve historical tests/notices/source until a separately reviewed replacement proves the relevant rows. StageA root build refusal did not delete runtime or waive these protections.
4. Parent review must reconcile this ledger with the normative product/automation/access/backup/style ledger, resolve any duplicate IDs by cross-reference (not silent deletion), and identify any unassigned normative clause before proposing R1 acceptance. This document itself closes no R1 or product gate.
