# Fresh APK task contracts — version 1

8 original phases, 32 original steps, 4 phone gates. **4/32 tasks accepted (12.5% of tasks; NOT functionality). P0: 4/4 tasks.** T01/T02 documentary and T03/T04 exact-source HOST/COMPILE accepted; all runtime, product and release acceptance remains open. Read `requirements.json` for the full atomic obligations mapped to these contracts. Each task requires its mapped requirement-specific positive/negative/race/stop conditions in addition to the contract below. This file does not replace or shorten the 362 assertions.

## Global execution rules

- Preserve original ROADMAP step meaning. Archive-first is a prerequisite amendment, not renumbering. Old source/tests/oracles are archive-only; write independent new implementation/tests. Original docs/UI XML/notices/schema are reference inputs outside active code; wrapper reuse requires exact hash/provenance review.
- Main stays stable. Plan before code on short `phase-NN` branches; independent review before fresh source. Never force-rewrite history or delete old checkouts. Remote cleanup belongs to parent after durable recovery and exact SHA recheck.
- Java/View + SQLite supersedes original Kotlin/Compose + Room, without claiming Room implemented. Preserve green branding reference; yellow circle is chat reporting only. Exactly six style accents; bounded plain-text badge, no URL/CSS/I/O.
- HOST is host synthetic execution; COMPILE is build/lint/package; ANDROID is actual framework lifecycle; PROVIDER is eligible engine behavior; PHONE is physical/OEM/app/account evidence. No tier inherits another; historical PASS never accepts this rebuild. Every receipt records exact source SHA, command, environment, result/counts and artifact hash.
- Provider unavailable by default. No fake clone/original/web fallback, third-party signature/package modification, identity spoofing, credential bypass, session copying or bulk social automation. Host UID/private directories/loaders do not prove isolation. Unknown outcomes fail closed and reconcile without blind replay.
- Cloud-independent work may proceed while phone/provider gates remain open; mark dependencies explicitly and keep unsafe actions disabled. No new purchase/license activation/security/device/account action without needed authority. Owner installs and logs in locally; no credentials in chat.
- Reuse unchanged PASS only with unchanged source/dependencies; run affected checks after edits and one aggregate checkpoint owner. Diagnose exact CI failures without weakening predicates. Stop any product-completion claim while required evidence is missing.

## APK-P0-T01 — Freeze scope and recovery-first amendment

Status: **ACCEPTED_DOCUMENTARY**; accepted evidence: `docs/evidence/documentary-acceptance.json` (T01/T02 only; clean phase-00, not historical main).

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L19`

> 1. Tổng hợp yêu cầu và giới hạn: nhiều ứng dụng, nhiều vùng tài khoản, mở trực tiếp bằng biểu tượng, không root, cập nhật theo ứng dụng gốc. Ghi rõ sự phụ thuộc bộ quản lý và giới hạn tài nguyên.

- **Inputs:** E82 PLAN/ROADMAP, E83 provider contract, all four atomic ledgers; full remote/local refs and worktree inventory
- **Outputs:** 362-row immutable source ledger, 32-task crosswalk, exact archive/remove/reference manifest; verified fresh backup and restore instructions
- **Interfaces:** requirements.json: stable ID, source SHA/path/line/assertion, task IDs, evidence need; recovery manifest: ref/SHA/blob/mode/hash/disposition
- **Dependencies:** None; before active-tree retirement, durable backup receipt and parent archive review/authorization required. Independent complete plan review before fresh source
- **Errors, cancel and recovery:** Ref drift or missing requirement stops retirement. Cancel leaves original trees/refs unchanged. No force rewrite or original reset; restore only into empty destination. Old implementation stays archive-only, not parity evidence.
- **Positive / negative / race tests:** Positive: 362 unique IDs and every original task mapped; negative: omitted BRG bullet or changed ref rejected; race: re-read refs/status before action; restore all refs/trees and sample code/UI/schema hashes.
- **Acceptance:** Independent review accepts complete documentary mapping and archive-first amendment; archive usable off checkout with exact recoverability. Does NOT accept any implementation.
- **Required evidence tiers:** HOST.

## APK-P0-T02 — Honest clean baseline and evidence registry

Status: **ACCEPTED_DOCUMENTARY**; accepted evidence: `docs/evidence/documentary-acceptance.json` (T01/T02 only; clean phase-00, not historical main).

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L20`

> 2. Kiểm tra mã hiện có: phân biệt chức năng đã code, chức năng mới có hợp đồng API, chức năng chưa có và kết quả chưa được kiểm thử. Không coi `UnavailableCloneEngine` là engine.

- **Inputs:** Verified recovery manifest; remote/main and old rebuild snapshots; frozen requirements
- **Outputs:** Fresh active-tree classification; initial STATUS with 0/32 completed, now 2 documentary tasks accepted and 0 product requirements accepted; historical references separately labeled; per-requirement NOT_IMPLEMENTED baseline
- **Interfaces:** status.json: phase/task/status/exact SHA/evidence receipt; reference manifest maps original SHA+path to preserved hash
- **Dependencies:** T01 recovery gate; parent approves exact cleanup checkpoint
- **Errors, cancel and recovery:** If any active old source/test remains, baseline fails. Missing gate never becomes PASS. Cancel no cleanup; accidental omission restored from verified snapshot, not broad reset.
- **Positive / negative / race tests:** Positive: only approved fresh files active; negative: old kernel/oracle test and stale accepted claim detected; race: source SHA changes invalidate manifest.
- **Acceptance:** All old implementation and tests excluded from active tree; original docs/UI/notices/schema retained as references; new source provenance distinguishable. No provider available.
- **Required evidence tiers:** HOST.

## APK-P0-T03 — Pin fresh toolchain and independently build foundation

Status: **ACCEPTED_HOST_COMPILE**; accepted evidence: `docs/evidence/foundation-acceptance.json` (exact a3372fa6 only; no runtime acceptance).

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L21`

> 3. Cài/pin toolchain; build APK nền tảng và chạy kiểm tra logic lập kế hoạch cập nhật. Lưu hướng dẫn tái tạo, không đưa khóa ký phát hành vào Git.

- **Inputs:** Reviewed license/provenance/toolchain pins; T01 requirements and archive gate; Java/View+SQLite architecture decision
- **Outputs:** Fresh native skeleton, independently written update-planning contracts/tests, reproducible build instructions and CI for phase-* and PR targets
- **Interfaces:** Gradle toolchain checksum manifest; Android application manifest; test result schema; default provider API explicitly unavailable
- **Dependencies:** T01/T02 approved documentary checkpoint; reviewed infrastructure hashes before wrapper reuse
- **Errors, cancel and recovery:** Unavailable tools produce BLOCKED not success. Checksum/license uncertainty stops bootstrap. Cancel stops before mutation; rerun affected test only after diagnosis. Never lower SDK/disable TLS or predicates to pass.
- **Positive / negative / race tests:** Positive: meaningful independent host tests and debug compile/lint/package audit; negative: invalid update plan, zero-test run, permission regression fail; race: source changes invalidate receipt.
- **Acceptance:** Exact-SHA tests and debug APK compile/lint/package/signature/permission checks pass. min26/target36 provisional compile baseline, NOT support coverage. No release key or provider imported.
- **Required evidence tiers:** HOST, COMPILE.

## APK-P0-T04 — Record reproducible baseline artifact and limits

Status: **ACCEPTED_HOST_COMPILE**; accepted evidence: `docs/evidence/foundation-acceptance.json` (exact a3372fa6 only; no runtime acceptance).

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L22`

> 4. Ghi báo cáo baseline, đường dẫn APK, phiên bản và các kiểm tra đã/chưa chạy để so sánh các bản sau.

- **Inputs:** T03 exact commit, commands/tool versions, test logs and debug APK
- **Outputs:** Baseline receipt with APK path/version/SHA256, source SHA, test counts and per-tier not-run list; reproduction recipe
- **Interfaces:** EvidenceReceipt(sourceSHA, toolchain, command, exit, artifact hash, tier, scope, timestamp)
- **Dependencies:** T03 actual output; artifact delivery only after security/package check
- **Errors, cancel and recovery:** Missing APK/hash or mismatched source blocks acceptance. Cancel preserves receipts; rerun changed checks without inheriting historical evidence.
- **Positive / negative / race tests:** Positive: clean rebuild reproduces declared semantics; negative: stale SHA and missing artifact rejected; race: post-test edit invalidates receipt.
- **Acceptance:** Artifact and provenance resolvable; HOST/COMPILE claims bounded; ANDROID/PROVIDER/PHONE explicitly NOT RUN; phase0 only closes if T01–04 accepted.
- **Required evidence tiers:** HOST, COMPILE.

## APK-P1-T05 — Provider candidate matrix

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L30`

> 5. Lập bảng ứng viên: giấy phép, phiên bản mã nguồn, Android/ABI hỗ trợ, yêu cầu SDK/NDK, base/split APK, Google Play Services và cách engine quản lý phiên bản dùng chung/từng bản. Tuyên bố của README chỉ là thông tin cần kiểm tra.

- **Inputs:** Pinned candidate source/docs/license metadata; target eight apps and signed split/update requirements
- **Outputs:** Versioned candidate table with provenance, license, API/ABI, NDK/SDK, base/split, GMS and shared-version scope; unmet capabilities
- **Interfaces:** ProviderCandidate record references immutable revision/artifact/environment; no runtime authority from README
- **Dependencies:** T01; research may proceed before runtime gates
- **Errors, cancel and recovery:** Unknown license/source/capability stays unknown; no SDK purchase/contact/license activation. Cancel retains research only; changed revision requires reevaluation.
- **Positive / negative / race tests:** Positive: each field evidence-linked; negative: README-only support not accepted; race: revision mismatch invalidates candidate score.
- **Acceptance:** Each candidate has reproducible evidence or explicit gap; no selection implied by table.
- **Required evidence tiers:** HOST.

## APK-P1-T06 — Provider initialization and security review

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L31`

> 6. Rà đường chạy khởi tạo, quyền, dependency/native binary, việc ghi/gửi log và các thay đổi thông tin thiết bị. Loại khỏi tích hợp các hành vi không thuộc yêu cầu; không thu thập tài khoản hoặc bí mật đăng nhập.

- **Inputs:** T05 candidate source/provenance and complete dependencies/native binaries
- **Outputs:** Initialization/permission/network/log/device-information threat review, component license inventory, excluded behavior report
- **Interfaces:** Capability policy binds revision/artifact/environment and actual authority; secrets never enter diagnostics
- **Dependencies:** T05; separate authorization for new legal/security obligations
- **Errors, cancel and recovery:** Identity spoofing, uncontrolled exfiltration or unknown native provenance blocks candidate. Cancel before activation. Removed unsafe behavior must be tested, not bypassed.
- **Positive / negative / race tests:** Positive: bounded initialization/permission inventory; negative: hidden sender/log token/device spoof path rejected; race: dependency update invalidates audit.
- **Acceptance:** No uncontrolled data export or prohibited behavior in candidate to be built; exact permissions and legal gates resolved.
- **Required evidence tiers:** HOST.

## APK-P1-T07 — Build authorized provider candidate

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L32`

> 7. Build engine từ nguồn có quyền sử dụng hoặc SDK đã được cấp phép. Giữ TLS/chữ ký/checksum; chạy các kiểm tra khả thi trong môi trường cloud. Ghi riêng phần không chạy được do thiếu thiết bị/ABI.

- **Inputs:** T06 reviewed source or already licensed SDK; pinned toolchain/artifact hashes
- **Outputs:** Reproducible isolated candidate build and feasibility report separating unavailable runtime/ABI
- **Interfaces:** Opt-in candidate build boundary; production default contains no provider until eligible
- **Dependencies:** T05/T06; actual legal rights and bootstrap authority
- **Errors, cancel and recovery:** TLS/signature/checksum failure stops. No commercial procurement or license consent. Cancel removes only temporary output; partial build never eligibility evidence.
- **Positive / negative / race tests:** Positive: authorized artifact hashes and build; negative: tamper/missing license/unsupported ABI rejected; race: artifact drift invalidates prior review.
- **Acceptance:** Build provenance valid and limitations explicit; compiling is not provider acceptance.
- **Required evidence tiers:** HOST, COMPILE.

## APK-P1-T08 — Minimal owned-fixture provider adapter

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L33`

> 8. Tích hợp một adapter tối thiểu; thử tạo, mở, đọc trạng thái và xóa vùng dữ liệu với ứng dụng kiểm thử do dự án kiểm soát. Xác định chính xác mô hình dữ liệu và phạm vi cập nhật. Chuẩn bị APK PoC chỉ khi phần nền an toàn để thử.

- **Inputs:** T07 candidate; owned synthetic app/source; exact namespace/signed generation contract
- **Outputs:** Create/open/read state/delete PoC, actual lifecycle/isolation readbacks, shared/per-instance version model; otherwise unavailable adapter
- **Interfaces:** ProviderPort typed result: unavailable/unsupported/denied/uncertain/success with private exact identity readback; no original/web fallback
- **Dependencies:** T06/T07 before runtime; synthetic framework gate before third-party accounts
- **Errors, cancel and recovery:** Unknown outcome quarantines and reconciles; never blind replay. Cancel before dispatch has zero side effects; after dispatch requires readback. No loader/UID/directory-only isolation claim.
- **Positive / negative / race tests:** Positive: actual framework lifecycle and isolated fixture A/B; negative: cross-read, wrong identity, absent provider; race: provider restart during operation.
- **Acceptance:** Owned fixture demonstrably runs in private region, authority/lifecycle/readback verified; safe PoC artifact available, or gate remains BLOCKED.
- **Required evidence tiers:** HOST, COMPILE, ANDROID, PROVIDER.

## APK-P2-T09 — Samsung owned-fixture then Facebook A/B setup

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L43`

> 9. Đưa bản PoC lên Samsung không root. Xác minh thông tin máy/Android/ABI và thử ứng dụng kiểm thử trước; sau đó tạo Facebook phụ A và B bên cạnh Facebook gốc. Cài đặt APK trên máy cần thao tác xác nhận của người dùng.

- **Inputs:** T08 safe PoC; authorized Samsung SM-A525F actual Android/ABI; owner installation consent
- **Outputs:** Device receipt; fixture runtime evidence; Facebook original + clean A/B instance inventory
- **Interfaces:** Phone checklist records model/API/ABI/app version/provider revision; owner confirms install
- **Dependencies:** T08 synthetic safety and explicit device authority; no account entry yet
- **Errors, cancel and recovery:** Absent phone/unknown Android blocks PHONE only. Cancel installation before device changes; failure preserves original app/data and explains recovery.
- **Positive / negative / race tests:** Positive: no-root fixture then two clean regions; negative: unsafe fixture or root requirement stops; race: install interruption/permission denial.
- **Acceptance:** Fixture safety established on exact device before Facebook account test; no account/session copied.
- **Required evidence tiers:** ANDROID, PROVIDER, PHONE.

## APK-P2-T10 — Independent owner-login isolation acceptance

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L44`

> 10. Người dùng đăng nhập hai tài khoản thử nghiệm do mình kiểm soát. Kiểm tra dữ liệu không trộn, đăng xuất A không đăng xuất B, làm sạch/xóa A không ảnh hưởng B hoặc Facebook gốc. Không tuyên bố cách ly tương đương sandbox hệ điều hành chỉ từ mô hình engine.

- **Inputs:** T09 original+A/B and owner-controlled test accounts
- **Outputs:** Redacted isolation matrix: A/B/original login/logout/clear/delete outcomes
- **Interfaces:** Owner enters credentials locally; results are IDs/outcomes only, never OTP/token/account contents
- **Dependencies:** T09 safety; owner participation and data-operation consent
- **Errors, cancel and recovery:** Any mixing/loss stops dependent acceptance; cancellation never clears another region; uncertain deletion retained for reconciliation.
- **Positive / negative / race tests:** Positive: two distinct accounts and separate state; negative: cross-access denied and logout A preserves B; race: delete/clear A with B and original active.
- **Acceptance:** Essential isolation cases pass on phone; do not equate engine region with OS sandbox without stronger evidence.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P2-T11 — Lifecycle and essential feature interruption tests

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L45`

> 11. Đóng/mở lại, hệ thống dừng tiến trình, khởi động lại máy và thử mất mạng/thu hồi quyền. Kiểm tra camera, chọn ảnh, liên kết đăng nhập, video và thông báo; lưu kết quả từng tính năng, kể cả lỗi.

- **Inputs:** T09/T10 safe instances; authorized network/permission/restart cases
- **Outputs:** Per-feature camera/photo/login link/video/notification and lifecycle result matrix
- **Interfaces:** Lifecycle receipt binds device/app/provider/config and interruption; unknown never supported
- **Dependencies:** T09/T10; destructive device actions need owner consent
- **Errors, cancel and recovery:** Failures remain open requirements. Cancel returns to safe state; no global permission/reboot change without owner action.
- **Positive / negative / race tests:** Positive: reopen/process-death/reboot and features; negative: revoked permission/offline produces honest failure; race: background kill during callback.
- **Acceptance:** Essential restore/isolation cases pass; each remaining feature failure explicitly blocks full support claim.
- **Required evidence tiers:** ANDROID, PROVIDER, PHONE.

## APK-P2-T12 — Phone gate 1 exact shortcuts

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L46`

> 12. Ghim hai biểu tượng thử nghiệm và xác minh từng biểu tượng mở thẳng đúng bản/tài khoản. Gửi checklist kết quả; sửa và thử lại đúng lỗi đã phát hiện.

- **Inputs:** T09–11 evidence; launcher pin confirmation and distinct instance identities
- **Outputs:** Two pinned entries opening exact A/B; gate1 checklist and defect retest receipts
- **Interfaces:** Shortcut identity includes provider namespace + opaque ID; never execution authority from display/catalog key
- **Dependencies:** T09–11; launcher confirmation by owner
- **Errors, cancel and recovery:** Deleted/stale target yields unavailable, not original chooser fallback. Cancel pin leaves no assumed shortcut. Recreated ID must not revive stale intent.
- **Positive / negative / race tests:** Positive: direct A/B opening; negative: deleted/mismatched identity refused; race: rename/delete/restart versus launcher tap.
- **Acceptance:** Gate1 essential original+A/B isolation/recovery/shortcut criteria met; unresolved notifications/features remain open not relabeled supported.
- **Required evidence tiers:** ANDROID, PROVIDER, PHONE.

## APK-P3-T13 — Stable metadata and migrations

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L56`

> 13. Thiết kế dữ liệu Room theo ID bản clone ổn định: app nguồn, ID engine, tên, trạng thái và phiên bản thực. Không lưu mật khẩu. Thiết kế migration từ phiên bản đầu.

- **Inputs:** Frozen identity and configuration schema requirements; historical schema1–6 reference data only
- **Outputs:** Fresh Java/SQLite metadata schema and independently written migrations; no passwords/session fields
- **Interfaces:** Composite namespace+opaque instance ID; display metadata non-authoritative; transactional schema versioning
- **Dependencies:** T01/T02; host work independent of runtime; actual provider mapping waits T08
- **Errors, cancel and recovery:** Malformed/unknown ownership quarantined; no UUID remap/drop. Cancel migration rolls transaction back; failure preserves source data for review.
- **Positive / negative / race tests:** Positive: reference fixtures preserve metadata and IDs; negative: duplicate/foreign ID/malformed version rejected; race: migration interruption/concurrent read.
- **Acceptance:** HOST schema tests plus actual Android SQLite/file lifecycle before data layer accepted; Room requirement explicitly superseded by approved SQLite decision.
- **Required evidence tiers:** HOST, ANDROID.

## APK-P3-T14 — Create/list/rename/delete lifecycle manager

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L57`

> 14. Hoàn thiện tạo bản sạch, đọc danh sách từ engine, đổi tên và xóa có xác nhận. Xử lý tạo/xóa thất bại hoặc bị đóng tiến trình giữa chừng bằng đối chiếu dữ liệu, không để bản "ma" hoặc xóa nhầm.

- **Inputs:** T13 metadata; T08 exact provider contract and verified capabilities; fresh UI contract
- **Outputs:** Fresh flows for clean create, provider list/state, rename, confirmed delete with reconciliation
- **Interfaces:** Controller command carries exact identity/auth/operation generation; metadata state not provider READY authority
- **Dependencies:** T13; cloud models can proceed; guest actions disabled until T08 Runtime activation additionally requires the accepted current T26 access/auth grant at its own foreground/dispatch boundary; numeric roadmap order is not execution permission. Inert cloud models may proceed without completing all phase6.
- **Errors, cancel and recovery:** Create/delete uncertain result review-required; cancel consent means no provider dispatch; lock-time revalidation prevents wrong deletion.
- **Positive / negative / race tests:** Positive: clean create/readback and exact rename/delete; negative: no provider/failed auth/wrong ID; race: crash between provider and metadata commit.
- **Acceptance:** No ghost rows or wrong-region mutation; provider and Android flow evidence required beyond host models.
- **Required evidence tiers:** HOST, ANDROID, PROVIDER.

## APK-P3-T15 — Stable direct-launch shortcuts

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L58`

> 15. Hoàn thiện shortcut theo ID ổn định: mở đúng bản, xử lý bản bị xóa, tên mới, launcher không hỗ trợ và xác nhận ghim của Android. Lối tắt không mở nhầm app gốc khi clone lỗi.

- **Inputs:** T13 stable identity; T14 manager; Android shortcut/launcher contract
- **Outputs:** Pin/update/remove guidance and stale-target handling; rename retains identity
- **Interfaces:** Shortcut entry -> access gate -> exact provider resolution -> launch; no direct raw catalog-key execution
- **Dependencies:** T13/T14; launch capability requires T08 Runtime activation additionally requires the accepted current T26 access/auth grant at its own foreground/dispatch boundary; numeric roadmap order is not execution permission. Inert cloud models may proceed without completing all phase6.
- **Errors, cancel and recovery:** Unsupported launcher explains limitation; canceled confirmation never success. Deleted identity refuses; uncertain launch reconciles without replay.
- **Positive / negative / race tests:** Positive: correct direct instance after rename; negative: unsupported launcher/deleted clone/failed auth; race: delete or provider revision changes during tap.
- **Acceptance:** Actual launcher lifecycle opens intended identity; host tests alone cannot close.
- **Required evidence tiers:** HOST, ANDROID, PROVIDER.

## APK-P3-T16 — Reconcile restart, manager upgrade and original uninstall

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L59`

> 16. Đối chiếu trạng thái sau khi khởi động lại hoặc cập nhật chính Fonesi. Khi gỡ app gốc, giữ dữ liệu clone và báo khả năng mở thực tế của engine; không tự xóa tài khoản.

- **Inputs:** T13–15 metadata/protocol; provider private enumeration/readback
- **Outputs:** Startup/upgrade reconciliation and retained clone-data behavior after original removal
- **Interfaces:** Reconciliation uses namespace/revision/signed generation and row ownership; unknown status explicit
- **Dependencies:** T13–15; independent host reconciliation before runtime
- **Errors, cancel and recovery:** Original uninstalled is not delete instruction. Unknown provider retains metadata and review-required state; canceled refresh cannot overwrite newer generation.
- **Positive / negative / race tests:** Positive: reopen/upgrade state matches provider; negative: source absent retains clone data; race: provider restart/source uninstall during readback.
- **Acceptance:** No automatic loss/remap; actual restart/upgrade/original-uninstall evidence verifies behavior.
- **Required evidence tiers:** HOST, ANDROID, PROVIDER.

## APK-P4-T17 — Immutable signed base/split snapshot

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L67`

> 17. Chuẩn bị nguồn base/split APK nhất quán; đọc phiên bản/chữ ký; xác minh quyền đọc và tính toàn vẹn. Nếu app gốc thay đổi trong lúc chuẩn bị, bỏ nguồn tạm và lập lại. Không trộn split của hai phiên bản.

- **Inputs:** Authorized installed original package descriptor and complete signed generation; storage budget
- **Outputs:** Private immutable base+split source snapshot with signer lineage/version/dependency/required split checks
- **Interfaces:** Snapshot ID + package/version/signers/components/hashes; paths remain private; source permission not execution authority
- **Dependencies:** T01; contract/fixtures independent; actual platform source read requires permission
- **Errors, cancel and recovery:** Drift discards only temporary snapshot and re-prepares; invalid signature/mixed generation denied; cancellation releases temporary refs not retained users.
- **Positive / negative / race tests:** Positive: complete consistent signed set; negative: missing/tampered/mixed split and signer mismatch; race: original update during copy.
- **Acceptance:** Source provenance and integrity verified with independent fixtures and Android readback; never alter package/signature.
- **Required evidence tiers:** HOST, ANDROID.

## APK-P4-T18 — Durable deduplicated single-flight update queue

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L68`

> 18. Lưu hàng đợi và trạng thái bền vững. Gom tác vụ trùng, chỉ một tác vụ cập nhật chạy cùng lúc. Có kiểm tra lại nguồn, trạng thái đang dùng và phạm vi ảnh hưởng ngay trước khi thực thi.

- **Inputs:** T17 snapshot; T13 transactional store; shared-scope provider contract
- **Outputs:** Persistent states/dedupe/single-flight journal, pause/defer/retry controls, retained-source ownership
- **Interfaces:** Queue operations use request identity, expected generation, atomic ownership and compare-and-set transitions
- **Dependencies:** T13/T17; dispatch disabled until T08 accepted Runtime activation additionally requires the accepted current T26 access/auth grant at its own foreground/dispatch boundary; numeric roadmap order is not execution permission. Inert cloud models may proceed without completing all phase6.
- **Errors, cancel and recovery:** Unknown running peer or source blocks; cancellation before intent releases reservation; after dispatch reconcile rather than replay; corruption quarantines.
- **Positive / negative / race tests:** Positive: one operation across manual/auto paths; negative: duplicate/invalid transition rejected; race: crash at every durable boundary and competing enqueues.
- **Acceptance:** Independent host fault tests + Android durability; actual update not accepted until T20.
- **Required evidence tiers:** HOST, ANDROID.

## APK-P4-T19 — Actual scoped update controls

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L69`

> 19. Nối các nút Cập nhật, cập nhật bản đã chọn và Cập nhật tất cả vào thực thi thật. Nếu engine dùng chung mã APK, giao diện phải báo cập nhật ảnh hưởng cả nhóm; không giả lập cập nhật riêng. Hoãn bản đang dùng, cho tạm dừng ở ranh giới an toàn và chỉ báo thành công sau khi đọc lại phiên bản.

- **Inputs:** T18 queue; T17 source; provider complete affected-peer and running-state readbacks; user scope consent
- **Outputs:** Single/selected/all update flows with accurate group impact and safe-boundary pause
- **Interfaces:** Preview scope/version/peer set -> consent -> lock-time revalidation -> dispatch -> private version readback
- **Dependencies:** T17/T18; actual mutation requires T08 eligible update capability Runtime activation additionally requires the accepted current T26 access/auth grant at its own foreground/dispatch boundary; numeric roadmap order is not execution permission. Inert cloud models may proceed without completing all phase6.
- **Errors, cancel and recovery:** Scope drift invalidates consent. Running/unknown peers defer; cancel pre-dispatch zero mutation; late cancel settles/reconciles current task then pauses.
- **Positive / negative / race tests:** Positive: real scoped update and version verification; negative: missing peers/consent/current auth denies; race: peer starts or source changes at lock boundary.
- **Acceptance:** Success only after actual version/readback and complete affected peers; shared code never represented as per-instance independent version.
- **Required evidence tiers:** HOST, ANDROID, PROVIDER.

## APK-P4-T20 — Phone gate 2 update and crash recovery

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L70`

> 20. Thử cập nhật thật từ app gốc, hết dung lượng, nguồn bị gỡ, khác chữ ký, tác vụ trùng và tiến trình bị đóng ở các điểm quan trọng. Đối chiếu trạng thái engine trước khi tiếp tục, không chạy lại mù quáng. Kiểm tra dữ liệu và phiên đăng nhập sau cập nhật trên điện thoại.

- **Inputs:** T17–19; owner-authorized phone/app update and data-preservation checklist
- **Outputs:** Real update/low-space/source-removal/signer/duplicate/crash receipts; retained data and session outcomes
- **Interfaces:** Phone update report binds before/after signed generation and affected peer IDs; sessions never exported
- **Dependencies:** T19 verified provider; device and account operations owner-controlled
- **Errors, cancel and recovery:** Loss/mixing stops gate. Unknown dispatch not retried blindly; no downgrade-as-rollback. Canceled test preserves evidence and data.
- **Positive / negative / race tests:** Positive: update preserves tested data; negative: disk full/wrong signer/source gone; race: process death around install/readback and duplicate enqueue.
- **Acceptance:** No duplicate execution or data loss in tested cases; actual session retention or app-requested re-login honestly recorded; gate2 passed only with phone evidence.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P5-T21 — Zalo and TikTok two-region compatibility

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L80`

> 21. Tích hợp/kiểm thử Zalo và TikTok trước; tạo ít nhất hai vùng tài khoản mỗi app. Ghi riêng mọi lỗi đăng nhập, thông báo và chức năng thiết yếu.

- **Inputs:** Eligible provider; exact app versions and owner-controlled accounts
- **Outputs:** Separate Zalo and TikTok A/B login/notification/core-feature records
- **Interfaces:** App capability matrix keys app/version/API/model/ABI/provider revision
- **Dependencies:** T08 and safety gate; phone owner participation
- **Errors, cancel and recovery:** Untested app remains untested. No bypass/signature changes/account automation. Cancel never deletes unrelated instances.
- **Positive / negative / race tests:** Positive: independent A/B per app; negative: failure not generalized from Facebook; race: reopen/update and concurrent app activity.
- **Acceptance:** Both apps meet required criteria or remain explicit incomplete requirements.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P5-T22 — Messenger, Threads, X, Shopee and YouTube compatibility

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L81`

> 22. Kiểm thử Messenger, Threads, X, Shopee và YouTube. Không suy ra Messenger hoạt động từ Facebook hoặc suy ra Google sign-in hoạt động từ việc mở được màn hình YouTube.

- **Inputs:** Eligible provider and each exact app/version; owner test accounts
- **Outputs:** Five independent compatibility receipts including required YouTube sign-in
- **Interfaces:** Same versioned app matrix; opening UI distinct from login and essential features
- **Dependencies:** T08 safety; phone owner participation
- **Errors, cancel and recovery:** No inference Messenger from Facebook or Google sign-in from screen. Failures remain open; cancel retains owner data.
- **Positive / negative / race tests:** Positive: separate login/notification/core cases; negative: unsupported sign-in reported; race: process restart/update each app.
- **Acceptance:** Every required app independently tested; any missing one prevents full eight-app claim.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P5-T23 — Evidence-based compatibility matrix

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L82`

> 23. Lập ma trận ứng dụng × phiên bản ứng dụng × Android × model/ABI. Trạng thái gồm đã kiểm thử, có giới hạn, chưa kiểm thử, không hỗ trợ; không có nhãn "không bị ban".

- **Inputs:** T09–12/T20–22 results and unknown configurations
- **Outputs:** Matrix tested/limited/untested/unsupported, with exact limitations and retest triggers
- **Interfaces:** Matrix keys app+version+Android+model+ABI+provider+artifact; no package-name-only heuristic
- **Dependencies:** Can scaffold before runtime; statuses only from actual receipts
- **Errors, cancel and recovery:** Stale revision invalidates evidence; missing case becomes untested not unsupported. Cancel edit keeps prior valid registry.
- **Positive / negative / race tests:** Positive: receipt resolves every tested claim; negative: no anti-ban/universal support labels; race: app/provider upgrade marks stale.
- **Acceptance:** Public scope accurately reflects evidence; narrowing required release apps needs user decision.
- **Required evidence tiers:** HOST, PROVIDER, PHONE.

## APK-P5-T24 — Phone gate 3 mixed-app behavior

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L83`

> 24. Trên điện thoại, thử nhiều loại app xen kẽ, mở đúng tài khoản, nhận thông báo, cập nhật nhóm và xóa một bản trong khi giữ các bản khác. Sửa lỗi rồi thử lại phần bị ảnh hưởng.

- **Inputs:** Eight-app matrix and allowed phone setup; update/delete capabilities
- **Outputs:** Mixed-app alternation, correct account, notifications, group update and one-region deletion receipts
- **Interfaces:** Cross-app scenario references precise identities and outcome boundaries
- **Dependencies:** T20–23; runtime dependencies accepted
- **Errors, cancel and recovery:** Any cross-account leakage/loss stops gate; cancel stops future actions; uncertain effects reconciled and retained.
- **Positive / negative / race tests:** Positive: alternate apps and accounts correctly; negative: unrelated clone stays intact on delete; race: group update while other app active.
- **Acceptance:** Required release apps meet criteria; unresolved required apps remain incomplete and cannot silently leave scope.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P6-T25 — Discovery, search, groups and fixed styling

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L91`

> 25. Hoàn thiện quét, tìm kiếm, phân nhóm, tên/màu/huy hiệu và thao tác nhiều bản. Mọi cảnh báo tương thích lấy từ kết quả đã ghi, không chỉ từ tên gói.

- **Inputs:** Package visibility contract; T13 metadata; STYLE-R1-01; reference XML and original green branding
- **Outputs:** Fresh Java/View discovery/search/group/name/style/batch UX; six accents teal/blue/purple/amber/red/slate; bounded plain badge
- **Interfaces:** Scanner returns launchable visible apps; controller generation guards; style data no URLs/CSS/I/O; warnings from T23 matrix
- **Dependencies:** T13; runtime guest commands depend T08; UI may develop independently
- **Errors, cancel and recovery:** Stale callbacks cannot overwrite newer search; cancellation clears selection safely; hidden app not falsely eligible; yellow chat marker does not change brand.
- **Positive / negative / race tests:** Positive: correct filter/group/style/batch scope; negative: unknown accent/oversize badge/unsafe fields denied; race: package removal/rotation/repeated tap/back/close.
- **Acceptance:** Host contracts plus actual Android View/scan/lifecycle checks; visual reference inspected/rendered before parity claim.
- **Required evidence tiers:** HOST, ANDROID.

## APK-P6-T26 — Manager and shortcut access lock

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L92`

> 26. Thêm khóa giao diện bằng sinh trắc học với đường xử lý phù hợp khi máy không hỗ trợ hoặc thay đổi đăng ký. Giải thích rõ gỡ Fonesi có thể làm mất bản clone; không gọi khóa giao diện là mã hóa toàn bộ dữ liệu nếu engine không cung cấp.

- **Inputs:** Android authentication capabilities and lifecycle; T15 shortcut routing
- **Outputs:** Biometric strong/device credential access gate with unsupported/enrollment-change handling and accurate warnings
- **Interfaces:** Auth grant scoped to current foreground session/operation; host lock not encryption claim
- **Dependencies:** T13/T15; permission/account changes require owner participation
- **Errors, cancel and recovery:** Failure/cancel/background/changed enrollment invalidates grants; no fallback bypass. Explain uninstall risk without deleting data.
- **Positive / negative / race tests:** Positive: supported auth unlocks bounded operation; negative: unsupported/canceled/failed auth denies; race: background/recreation/shortcut during enrollment change.
- **Acceptance:** Actual Android auth/lifecycle verified; no cached token permits stale operation or claim of whole-data encryption.
- **Required evidence tiers:** HOST, ANDROID.

## APK-P6-T27 — Permissions, notifications, storage and battery guidance

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L93`

> 27. Hoàn thiện màn hình quyền/thông báo, dung lượng, hạn chế pin và lỗi. Nếu quyền hệ điều hành được cấp cho host và engine không tách được theo từng bản, phải báo đúng giới hạn đó.

- **Inputs:** Actual host/provider permission model and measured limits; T23 compatibility
- **Outputs:** Honest status/help/error screens and permission request flows where authorized
- **Interfaces:** Capability/permission readback reports host-wide vs per-instance scope; no unsupported permission granularity
- **Dependencies:** T08 provider model; T25 UI; device-sensitive operations owner-controlled
- **Errors, cancel and recovery:** Denied/revoked permission displays actionable limit; cancel requests no extra access; stale readback invalidated.
- **Positive / negative / race tests:** Positive: accurate available actions/limits; negative: unknown or host-wide access not shown isolated; race: permission revocation/background limitation while screen open.
- **Acceptance:** Android/phone behavior matches disclosures; no broad permission added merely to accommodate engine.
- **Required evidence tiers:** HOST, ANDROID, PHONE.

## APK-P6-T28 — Configuration-only backup/restore and safe diagnostics

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L94`

> 28. Sao lưu/khôi phục cấu hình, tên và nhóm; dữ liệu xuất không chứa mật khẩu/token. Thử dữ liệu nhập sai/hỏng và lọc thông tin nhạy cảm trong chẩn đoán. Sao lưu phiên đăng nhập/chuyển máy không nằm trong cam kết bản đầu.

- **Inputs:** Approved config/name/group/style schema; Android SAF contract; T13 transactions
- **Outputs:** Versioned configuration export/import preview, explicit mapping transaction, redacted diagnostics
- **Interfaces:** Config format excludes session/password/token/private guest files; IDs not portable execution authority; bounded parsing
- **Dependencies:** T13/T25/T26; historical schemas reference only
- **Errors, cancel and recovery:** Malformed/oversize/duplicate/foreign identity rejected or quarantined; cancel preview zero writes; interruption rolls back and preserves original config.
- **Positive / negative / race tests:** Positive: allowed config round-trip; negative: secrets/URLs/unknown fields/corruption; race: stale preview concurrent edit and import crash.
- **Acceptance:** Independent host parser/transaction tests and Android SAF validation; no session backup/move-device promise.
- **Required evidence tiers:** HOST, ANDROID.

## APK-P7-T29 — Measured capacity and simultaneous running load

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L102`

> 29. Đo tài nguyên trên máy thật: một bản, ba bản, năm bản và tăng tiếp nếu máy cho phép; đo riêng số bản lưu được với số bản chạy đồng thời. Đây là các mức thử, không phải giới hạn sản phẩm. Chọn cảnh báo từ số đo thay vì cam kết chạy vô hạn.

- **Inputs:** Authorized device configurations and eligible provider; reproducible scenarios
- **Outputs:** Separate stored-instance and concurrent-running resource measurements at 1/3/5 then safe increases
- **Interfaces:** Performance receipt contains RAM/storage/latency/thermal context and duration; warning thresholds derived from measurements
- **Dependencies:** T08 and safe runtime; owner phone participation
- **Errors, cancel and recovery:** Resource pressure stops increase without data drop; no arbitrary product cap or infinite promise. Cancel stops test workload safely.
- **Positive / negative / race tests:** Positive: repeatable measured samples; negative: render page budget not clone limit; race: low storage/background reclaim under load.
- **Acceptance:** Phone metrics support bounded warnings only; unknown configurations remain untested.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P7-T30 — Two-hour stability and background behavior

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L103`

> 30. Thử sử dụng/hạn chế nền tối thiểu hai giờ với nhóm ứng dụng ưu tiên trên thiết bị đích; thử khởi động lại, tắt màn hình và mạng thay đổi. Kiểm tra qua đêm là kiểm tra bổ sung. Chỉ kết luận trong phạm vi thời gian và cấu hình đã thử.

- **Inputs:** T29 configuration; prioritized app set; consented phone/network plan
- **Outputs:** At least two-hour continuous stability receipt plus restart/screen-off/network-change results; optional overnight separate
- **Interfaces:** Time-bounded log includes start/end/gaps/config and redacted error counts
- **Dependencies:** Runtime safety accepted; uninterrupted actual observation window
- **Errors, cancel and recovery:** Interruption invalidates duration claim or restarts measured window; cancel records elapsed incomplete test; no invented pass during unobserved sleep.
- **Positive / negative / race tests:** Positive: two-hour measured scenario; negative: shorter/unobserved interval cannot pass; race: reboot/background kill/network handoff.
- **Acceptance:** Only observed duration/config claimed; unresolved loss/mixing/crash defects block release as applicable.
- **Required evidence tiers:** PROVIDER, PHONE.

## APK-P7-T31 — Samsung plus non-Samsung/API/ABI/page-size matrix

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L104`

> 31. Kiểm thử ít nhất một thiết bị Samsung và một hãng khác trước khi tuyên bố đã xác minh nhiều hãng. Xác minh tương thích API/ABI và thư viện native/page size khi liên quan; không nói hỗ trợ mọi máy từ kết quả của một máy.

- **Inputs:** At least two authorized physical devices, actual API/ABI/page-size and native artifacts
- **Outputs:** Cross-vendor compatibility receipts and exact support matrix
- **Interfaces:** Device matrix binds hardware/OS/ABI/native dependency hash/page size/provider revision
- **Dependencies:** T08 and synthetic safety per configuration; owner device authority
- **Errors, cancel and recovery:** Missing device blocks multi-vendor claim, not independent work. Native alignment mismatch marks unsupported/blocked, never SDK downgrade workaround.
- **Positive / negative / race tests:** Positive: Samsung and another vendor checked; negative: one model cannot stand for all; race: OS/provider update invalidates baseline.
- **Acceptance:** Actual multi-vendor tests before multi-vendor verified claim; relevant native/page-size constraints proven.
- **Required evidence tiers:** ANDROID, PROVIDER, PHONE.

## APK-P7-T32 — Final regression, owned signing and phone gate 4

Status: **NOT_STARTED**; accepted evidence: none.

Original: `2fac016e3b28e4d9f21390c221cad1df7a382c86:docs/ROADMAP.md:L105`

> 32. Chạy kiểm tra hồi quy cuối, build APK bằng khóa phát hành được bảo quản đúng cách, xác minh chữ ký/cài nâng cấp giữ dữ liệu và tạo ghi chú phát hành. Cung cấp APK, checksum, hướng dẫn và danh sách giới hạn. Thực hiện **mốc điện thoại 4** để nghiệm thu.

- **Inputs:** All required accepted task receipts; no known data-loss/mixing defects; user-controlled signing and release authority
- **Outputs:** Regression report, signed trial APK/checksum, upgrade/data-preservation receipt, limitations, install guide and gate4 checklist
- **Interfaces:** Release manifest binds source SHA/artifact/signature/version/required evidence; private keys never Git/chat/archive
- **Dependencies:** T01–31 required acceptance; explicit signing/distribution authority, not automatic store publish
- **Errors, cancel and recovery:** Missing required test or key authority blocks release. Cancel leaves unsigned/debug artifact separate; upgrade failure preserves data and does not downgrade blindly.
- **Positive / negative / race tests:** Positive: exact final regression/signature/install-upgrade retains data; negative: mismatched signature/stale evidence/unapproved publish denied; race: last edit invalidates affected checks.
- **Acceptance:** All mandatory scoped requirements met; authorized signed upgrade/phone acceptance, accurate limits; no store/service publication without separate request.
- **Required evidence tiers:** HOST, COMPILE, ANDROID, PROVIDER, PHONE.

