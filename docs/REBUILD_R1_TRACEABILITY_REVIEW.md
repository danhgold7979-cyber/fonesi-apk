# R1 documentary traceability review — not gate acceptance

2026-10-31. Read-only requirements review; no new implementation scope. R1 remains OPEN: the master asks for each requirement mapped to evidence, but the detailed execution ledger begins at R2 and currently lacks a complete atomic requirement-ID/acceptance table.

## Authoritative inputs

Historical source requirements read at E82 `2fac016e3b28e4d9f21390c221cad1df7a382c86`: `docs/PLAN.md`, all32 `docs/ROADMAP.md` steps, `docs/DEVICE_MATRIX.md`, `docs/engine/THREAT_MODEL.md`, and `docs/engine/ACCEPTANCE_MATRIX.md`. Their implementation/progress statements are historical, not fresh-rebuild acceptance. In particular the old weighted28% estimate is not adopted. E83 exact contract source `58d52f4aa05c8c8d5256e7125b522815418b1e02` and the14-head reconciliation remain additional inputs, not replaceable by main's older files.

Current ordering is REBUILD_MASTER_PLAN.md, not the original phase order; the original required behaviors remain. Supplementary access-control, foreground automation, configuration-backup and style/badge specifications must still be atomized against that pinned history before accepting R1.

## Coverage crosswalk from all32 roadmap steps

This table is a coverage index, **not a claim of requirement completion**. HOST=direct host contracts; COMPILE=Android source/lint/package; ANDROID=actual framework/runtime; PROVIDER=eligible real engine; PHONE=physical/OEM/app acceptance.

| Historical steps / required behavior | Rebuild gate and current evidence | Missing acceptance layer |
|---|---|---|
|1–4 scope, no-root, honest baseline, pinned toolchain/build | R0 accepted narrowly; frozen master scope, fresh debug COMPILE and exact source/CI receipts | R1 atomic requirement mapping; no product acceptance from builds |
|5–8 provider license/source/ABI/base-split scope and controlled synthetic lifecycle | R4 unresolved; E84 no eligible provider; R2 policy HOST, no executable authority from claims | Reviewed provider/architecture/provenance plus ANDROID→PROVIDER evidence |
|9–12 original Facebook + two clean clones, isolation/restart/essential features, direct shortcuts | R4/R5/R7; no new provider or guest execution | Synthetic first, then PHONE/provider/session/launcher proof; no credential copying |
|13–16 stable composite IDs, lifecycle CRUD/reconciliation, shortcuts, original-app removal retention | R3/R5; opaque CatalogKey/model HOST; native empty catalog COMPILE; schema1–6 synthetic retention | Real Android migration/ownership/recovery and provider CRUD; shortcut/auth/launcher runtime |
|17–20 frozen signed base/split source, durable serialized queue, shared-peer consent, real update/readback/recovery | R2 accepted Linux/JVM only; Java17 logical/codec parity; R3 synthetic quarantine | Android durable bridge/source ingestion and PROVIDER atomic updates/readback/crash/data retention |
|21–24 all remaining mandatory apps, per-version device matrix, mixed workloads | R4/R7; no app inferred from another | Zalo/TikTok/Messenger/Threads/X/Shopee/YouTube plus Facebook remain required; app/version/API/ABI/PHONE evidence |
|25 search/group/name/style/badge/batch UI | R5 page search/group/selection HOST+COMPILE; immutable display IDs | Editing/style/badge/real catalog writer and complete native/accessibility acceptance |
|26 system-backed access lock, unsupported/enrollment-change handling, truthful uninstall warning | R5; provider disabled; current-user storage-unlocked check is NOT access authentication | Fresh reviewed access-control implementation and actual framework/auth lifecycle proof |
|27 permissions/notifications/storage/battery/error UX | R5/R7; no permission requested in diagnostic app; generic retained failure state | Actual host-versus-per-clone permission model and tested native/OEM UX |
|28 configuration-only backup/import/preview/map/transaction, secret-free diagnostics | R3/R5; no export/import activation | Complete original-row/identity mapping, malformed import and filtered diagnostics; SAF/runtime; never session backup |
|29–32 resource measures,>=2h target workload, Samsung+other OEM, signed upgrade/release | R7; debug-only COMPILE, no release | Stored-vs-running limits measured, API/ABI/page-size/app matrix, PHONE/signing/upgrades; no arbitrary clone cap |

## Threat and acceptance rows that must not disappear

Preserve E35 acceptance1–8: cross-clone files/DB/URI denial; notification/service/provider separation; stale/malformed cluster pre-write rejection; path/symlink containment including cleanup; crash outcomes old-verified or REVIEW_REQUIRED; lifecycle evidence not host events; locked/disabled manager cannot initialize/mutate guests; release dependency/native/license binding. Host policy tests cover only parts of preflight/state semantics. Strong isolation labels are necessary policy conditions, not attestations. Same-UID directories and the new app-private directory-mode exception are not hostile-guest isolation.

Mandatory app set stays Facebook, Messenger, Zalo, TikTok, Threads, X, Shopee, YouTube. SamsungSM-A525F is an initial proposed test device, not a product whitelist; its installed Android/ABI remains unverified. Fresh minSdk26/target36 are compile configuration, not engine support across that range. Distinguish untested from unsupported; every performance/resource limit requires evidence, not a guessed product cap.

## Smallest remaining R1 closure package for parent review

1. Assign atomic IDs to each normative PLAN/ROADMAP and supplementary access/automation/style/config requirement; link exact SHA/path/section, rather than letting this grouped crosswalk stand in for all requirements.
2. Map each ID to current source/test/report or explicitly NOT IMPLEMENTED; identify HOST, COMPILE, ANDROID, PROVIDER and PHONE evidence separately. Freeze positive, negative, race/recovery and acceptance stop conditions; no test-count percentages.
3. Reconcile E35 P-rows/E83 and divergent-head authority/quarantine/schema protections into that same table; retain older receipts as historical only. Record actual current image/runtime absence from REBUILD_RUNTIME_GATE_HANDOFF.md without treating it as permanent environment impossibility.
4. Define R1's documentary exit: no unassigned normative requirement, no silent scope drop, explicit provider/runtime/owner-decision dependency per row, independent review of the table. Only then propose R1 acceptance to root; it does not accept R3–R7.

This package is independent of runtime installation and closes an existing master-plan requirement. No further code, provider choice, environment provisioning, device deployment or source deletion is authorized by this review. Further manager/adapter code is not universally impossible, but starting it before a reviewed requirement slice risks another disconnected partial implementation.
