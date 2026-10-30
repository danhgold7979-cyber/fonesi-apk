# Fonesi APK rebuild — trạng thái 30/10/2026

**Đã hoàn thành recovery/reconcile; đang xây foundation mới độc lập, chưa thay sản phẩm Android hoặc xóa source cũ.** Đỏ là marker chat, không đổi thương hiệu app. Repo APK riêng, không thay Auto/Edit.

## Baseline đúng

- Checkout rebuild bắt đầu từ main `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`; không gọi đây là trạng thái mới nhất.
- Latest reviewed development/report head: `phase/e82-public-component-factory-feasibility-20261030@2fac016e3b28e4d9f21390c221cad1df7a382c86`.
- Latest recorded exact source/test PASS: `58d52f4aa05c8c8d5256e7125b522815418b1e02` (E83). CI receipts chưa được audit độc lập trong lượt này.
- 14 remote heads không là tổ tiên latest head đã được preserve và semantic reconcile riêng; xem REBUILD_RECONCILIATION.md.
- Full bundle `/tmp/fonesi-apk-pre-rebuild-20261030.bundle`, SHA-256 `d0516e03126dd60b20e5dc954065dd694469d0d6b3689691cbc7e4717866404a`, 65 advertised refs. All-ref restoration VERIFIED: wildcard fetch vào bare repo mới khớp chính xác 64 refs; HEAD riêng khớp main. Bản workspace tại `/workspace/shared/fonesi-apk-recovery-20261030/` có bundle, restore.git và ref-manifest.json.

## Đã làm trong lượt này

Đọc main và latest branch, quan hệ Git mọi remote head, E35 threat model, E82/E83 results, E84 feasibility, E85 pre-code plan, schema/worker source và những mâu thuẫn trạng thái. Tạo [REBUILD_REVIEW.md](REBUILD_REVIEW.md). Tại checkpoint audit ban đầu chưa chạy APK/binary/scripts hoặc sửa/xóa code. Các lần compile/test foundation sau đó được ghi rõ phía dưới.

## Kết luận hiện hành

Có Android synthetic host-stub/Application/data/restart proof và nhiều contract/storage tests; không được nói toàn repo chưa chạy Android. Chưa có production split provider, third-party/social-account acceptance hoặc physical phone acceptance. SpaceCore vẫn single APK. E85 orchestration là kế hoạch, không phải đã implement. Room latest schema 6; background journal-only worker và clone color/badge đã tồn tại.

## Đang chờ trước khi thay source

1. ĐÃ ĐẠT: so khớp all-ref bundle restore và lưu bản workspace ngoài /tmp; không coi workspace là bảo đảm lưu vĩnh viễn.
2. ĐÃ ĐẠT: semantic review 14 nhánh rẽ; giữ regression oracle riêng.
3. ĐÃ CÓ: master/detailed rebuild plan có acceptance matrix; không dùng main-only gaps làm requirements.
4. Sau đó mới tiến hành rebuild có regression oracle và fresh verification. Không tự tăng phần trăm từ lịch sử test.

## Gate còn mở của sản phẩm

Provider provenance/license, full signed split import/readback/atomic update, guest framework/Binder/permissions and adversarial isolation, phone/app compatibility, release signing/upgrade/data retention. Tiếp tục độc lập trong cloud; tài khoản thử do người dùng tự đăng nhập khi đến gate, không xin bí mật qua chat.

## R2 foundation progress

Recovery and semantic reconciliation complete; see REBUILD_RECONCILIATION.md. Plan checkpoints committed locally (`a066c17`, `5166d71`), no push. New independent `rebuild-kernel` compiled with installed Java 21 compiler module and passed **48 explicit checks**, zero failed checks. Local JRE image lacks ct.sym, so compile uses Java21 system modules with source/target21; no SDK build claim. No production provider or source deletion. Đây là trạng thái trung gian; kết quả review hoàn tất và số test hiện hành ở R2.1/R2.2 phía dưới.

R2.1 independent review found and corrected missing lifecycle dependency, shared-peer readback and operation/attempt-binding gaps. Final local run: **64 explicit JVM checks PASS**, compiler `-Xlint:all -Werror`; policy now binds exact cluster/provider revision/environment, globally rejects unknown isolation claims, and validates every peer observation. This supersedes the interim 48-check count. No Android/provider/product completion claim.

## R2.2 passing checkpoint — 2026-10-30

Fresh rerun after resuming: `./rebuild-kernel/check.sh` exited 0, **64 kernel checks + 60 Linux filesystem journal checks**, no failures; compiler `-Xlint:all -Werror`. `git diff --check` passed. Journal tests include real second-JVM lock contention/forced child termination, atomic CAS/reopen, correctly checksummed malformed payloads, symlink/FIFO/directory refusal, pinned-root replacement, and injected pre-/post-rename failures. Pre-rename retains old state; post-rename remains uncertain and is durably quarantined, never permission to dispatch.

Independent review findings were fixed or explicitly retained as integration blockers: journal is storage-only, not an execution lease or readback authenticator. A future trusted dispatcher must reserve overlapping clone/package operations throughout mutation/readback, verify live authorization/READY/stopped peers, and own validated transitions. Public record construction is not proof. Trusted private-directory ownership must prevent lock/root replacement; file identity checks are not a formal hostile-filesystem guarantee.

No Android SDK/build, emulator, actual guest/provider, migration deployment or physical-device tests were run for this new module. No production capability was enabled and no old source was deleted. All-ref recovery and 14-head semantic reconciliation are complete; replacement still requires preservation of the documented oracle. Provider/license/native/hostile-guest isolation and phone acceptance remain unresolved. Gradle definition exists but Gradle execution was not performed; tests used installed Java21 compiler/JVM directly. No push by this worker; root coordinates publication.

## R2.3 passing local checkpoint — 2026-10-30

Fresh suite: **64 kernel + 61 Linux journal + 83 trusted-callback dispatcher checks PASS**; warning-free Java21 compilation, `git diff --check` passed. Dispatcher holds provider-namespace/package lease through mutation/readback; prior unresolved work blocks that package across provider revisions but unrelated packages proceed. Global UUID journal namespace prevents request rebinding/replay; Identity includes package/environment/evidence scope. Complete package enumeration, consistent shared generation and triple authority/source/peer checks are required. Real second-JVM contention/crash and injected create/dispatch/verification/review publication failures were exercised with synthetic callbacks only.

Independent review corrections included durable scope binding, complete peer enumeration, generation consistency and streaming history inspection. Journal v2 refuses unbound v1 records, with no automatic migration/deletion; legacy app Room data is untouched. Full-lifetime leases must be shared by every future conflicting adapter; callbacks must be independently verified. This is not formal hostile-guest isolation or actual engine proof.

New `.github/workflows/rebuild-kernel.yml` defines exact-source Linux Java21 tests with read-only token permissions and pinned checkout action. **Remote CI NOT RUN for this new workflow yet**; root publishes and observes results. Windows filesystem semantics are not claimed. README now labels old main app/docs as historical reference, not rebuilt product.

Source removal assessment: all-ref recovery and semantic review are ready, but R2 is not an Android manager/runtime replacement. No source deletion in this checkpoint; retain legal notices, wrapper, requirements and oracle until tested replacement/scaffold and migration boundaries justify a separate removal commit. No push by worker.

## R3.1 local storage checkpoint — 2026-10-30

**51 synthetic SQLite unittest cases PASS**, zero failures/skips, on Python3.12.14/SQLite3.53.1. Historical schema1–6 JSON is pinned to E82 SHA with per-file checksums and used only as fixture/schema oracle. New storage reference code reads source mode=ro/query_only, validates supported schema structure, retains every typed row, and transactionally creates a separate quarantined catalog. Unknown provider/state/owner remains inert; no dispatch/release/launch is enabled. Source fixture bytes remain unchanged except a test explicitly simulating an independent later writer.

Independent review fixes: TEMP/attached-target shadowing, generated/hidden fields, required-index predicates/order/collation, defaults/FKs/table options, cell/snapshot budgets, explicit SQL transactions under autocommit, unsafe journal/CHECK settings, and uncertain commit/rollback errors preserving original failure. Tests include all owner kinds/orphans, foreign identities, locks, precommit rollback, postcommit uncertainty and exact preserved-row readback after reopen.

Read-only tool inventory found no Android SDK/adb/sdkmanager/Gradle executable or configured SDK root. Official Android documentation audit is [REBUILD_ANDROID_COMPATIBILITY.md](REBUILD_ANDROID_COMPATIBILITY.md): Java21 host compilation is not Android API/desugaring compatibility. No SDK installation/license acceptance happened. **Android adapter, real migration, actual guest/provider, physical-device acceptance and source replacement remain pending.** Old source retained because tested JVM/SQLite contracts are not yet an Android replacement; full replacement is still tracked, not declared complete.

Final local aggregate must include unchanged **64+61+83 JVM/filesystem/dispatcher checks** plus the 51 separately counted unittest cases. The standalone workflow now defines an additional synthetic-SQLite job; creation is not remote CI PASS. Root coordinates publication/monitoring; worker does not push.

## Hosted Android feasibility — inventory pending

Official runner inventory suggests a viable preinstalled API36/build-tools36/JDK17 route, but actual own-job SDK inventory is not yet observed. Verified local wrapper JAR and configured Gradle8.13 ZIP checksum against Gradle's official release-checksum page; wrapper shell inspected, no execution. Candidate experiment plan is [REBUILD_HOSTED_ANDROID_PLAN.md](REBUILD_HOSTED_ANDROID_PLAN.md).

New read-only diagnostic job/script lists exact installed SDK properties/tool presence and JDK17 release, never invokes sdkmanager/Gradle or reads/writes licenses. Six local diagnostic fixture tests pass. Local real inventory correctly reports NOT_READY (SDK/JDK17 absent) with wrapper verification true. Root must publish diagnostic and inspect actual job output before approving bounded scaffold compile. No Android implementation or compile is claimed by this checkpoint.

## Fresh Android compile experiment prepared — 2026-10-30 18:19 UTC

Root conditionally authorized hosted compilation after exact same-job installed SDK inventory. Fresh `rebuild-android/` is independent Java17 native presentation, distinct `vn.fonesi.rebuild.debug`, no requested permissions, backup disabled, four unsupported actions disabled. No historical runtime copied or Java21 kernel/Python harness imported. Root verified wrapper reused with isolated `-p` project; both SDK and Java-toolchain auto-download disabled. Official pinned Gradle/AGP dependency resolution only after inventory passes. Missing SDK/JDK fails BLOCKED with no installer/license fallback.

Local: 208 JVM/journal/dispatcher checks + 51 synthetic SQLite cases still pass. 24 Python tools tests pass (7 inventory + 6 source contracts + 11 synthetic APK-evidence gate cases); py_compile and whitespace checks pass. These tests do NOT compile Android. Local SDK/JDK17 absent; hosted `assembleDebug lintDebug` and actual APK permission/identity/debug-signature/disabled-layout verification remain PENDING. No APK deployed or release published. Old-source replacement remains pending corresponding tested functionality, legal/history/oracles preserved.
