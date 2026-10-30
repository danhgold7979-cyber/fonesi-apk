# R5.3 pre-code plan: exact composite catalog identity

2026-10-30 23:04 UTC. **Proposal only; root review required before source changes.** B3c published f1b226e4ccfbff1889bd00cb2860aad35246e5db/tree483414fd447d5c05cbf8cd856f48732215bb3fa1; parent reports full run41141819383 SUCCESS including actualJDK17.0.21 and731checks/342vectors per process. This proposal changes the existing active manager model/controller/view boundary, not another standalone laboratory.

## Evidenced mismatch and schema semantics

All six immutable E82 Room schema oracles in `rebuild-storage/spec/legacy-room/{1..6}.json` define `clone_metadata.engineKey` and `cloneId` as `TEXT NOT NULL`, with composite primary key `(engineKey, cloneId)`. Their CREATE statements specify no length CHECK, UUID syntax, trimming, normalization or custom collation for either column. The current synthetic R3 target similarly uses `manager_catalog(engine_key TEXT NOT NULL, clone_id TEXT NOT NULL, ... PRIMARY KEY(engine_key,clone_id))`.

Current `ManagerModel.Entry.id` is UUID; publication duplicate detection, visible selection and controller/view events use UUID alone. Thus opaque historical IDs cannot be represented, and identical clone ID text in distinct engine namespaces cannot remain independently selectable. A random replacement UUID or concatenated string would hide rather than repair the mismatch.

Important distinction: `populate_quarantined_catalog` currently projects only nonempty strings for these fields and otherwise rejects the synthetic import while retaining source; this is a reference projection policy, **not a schema prohibition on empty strings**. Its full preserved rows and original sources must not be rewritten. This slice neither changes that importer nor declares all schema-valid rows already projectable.

## Exact proposed value contract

Add a nested `public static final ManagerModel.CatalogKey` to the existing model (no new module):

- Two immutable final String components: `engineKey`, `cloneId`.
- Reject null components only. No invented grammar or length restriction. Preserve empty, whitespace, case, punctuation/delimiters, leading zeros, UUID-looking and non-UUID text exactly; no trim, Unicode normalization, case fold, UUID parse or replacement identity.
- Equality requires both String values, with matching two-component hashCode. Do not serialize identity by concatenating a delimiter. Distinct pairs remain distinct even if hashes collide. No raw-key diagnostic/toString logging is needed.
- Java String equality preserves exact code units; this is **not** a claim to decode arbitrary malformed SQLite bytes losslessly. Future storage decoding must define that boundary and quarantine/retain undecodable records rather than substitute replacement text.
- This is a display/catalog reference only. It is not kernel operation UUID, verified provider ID/revision, capability evidence, path, launcher token, consent or authority. B1b provider identity grammar/limits do not apply to this historical display reference.

Change Entry constructor to `Entry(CatalogKey key, String packageName, String name, String group, Status status)` and expose `key` instead of UUID `id`. Remove redundant `provider` string (currently used only by a test); namespace now has one exact source, `key.engineKey`, so a separate provider label cannot contradict identity. Other display-field validation and UNVERIFIED/QUARANTINED/UNAVAILABLE semantics remain unchanged. Supporting every legacy display-field shape is outside this identity-only slice and cannot be silently claimed.

## Resource budgets versus identity validity

The schemas supply no numeric text limit to inherit. Do not copy the kernel's256UTF16/768UTF8 provider limits, invent an ID cap or silently truncate. This slice adds no adapter, external input or seeded rows; runtime Activity remains unconnected/empty. Test inputs stay small and synthetic.

Before any catalog source is wired, a separate reviewed adapter plan must bound raw input/page bytes, per-field allocation/normalization work, cancellation and total rendered window. The previously suggested128rows/256KiB are provisional transport budgets, not accepted schema limits. An oversized/undecodable record must leave source bytes intact and produce explicit quarantine/load failure, not empty-success, remapping or silent row loss. Production-scale ingestion/search/paging remains blocked until that boundary is specified and tested. This identity value alone must not be represented as safe unbounded external ingestion.

## Concrete implementation surface

1. `rebuild-android/app/src/main/java/vn/fonesi/rebuild/model/ManagerModel.java`: nested CatalogKey; Entry signature; Set<CatalogKey> snapshots/selection; composite duplicate detection; visible membership/filter pruning. Full validation before publish remains atomic.
2. `.../model/ManagerController.java`: Render selected set and `select(Token,CatalogKey,boolean)` only; retain owner/epoch/revision tokens, reentrancy, refresh cancellation and foreground guards unchanged.
3. `.../ManagerActivity.java`: checkbox membership and captured row callback use exact `entry.key`. Keep main-thread assertion, listener suppression, disabled saved-view state, truthful empty/loading/failure state and all four unsupported buttons unchanged. No database reads, new permissions or real rows.
4. `rebuild-android/contract-tests/ManagerModelTest.java` and `ManagerControllerTest.java`: migrate existing synthetic UUID rows to deterministic string keys; preserve all prior assertions except the old blank-provider rejection, which becomes a documented schema-compatible key-preservation assertion. Continue rejecting null key/component and unchanged invalid display fields. Do not weaken stale/reentrancy/overflow/concurrency tests.
5. Adjust a focused existing source tripwire only if its exact old UUID expectation requires it. `check-model.sh` already compiles model/controller directly; nested value needs no new dependency/source-set wiring. Existing full Android compile/lint/APK semantic workflow must pass.

No changes to kernel/logical codecs, R3 source/target schema, historical fixtures, existing databases, migrations, provider implementation, access-lock authorization, legacy implementation or SDK configuration.

## Required tests and review gates

- Same opaque cloneId in two engines: two rows publish, each selection changes only its own composite key; select-visible and filter pruning retain exact namespace identity.
- Same exact pair twice rejects without partial publication, even if label/package/status differ. Equal independently constructed keys behave identically in HashSet; reversed/delimiter-containing pairs stay distinct.
- UUID text/non-UUID text/leading zeros/case/whitespace/empty strings/Unicode composed versus decomposed values preserve exact components and equality. Null key/components reject; no runtime-authority implication.
- Immutable key fields, defensive list/set snapshots and caller-list mutation after publication remain covered.
- Unknown composite key refuses selection. Existing revision/refresh generation/owner/epoch boundaries reject stale row events and cross-controller events, including a refreshed row with the same cloneId in another engine. Refresh clears prior selections; interrupted/duplicate completions do not republish old data.
- No fake seeded Activity rows, provider/storage calls, new permissions or enabled unsupported actions. InstalledJDK17 model/controller tests plus actual D8/Android lint/packaged semantic gates; UI/device/TalkBack/runtime execution remains NOTRUN.
- Independent review of identity mapping and preserved guards before root publication. Stop on opaque identity ambiguity, required storage decoding decisions, unexplained test drift or new external-data dependency. Denied experiment remains excluded and is not retried.

Acceptance is only correct composite metadata identity across the existing manager's model/controller/view code and its tested stale-event boundaries. It is not completed R3 persistence, whole R5 UI, migration, provider or device acceptance. Root approval of this plan is required before implementation.

## Root approval recorded before code — 2026-10-30 23:06 UTC

Root approved this exact identity-only correction: nested immutable composite CatalogKey, existing model/controller/Activity bindings and tests; exact opaque strings/both components, no normalization/cap, no catalog input/provider activation. Display validation and owner/epoch/revision/reentrancy/foreground gates stay unchanged. Independent review then actualJDK17/AndroidCI required after root publication. No adapter/dependency/device/legacy deletion/denied experiment authorized.

## Implementation evidence — 2026-10-30 23:09 UTC

Three existing production files now use exact immutable CatalogKey for row/selection identity; Activity retains empty/unconnected behavior and disabled operations. Only the precise existing Activity callback tripwire changed from `entry.id` to `entry.key`. Existing synthetic tests now use deterministic composite keys. The former same-UUID/different-provider rejection is intentionally corrected to two independent rows; exact duplicate composite keys with differing payload still reject before publication. No other display validation or lifecycle/overflow/reentrancy guard was removed.

Local canonical runner passes158model+92controller checks, including equal-hash unequal keys, opaque empty/space/Unicode/UUID text, namespace-isolated selections, explicit refresh replacement and filter pruning.69tooling checks and unchanged kernel/history/logical342codec/17,317B2/51SQLite regression suites pass. LocalJDK21 fallback is not actualJDK17/Android validation; exact new-head hosted compile/lint/package and JDK17 remain pending publication. No device/UI/runtime/catalog migration/provider proof is claimed.
