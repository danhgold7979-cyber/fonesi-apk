# Kế hoạch thực thi và đối chiếu yêu cầu

## E49 — pre-code integration of E46 foreground, E47 manual and E48 recovery source authority (29/10/2026)

Branch `phase/e49-source-authority-gates-integration-20261029`, stacked on E48 implementation `b02ef1a9b245c211f565cfb16918aa692043cd9a` and bounded E45 verified baseline. Original product Phase 4, steps 17–20. [E49 detailed pre-code plan](phases/E49_SOURCE_AUTHORITY_GATES_INTEGRATION_PLAN.md). Carry disjoint E46 foreground original-source guard, E47 manual update confirmation private-queue conflict guard and E48 recovery-time no-false-success guard into one source with all their tests. Do not overwrite independent branch histories or claim product engine readiness. Requires own E40–E49 source audits and pinned cloud/candidate build/lint/APK verification at exact E49 SHA. Keep Room v6, split import disabled, no garbage collection and original weighted product 28.00%.


## E48 — recovery-time retained source quarantine pre-code plan (29/10/2026)

Branch `phase/e48-retained-recovery-quarantine-20261029` starts from E45 report HEAD `3da43261747f28f49c86356cb79afb42699f6e3d`, source E45 [CI #40848683404](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40848683404) PASS, 390 cloud/candidate tests each. E46/E47 are distinct parallel technical branches; do not mix their results. [E48 detailed plan](phases/E48_RETAINED_RECOVERY_QUARANTINE_PLAN.md). Original product Phase 4 steps 18–20: guard `UpdateRunner.recoverLocked` with existing fail-closed retained-source authority before legacy engine version readback or snapshot deletion. Denied APPLYING/VERIFYING → REVIEW_REQUIRED with immutable marker; denied PREPARING/REVIEW_REQUIRED remain held; legitimate legacy recovery unchanged. Add negative JVM tests and pinned E48 cloud/candidate audits. No product % increase from storage/routing reliability: 28.00%, zero real clones.


## E45 — bounded technical integration PASS (29/10/2026)

Source `64e0a285da84360e3fdea57135367d5b9bd6f8b5` [GitHub Actions #40848683404](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40848683404) **SUCCESS**: 390 tests per cloud/candidate gate, build/lint/signed APK permission checks and E40–E45 audits. [Full E45 results](phases/E45_RETAINED_BATCH_SOURCE_QUARANTINE_RESULTS.md). E45 preserves E44 batch atomicity plus source-authority no-fallback guard; existing Room v6, single-APK candidate and no destructive GC remain. This does not prove private-source execution, full split provider or account isolation. Next: E46 recovery-time retained-source fail-closed proof; separate pre-code plan required. Product 28.00%, zero real clones.


## E45 — pre-code integration of verified batch and durable source quarantine (29/10/2026)

Branch `phase/e45-retained-batch-source-quarantine-20261029` starts at E44 verified report HEAD `cd2089568bbc20d886ff0f16ca218c0d872df7e6`; E44 separate Draft PR #18 remains independently tested and unmerged. Original product Phase 4, steps 17–20. [E45 detailed plan](phases/E45_RETAINED_BATCH_SOURCE_QUARANTINE_PLAN.md): import durable private-source marker and fail-closed UpdateRunner original-source policy from the separate branch without losing E44 atomic batch readback/queue retention, add cross-feature Room/native-SQLite tests, run all source audits and cloud/candidate signed APK gates before marking E45 PASS. Room v6, `supportsSplits=false`, unchanged product 28%/0 real apps.


## E44 — bounded cloud/candidate technical PASS (29/10/2026)

Exact tested source `9d7270b207a6dd82e50befa484850c5360bd740f`: [E44 CI #40845568917](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40845568917) **SUCCESS**, **382 tests per cloud/candidate gate**, build/lint/signature/permissions and E40–E44 audits passed. [Verified E44 results](phases/E44_ATOMIC_BATCH_RETAINED_ENQUEUE_RESULTS.md). The public opt-in batch API verifies all signed private clusters before one all-or-nothing Room task/owner transaction; it does not connect to UI/provider, change Room schema, enable split import or prove account isolation. Product estimate remains 28.00%; 0 accepted real app clones. A report-only follow-up HEAD must independently pass CI.


## E44 — pre-code atomic multi-target private-source retained queue (29/10/2026)

Branch `phase/e44-atomic-batch-retained-enqueue-20261029` stacks on E43 reporting HEAD `785cb956ce3f193c8e5f1b05dfc569470522b265`. Original Phase 4, roadmap steps 17–20. [E44 detailed pre-code plan](phases/E44_ATOMIC_BATCH_RETAINED_ENQUEUE_PLAN.md). Verify all batch cluster IDs and source identities via signed private-store readback before DB mutation, fail closed on duplicate dedup identities, then enqueue every task and acquire matching UPDATE_TASK owner in one Room transaction. Add negative native-SQLite rollback/idempotence/conflict tests and pinned cloud/candidate CI audits. Do not change normal UI/engine behavior, Room schema, split import or GC; retain product estimate 28.00% and real-app acceptance 0.


## E43 — verified atomic private-source enqueue increment (29/10/2026)

Exact tested SHA `0579d7ebee2ef829347d77d6b3169255f1d8e92c`, [CI #40843854486](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40843854486) **SUCCESS**: cloud and candidate **374 tests each**, 0 failures/errors/skips, build/lint/signature/permissions and single-APK/no-GC safety audits. [E43 detailed results](phases/E43_ATOMIC_RETAINED_ENQUEUE_RESULTS.md), [pre-code plan](phases/E43_ATOMIC_RETAINED_ENQUEUE_PLAN.md). Room v6 unchanged. Bounded storage-only technical PASS; Phase 4, production engine, real guest session and device acceptance still open.


## E43 — atomic verified private-cluster enqueue and UPDATE_TASK reference (pre-code, 29/10/2026)

Branch `phase/e43-atomic-retained-enqueue-20261029`, stacked on E42 `1f13bfa70e12631baf650f4478e57244c7de168b`. Phase 4 steps 17–20. [E43 detailed plan](phases/E43_ATOMIC_RETAINED_ENQUEUE_PLAN.md). Steps: (1) readback a previously published, signed private cluster; (2) match exact requested package/version/signers and use only verified private APK paths; (3) atomically insert/get QUEUED task and acquire matching UPDATE_TASK Room reference; (4) reject conflicting owner/generation or in-progress task and roll back if SQLite insert fails; (5) regression tests including reopen/idempotence/unpublished IDs; (6) full cloud/candidate source/APK/CI acceptance at exact SHA. No invocation of actual guest engine, no split import, account/session proof, cluster deletion or product progress increase.


## E42 — accepted technical journal/reference increment (29/10/2026)

Exact tested source `7f749ae4e54f01c23f11772430a992a050be36c0`, [CI #40841923812](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40841923812) **SUCCESS**: cloud/candidate 366 tests each, 0 failed/error/skipped; build/lint/signature/permission/no-split/no-GC audits passed. [E42 results](phases/E42_ATOMIC_JOURNAL_OWNER_TRANSITIONS_RESULTS.md), [pre-code plan](phases/E42_ATOMIC_JOURNAL_OWNER_TRANSITIONS_PLAN.md). This closes only the bounded E42 technical acceptance; Phase 4/guest engine/phone/account acceptance remain open. Read the subsequent report-only commit's exact-HEAD CI before treating it as tested.


## E42 — atomic journal/retained-cluster transitions (plan before code; 29/10/2026)

Branch `phase/e42-atomic-journal-owner-transitions-20261029` stacks on E41; Phase 4 roadmap steps 17–20. [Detailed E42 pre-code plan](phases/E42_ATOMIC_JOURNAL_OWNER_TRANSITIONS_PLAN.md). Scope: make successful Room update-task state CAS transfer/release an existing E41 UPDATE_TASK source reference in the **same transaction** when entering REVIEW_REQUIRED/terminal states, without changing existing unsnapshotted tasks or invoking the provider. Existing E41 explicit-method test paths remain independently verified. Test stale CAS, SQLite aborts, no-ref legacy, conflicting generations, other-owner retention and DB reopen. Full pinned cloud/candidate CI and no-split/no-GC audit required on exact HEAD before PASS. Product completion remains 28.00%.


## E41 — verified technical result (2026-10-29)

Exact code SHA `d8c0a44584f1821e5ce91fa7a6a9f8875c0783ff`: [E41 cloud/candidate Actions #40840049529](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40840049529) **SUCCESS**, 358 tests per gate per CI logs. [Detailed E41 results](phases/E41_UPDATE_TASK_CLUSTER_OWNERSHIP_RESULTS.md). The Room owner API is explicit and not wired into production UpdateRunner; no claim of real app updates/account isolation. Product Phase 4 remains incomplete. Subsequent work needs its own pre-code plan.


## E41 — update-task/private-cluster ownership retention (pre-code plan, 29/10/2026)

Branch `phase/e41-update-task-cluster-ownership-20261029` starts at E40 verified reporting SHA `dc5aea86a01aee0594ad70cbae6367f6332e2596`; [E40 cloud/candidate CI #40838110200](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40838110200) passed on that exact SHA. Align Phase 4 steps 17–20 without changing the original eight-Phase roadmap. Review [E41 detailed plan](phases/E41_UPDATE_TASK_CLUSTER_OWNERSHIP_PLAN.md) before each code change.

Sequence: (1) validate existing task+state+immutable signing/package/version and readback-verified private cluster; (2) acquire exactly one owner-scoped UPDATE_TASK ref transactionally/idempotently; (3) promote an unknown-outcome journal to durable REVIEW_REQUIRED hold before releasing UPDATE_TASK; (4) release only UPDATE_TASK refs after terminal journal state, retaining unrelated CLONE/REVIEW_REQUIRED/ROLLBACK refs; (5) test rollback/reopen/ambiguous-owner/invalid readback; (6) full cloud/candidate CI on same SHA; (7) document exact evidence. No automatic linking to engine updater, APK publication, deletion/GC, split import, UID/account claims, or product percentage increase in E41.


## E40 — integrate Room clone metadata with private APK cluster ownership (plan before code)

**Accepted technical source:** E40 code+schema `a3cf9ec199de7d1c5ec66e105d525a1175554e1c` passed [same-source cloud/candidate CI #40837064438](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40837064438), 349 tests per gate, zero failures/errors/skips. See [E40 results](phases/E40_CLONE_CLUSTER_OWNERSHIP_RESULTS.md) and [product progress rubric](PRODUCT_PROGRESS.md). Storage-only technical PASS, not Phase 3 production acceptance. The report-only commit's new HEAD requires its own CI verification.

Repository: `danhgold7979-cyber/fonesi-apk`; dedicated branch `phase/e40-clone-cluster-ownership-20261029`; parent branch `phase/e21-android-split-code-proof-20261028`; starting parent HEAD `1da1a1ea42c5f44c95bf9a3b0778d5bba0a23162`.
Product alignment: Phase 3, roadmap steps 13–16 (stable clone identity/lifecycle), with Phase 4 storage-retention dependency. E39 is accepted only for its own Room v5 ownership registry: workflow [#40803931491](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40803931491) passed cloud and candidate gates on source `07d582e09bce8e1d21249c79333767ba8efdb4f0`, 334 tests in each gate. This does not make E40 pass.

### Pre-code audit and implementation sequence

1. Keep the existing Room ownership registry as the sole owner authority; do not use the file ledger to authorize deletion. Preserve SpaceCore's single-APK and disabled-split guards.
2. Extend `CloneMetadataRow` with nullable `clusterId`; migrate Room schema 5→6 with a non-destructive nullable-column migration. Existing rows must migrate to null, with clone IDs, launch tokens, tasks, queue pause state, engine instances and E39 owner rows preserved.
3. Add a dedicated clone/cluster ownership repository that accepts only a canonical UUID and a cluster returned by `PrivateApkStore.open` readback. Validate clone/engine identity and package match. In one Room transaction, assert the old metadata/reference relationship is consistent, acquire the new CLONE reference, update `clusterId`, and only then release the old reference. Reject ambiguous or inconsistent owner references without mutation.
4. Integrate deletion with the existing clone metadata lifecycle: under the existing operation lock, validate the expected launch token, confirm provider deletion/absence first, then atomically delete clone metadata and release exactly its matching CLONE reference. If metadata and references disagree, preserve metadata and fail closed. Never delete cluster files or other owner types.
5. Add tests for 5→6 migration and null legacy values; valid association; idempotent re-association; generation switch; wrong package/engine/clone; malformed/missing cluster; inconsistent/multiple CLONE references; stale delete token; provider deletion failure; atomic rollback; database reopen and unrelated UPDATE_TASK/REVIEW_REQUIRED/ROLLBACK references retained.
6. Update schema/audit/workflow coverage and execute the exact cloud plus opt-in candidate gates. Review generated schema v6 and the candidate's install/split guard evidence. Do not mark E40 PASS until both gates succeed on the same source SHA and migration/schema evidence is reviewed.

### Acceptance boundary

- A non-null `clone_metadata.clusterId` has exactly one matching `CLONE` reference for the same `engineKey/cloneId`; null means no CLONE reference.
- Association, generation switch, and post-provider-confirmed deletion keep metadata/reference state atomic and fail closed on inconsistencies.
- Existing data survives migration and process/database reopen.
- No automatic cluster publication, cluster deletion/GC, original-app fallback, split import, guest launch, account isolation, or product-phase completion is claimed.
- Product readiness percentage remains unchanged for this storage-only increment unless accepted product functionality changes under `docs/PRODUCT_PROGRESS.md`.


## E9 — plan nạp code/resources private, trước code

Đối chiếu tổng thể: E7/E8 đã push d5a6ff7, 301 test và các artifact có báo cáo; E6 chưa chạy Android. Vẫn thiếu classloader/resources/lifecycle/Binder trước khi có provider hoàn chỉnh. Bước này chỉ làm code image loader, không gọi Application.onCreate/Activity hoặc đưa SDK SpaceCore thành hỗ trợ split.

1. Thêm cờ isolatedSplits từ manifest đã ký vào verifier chung. Giữ chữ ký/root/dependency checks; test bằng APK binary thật. Loader phẳng phải từ chối isolatedSplits cho tới khi có graph classloader/resources đúng, không âm thầm flatten.
2. Layout lấy cluster bằng PrivateApkStore.open, xác minh mọi APK nằm trong private data root host, từ chối package host/framework và bảo toàn toàn base/split. Sắp splitNames/sourceDirs cùng thứ tự tên như Android; không lấy ApplicationInfo/codePath từ app gốc đang cài.
3. Android loader chỉ ở dedicated guest process, sau native ENABLED và trên thread IO. Chỉ một lần thử trong process; lỗi không cho hot-swap/retry account. Dùng DexClassLoader với parent framework của host (không parent chứa các thư viện app host), native search path chỉ từ preparer, cache private. Không load/init class guest trước readback.
4. Resources được đọc bằng PackageManager.getResourcesForApplication(ApplicationInfo) trỏ hoàn toàn vào code private và UID host, không createPackageContext/fallback app gốc. Base ApplicationInfo chỉ phục vụ resource paths; chưa là PackageInfo/components đầy đủ của feature. Public API chưa gắn custom resource classloader/Context; không nhận là full resource/lifecycle support.
5. Đọc lại cluster/native image sau tạo code/resources; lỗi chấm dứt attempt và để controller sau xử lý process thất bại. Loader không tự bật provider hoặc xử lý intent, không khởi guest Application/Activity. Chưa có context/Binder mediation nên chưa cho tài khoản vào.
6. Kiểm tra layout bằng signed fixtures thật cho đủ/thứ tự/private paths/isolation/host collision/corrupt input, không mock để báo ART chạy. Compile/lint/audit source component, app hai cấu hình và bundle vì verifier public model đổi. E6 không boot lại; DEX/Resources thực thi Android vẫn NOT VERIFIED khi chưa có kết quả thật.

E9 compile đầu phát hiện splitSourceDirs của ApplicationInfo là nullable. Giữ một snapshot local splitPaths rồi gán/copy riêng hai field; không dùng non-null assertion trên field framework. Tiếp tục test/build sau sửa, chưa có code image load Android thành công. Public SDK 36 chỉ có Resources constructor ba tham số, nên chưa tự nhận có resource classloader binding qua API công khai.

E9 sau sửa: 309 test đạt, app hai cấu hình/source/probe build/lint/audit đạt; bundle E9 đã kiểm exact 7 entry/checksums. Code source chỉ có trong probe, không trong cloud/candidate. [GUEST_CODE_VALIDATION.md](GUEST_CODE_VALIDATION.md) ghi hash/scope thật; E6 không boot lại, ART/Resources/Application/Binder chưa thực thi. Kế tiếp là framework/context/controller và các giao thức durable của provider, không bật capability từ code image loader component.

## E5 — plan dựng engine từ source, trước code (27/10/2026)

Đối chiếu tổng thể: E4 đã push f89ce14, 264 test đạt; không chạy lại suite cũ khi chưa đổi app. SDK SpaceCore không có source native để sửa split/16 KB. Tiếp tục phase 1/4/5 bằng runtime source riêng, không thay provider của dữ liệu đang có.

1. Dựng native trước: pin source Black00Z/Blacks-BlackBox `40282a7bf4500948cfd598fc67e6e63114b26dd9` và Dobby `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`. Đọc CMake/license/source trước build. Thay archive Dobby prebuilt bằng build từ source; không chạy build scripts của demo hoặc lấy keystore/APK/AAR của fork.
2. Native module dùng allowlist C++ source filesystem/Binder/classloader cần cho sandbox. JNI bridge riêng bỏ device spoof, hide-Xposed và disableResourceLoading; không link VirtualSpoof/AntiDetection có constructor. Chưa tải thư viện hoặc nối Java init của fork còn crash/log handlers. Module tách hoàn toàn app hiện có.
3. Fetch Git chỉ exact commit và kiểm tra working tree sạch; source/cache ở tools, source modification có review trong repo, không sửa binary đóng. Pin NDK 29.0.13846066/CMake 3.22.1, ARM64/API 26, link 16 KB. Giấy phép Apache-2.0 của upstream/Dobby và nguồn xDL phải được giữ.
4. Build shared library thật, kiểm tra ELF/ABI/alignment/dependency/export và input không có archive prebuilt. Ghi tool versions/hash/command và phạm vi chưa chạy. Đây là build native component, chưa phải engine Java hoàn chỉnh hoặc bằng chứng account isolation.
5. Sau native build, lập plan E6 cụ thể cho Java init sạch, parser/model/store split, classloader/resources/native libs/readback và transaction update; chỉ nối provider opt-in/namespace mới khi các phần tương ứng đã có code/build/test. Không bật importsSplitApks hoặc chuyển guest cũ sang engine khác từ kết quả native đơn lẻ.

Baseline mtechenginee/BlackBox-1 `df9ab44de7c1477939b28b7fa6e666396a495452` cũng đã đọc: ít crash hacks hơn nhưng nhiều module Pine/Xposed có attribution riêng cần kiểm tra. Chưa nhập/build; không dùng nó làm lý do bỏ qua provenance của native.

### Điều chỉnh E5 trước code native

Đọc sâu native fork phát hiện IO.cpp gọi strlen trên vùng malloc chưa khởi tạo, chặn resource/systemui bằng heuristic, FileSystemHook chỉ tìm symbol mà chưa hook đầy đủ và UnixFileSystemHook có chữ ký JNI sai. Không nhập các file đó rồi báo engine sạch. Thay bằng native module Fonesi tự viết, dependency duy nhất là Dobby source Apache-2.0 (kèm attribution BSD của TINYSTL); chưa cần native BlackBox hoặc xDL.

Thiết kế: rule map bất biến theo tiến trình, normalize đường tuyệt đối và khớp prefix theo ranh giới thư mục; không thay substring tùy ý, không log đường guest. JNI chỉ configure/enable/status, không auto-hook trong constructor/JNI_OnLoad. Hook libc file I/O bằng Dobby source, xử lý varargs open đúng O_CREAT/O_TMPFILE và giữ lifetime hai path của rename; kiểm tra symbol trước apply, lỗi hook phải rollback phần đã đổi hoặc giữ FAILED, không báo READY. Không hot-swap account trong tiến trình đã bật hook.

Module Android library opt-in riêng `includeSourceEngine`, không dependency app hoặc thay SpaceCore. Pin NDK/CMake/ABI/page alignment; build AAR thật rồi kiểm tra ELF, symbol/dependency, permission manifest và không chứa prebuilt archive. Test path mapper trên host bằng tình huống đọc/ghi thư mục riêng để kiểm tra ranh giới/tương tác, không gọi host tests là Android native execution. E6 còn phải nối lifecycle/Java/Binder/component/resources/split và mới có thể nghiệm thu runtime.

E5 sau build đầu: Dobby source pin có lỗi `MemRange.start`/`RuntimeModule.load_address` không khớp header; ARM64 assembly dùng relocation Mach-O trên ELF; Debug tự bật logging. Plan patch source có review: sửa theo đúng header, xử lý fgets/khởi tạo buffer và path bounded, dùng BL relocation ELF, giữ logging OFF theo cấu hình Fonesi. Prepare script xác minh exact commit + exact patch, không nới checksum hoặc dùng prebuilt. Build output `.cxx` phải ignored; ghi lỗi/build lại sau patch, không suppress compiler errors.

E5 build tiếp phát hiện include Cpu.h đã bị xóa, vòng include làm OSMemory chưa được khai báo và GCC không tự nhận đuôi .asm. Sửa include đúng ClearCacheTool, tách định nghĩa make_memory_readable xuống sau OSMemory (không bỏ kiểm tra memory), khai báo assembler-with-cpp cho source ASM. Giữ pin, không chuyển sang binary hoặc đổi assertion. Chưa chạy được host tests khi build còn lỗi.

Kết quả E5 phần native: source check exit 0, host libc routing thực thi trong hai subprocess và original marker không đổi; AAR ARM64 build/lint đạt, ELF ba LOAD align 16 KB, đúng bốn JNI export/dependency/manifest/license như allowlist. Lỗi verifier AAR directory-entry được sửa bằng bỏ directory trong tập file (vẫn giữ exact native file allowlist), không nới binary guard. App default dependency graph không có module mới; không chạy lại 264 test cũ vì app/core chưa đổi. [SOURCE_NATIVE_VALIDATION.md](SOURCE_NATIVE_VALIDATION.md) ghi hash và phạm vi chưa chạy.

## E6 — plan kiểm tra native trên Android, trước code

Đối chiếu E5: build ARM64 và host libc tests không thay Android JNI/SQLite. Cần thử framework native thực tế trước Java/provider app. Không có KVM nhưng software emulator có thể thử; chỉ được ghi boot/runtime đạt nếu lệnh thực sự thành công.

1. Chuẩn bị emulator + system image Android 34 x86_64 ở tools, AVD riêng không dữ liệu người dùng, serial/port rõ; dùng software acceleration, không yêu cầu điện thoại/root. Ghi version/ABI/boot outcome; timeout/failure phải có log, không lặp vô hạn.
2. Thêm probe APK do dự án kiểm soát, app ID riêng, không quyền/Internet hoặc SDK bên thứ ba; dùng source runtime x86_64 explicit build, không thay AAR ARM64 mặc định hoặc app chính. Hai service dedicated process A/B, host process không load hook.
3. Probe kiểm tra JNI/config/enable, file và SQLite Android thật: original/A/B dữ liệu khác nhau, ghi mỗi process chỉ vào bản tương ứng, host đối chiếu original và cả output. Không tài khoản, không mở app gốc làm clone; process chết/lỗi phải ghi thất bại, không báo success từ trạng thái đã dispatch.
4. Build/lint/audit chữ ký/permissions/components/ABI của probe, rồi cài/chạy chỉ trên serial emulator sở hữu riêng. Không thao tác ADB không có -s hoặc tác động điện thoại nối về sau. Giữ log/báo cáo private synthetic, không mật khẩu/OTP.
5. Nếu software emulator không chạy được, ghi đúng lỗi, giữ runtime chưa đạt; không chuyển x86_64 thành chứng nhận ARM64/Samsung. Phần Java loader/Binder/component/cluster vẫn phải triển khai và kiểm tra riêng sau bước này, không bật provider từ probe component.

Chi tiết E6 trước chỉnh runner/lifecycle: mỗi lần chạy có run_id UUID từ runner và report phải khớp đúng ID để không đọc nhầm PASS cũ sau cài đè/khởi động. Rotation lưu scope đã dispatch và tiếp tục poll, không tạo lại/hot-swap native services. Host vẫn phải đối chiếu cả original/A/B sau khi nhận hai kết quả thật; không dùng riêng native state làm nghiệm thu.

E6 boot lần đầu timeout 480 giây: log Android 34 cho thấy PackageManager scan 258 app, giải nén Chrome/WebView/Trichrome rồi first-boot dexopt; chưa publish đầy đủ framework để chạy probe, chưa có kết quả app. Đã dừng đúng PID/AVD của dự án và giữ logs. Plan thử đúng một warm restart cùng AVD (không wipe/root/hạ target), tiếp tục budget 480 giây dựa trên dữ liệu init đã có. Runner ghi status failure/readiness riêng, không biến boot timeout thành native pass hoặc đụng thiết bị khác.

Rà guard restart: PID máy ảo đã dừng còn entry zombie Z trong /proc nên kiểm tra tồn tại đơn thuần báo nhầm còn chạy. Sửa phân biệt zombie và tiến trình sống; không bỏ ownership/prefix/serial checks hoặc cho kill PID không rõ. Warm restart thực tế chưa chạy trước khi sửa guard này.

Chuẩn bị chẩn đoán E6 trước native run: native failure cần trả lý do tĩnh (symbol nào thiếu/hook nào lỗi và rollback) qua JNI exception, thay vì một boolean không giải thích được. Không log path/account/address hoặc thêm public JNI export. Runner ghi attempt/running status ngay từ đầu, vẫn chỉ công nhận report khớp run_id và actual passed. Sửa chẩn đoán không đổi routing/guard; kiểm tra lại native host và build các ABI trước probe.

### E6 — điều chỉnh môi trường sau hai boot timeout, trước sửa script

Warm restart Google APIs 34 cũng hết 480 giây, ADB offline trước lúc cài probe. Chưa có kết quả JNI Android. Không tăng thời hạn vô hạn hoặc coi compile là runtime. Metadata SDK công khai có ảnh `system-images;android-34;default;x86_64` AOSP không kèm bộ Chrome/Google apps của ảnh trước.

1. Dừng đúng PID có cmdline/AVD khớp ownership; giữ kết quả/log cả hai lượt. Cho prepare/runner chọn ảnh bằng allowlist `aosp34`/`google34`, ghi ảnh trong ownership và kết quả. Không đổi API/target/minSdk của APK.
2. Tạo AVD riêng với AOSP 34 và màn hình 480×800, software emulation; thử một lượt có thời hạn 480 giây. Không root, wipe AVD cũ, đọc tài khoản hoặc dùng điện thoại. Reuse/restart phải xác minh đúng ảnh/AVD đã sở hữu.
3. Nếu boot đạt, chạy probe đã build có nonce và chẩn đoán mới. Nếu JNI/file/SQLite lỗi thì dùng lỗi thực tế để lập plan sửa native, không nới assertion hoặc bỏ SQLite. Nếu tiếp tục không boot, lưu E6 chưa chạy và tiếp tục phần code-store/loader độc lập với plan riêng; không biến lỗi hạ tầng thành tương thích hoặc không tương thích ứng dụng.

Rà E6 artifact trước các build sau: runner phải đóng băng một bản APK riêng theo attempt trước static audit/install, lưu hash của đúng bản đã cài. Không đọc lại đường output Gradle sau test để gắn hash vì build tiếp có thể thay artifact trong lúc máy ảo boot. Mỗi attempt giữ input riêng trong ignored reports; nonce kết quả vẫn bắt buộc.

## E7 — plan kho APK đầy đủ cho engine source, trước code

Đối chiếu tổng thể phase 1/4/5: native component E5/E6 chưa có parser/store Java để nạp bộ split. Trong lúc kiểm tra runtime, có thể triển khai kho code độc lập; không khai báo provider/split capability hoặc di chuyển guest SpaceCore từ bước này.

1. Tách verifier Kotlin thuần cùng 16 test chữ ký thật sang module JVM `apk-runtime` để app và engine source cùng dùng, không dependency ngược từ engine vào app. Giữ nguyên crypto/manifest assertions; cập nhật build/check/audit để tổng test có cả module mới, không đếm báo cáo cũ.
2. Kho riêng chỉ lưu code, không chứa phiên đăng nhập: mỗi cluster có UUID bất biến, toàn bộ base/split giữ nguyên bytes/chữ ký. Tên file nội bộ theo identity đã audit, không theo tên đường nguồn. Không merge/re-sign, không lấy split từ APK gốc lúc mở guest.
3. Giai đoạn publish: audit nguồn → tạo staging riêng → copy/hash/fsync từng APK → read-only → audit lại toàn bộ bản private → ghi index có giới hạn/type/count rõ → fsync → atomic rename → fsync root → mở lại/đối chiếu. Lỗi trước publish chỉ dọn staging của chính lần đó; không xóa phiên bản đã publish hoặc đổi active version.
4. Readback sau process restart dùng UUID/index chặt chẽ, exact file set, path canonical/no symlink, so hash/metadata và xác minh chữ ký lại. Index không tự biến dữ liệu hỏng thành phiên bản hợp lệ. Không có fallback lấy base/split đang cài trong hệ thống.
5. Test bằng signed binary APK fixtures: cluster với tên đánh lừa/dependency/config đầy đủ, bản v1/v2 cùng tồn tại, restart readback, nguồn sai/thiếu/giả chữ ký, sửa/xóa/thêm private files/index, path traversal/symlink và hai lần publish đồng thời. Không đánh dấu nạp resources/classloader/native libs/Binder/update/account là đạt từ file store.
6. Khi source app/shared module đổi, chạy lại suite/build/lint/audit cả cloud/candidate một lượt; ghi số test thực tế và đóng gói lại APK nếu byte thay đổi. E6 báo cáo độc lập; native probe không có trong gói điện thoại.

**Còn sau E7:** chọn/extract native ABI an toàn, metadata ApplicationInfo/PackageInfo, resources/classloader toàn bộ split, lifecycle/Binder/component, process allocation, giao thức active-version/update giữ dữ liệu và adapter riêng. Chỉ nối vào app khi các phần thực thi tương ứng có bằng chứng.

Rà E7 trước lượt test cuối: canonical root phải kiểm tra cả trước createDirectories để không tạo thư mục qua symlink; đóng băng list paths/signers đầu vào trước audit/copy/index để thay đổi collection của caller không đổi metadata giữa transaction. Không coi read-only flag hay checksum là sandbox chống mã cùng UID; engine còn phải có process/binding và permission mediation.

**Kết quả E7:** `scripts/check.sh --with-engine-candidate` exit 0, 283 test (92 core, 35 APK JVM gồm 19 store mới, 156 Android mô phỏng), cloud/candidate build/lint/audit đạt. Không chạy ứng dụng guest hoặc nhập split vào SpaceCore. E6 AOSP đã có log SystemServer/PackageManager nhưng chưa boot_completed trong 480 giây. Giữ đúng AVD đó chạy trong lúc hoàn tất việc độc lập, tối đa 15 phút tính từ launch; chỉ thử lại probe bằng reuse nếu đã thật sự ready, không tạo/wipe/thử ảnh mới.

E6 ở phút 12–14: ADB trả `Service package: found` và `Service activity: found`, khác hai lượt trước. Thử đúng một native attempt bounded bằng reuse trên AVD đó, không boot lại. Với reuse, yêu cầu cả hai service hiện diện trước install; sys.boot_completed được ghi riêng và không suy ra từ native result (native sample có thể chạy trước launcher hoàn tất). Nếu install/launch/report fail thì giữ FAIL/NOT RUN và dừng AVD; không bỏ file/SQLite assertion. Thời hạn install/launch/report lần lượt 90/90/180 giây, không tiếp tục chờ boot vô hạn.

## E8 — plan native library từ cả base/split, trước code

Đối chiếu phase 1/5: kho E7 chưa chuẩn bị native search path, nên chưa đủ để gọi DexClassLoader/guest. E8 chỉ thêm component source opt-in, không sửa app/core/verifier đã đạt E7 hoặc bật engine provider.

1. Thêm Kotlin cho engine-source, dùng chung apk-runtime/core. Preparer nhận ID kho private và ABI/page size rõ; mở lại code qua kho E7 để crypto/readback, không dùng APK đang cài làm fallback. Chỉ hỗ trợ ABI đã build engine (ARM64/x86_64), ghi rõ chưa hỗ trợ ARM32.
2. Duyệt toàn bộ base/split và đúng lib/<abi>/<name>.so. Chặn path traversal, entry không hợp lệ, thiếu ABI, metadata size vượt ngân sách. Trùng SONAME trong namespace phẳng chỉ chấp nhận bytes giống nhau; khác nhau phải báo chưa hỗ trợ, không chọn bản đầu/cuối tùy ý. Không giả lập linker dependency resolution.
3. Stream giải nén vào staging riêng có hash/fsync, xác minh ELF64 little-endian/DYN/machine/program headers và PT_LOAD phù hợp 4 KB/16 KB, không W+X/exec stack. Không đọc toàn .so vào RAM. Artifact immutable/UUID/atomic publish/readback; không ghi APK gốc, active-version hoặc vùng tài khoản. Receipt chỉ là code/native image, giao thức registry/activation vẫn còn mở.
4. Tạo fixture bằng NDK clang thật và ký lại APK test bằng khóa disposable, gồm base/feature/config, ARM64/x86_64, ELF 4 KB/16 KB, sai machine/ELF và collision. Fixture/keys/binaries chỉ trong build output. Test real extraction, hash/readback/corruption/ABI/alignment/source integrity; không dùng header giả để nhận đã nạp native trên Android.
5. Build/test/lint/audit engine-source và build lại probe có đúng dependency/notice; kiểm tra bốn export JNI/ELF engine vẫn giữ. App E7 không dependency engine-source nên không chạy lại 283 test app chỉ vì component opt-in đổi. Native .so guest chưa dlopen trong E8; classloader/resources/Binder/lifecycle vẫn chưa làm.

E8 compile test đầu phát hiện Files.writeString không có trong Android SDK API surface. Thay thao tác ghi test bằng Files.write(byte[]) có trên Android, không thêm shim hoặc suppress API. Mã production đã compile, test chưa chạy trước chỉnh này; chạy lại đúng test/build/lint component.

E8 đạt 16 test ELF/extraction JVM, build/lint 0 lỗi. Chạy source checker để giữ lại AAR ARM64 đã audit trước khi build probe x86_64. Thêm chế độ audit-only cho runner: kiểm tra APK thật/ELF x86_64/16 KB/manifest/license và ghi hash, không boot máy ảo. E6 reuse vừa đọc thấy framework không ready; crash buffer có DeadSystemException, chưa cài/chạy probe. Đã dừng đúng AVD/PID. Không thêm lượt boot; giữ Android JNI/SQLite NOT RUN, không quy lỗi này cho tương thích social app.

Rà build E7 trước checkpoint: SDK của fixture JVM phải được resolve khi task chạy, hỗ trợ ANDROID_HOME/ANDROID_SDK_ROOT/local.properties như Android build. Tránh sdk.get() eager làm cả :core:test đòi SDK khi chưa chạy fixture. Chỉ sửa task configuration/lazy resolution, không đổi verifier/fixture bytes/assertions. Kiểm tra task graph không có engine-source ở build thường và dry-run core khi bỏ biến SDK; không lặp các suite đã đạt từ đổi cấu hình này.

Rà cấu trúc ELF E8 phát hiện cần kiểm tra congruence theo chính p_align, ngoài page size, và overflow vaddr+memsz. Bổ sung hai fixture âm từ ELF NDK thật rồi sửa program header, ký APK test; yêu cầu parser từ chối với trang 4 KB dù offset/vaddr vẫn congruent modulo 4 KB nhưng sai modulo 16 KB. Đây là sửa validation component, chạy lại native tests/build/audit; app E7 không đổi. Notice source cũng đã bổ sung dependency apksig nên AAR/probe phải build/audit lại để hash khớp assets cuối.

## E3 — plan tiếp tục engine trước triển khai (27/10/2026)

Đối chiếu tổng thể: source 0.5.0 đã push, 246 test và gói debug đã kiểm tra; S1/S2/U1/Q1 không cần chạy lại khi chưa đổi. Điểm chính còn thiếu là engine có source/import split đầy đủ và runtime. Tiếp tục phase 1/4/5, không đổi yêu cầu sang profile giới hạn/root/web hoặc bỏ ứng dụng bắt buộc.

1. Khảo sát baseline khác/nhánh source sạch bằng Git/API công khai; pin commit, đọc license/build/native/init/log. Các fork cũ đã bị loại không tự trở thành đạt do đổi tên. Chưa chạy script/build bên thứ ba trước khi đọc.
2. Ưu tiên baseline đã có model/parser/store split. Nếu có, xác minh đường end-to-end: signatures/dependency, settings/Parcel, private APK cluster, ApplicationInfo/resource/classloader/native libs và update; nguồn gốc thư viện native phải được ghi.
3. Chỉ sau audit đầu vào mới ghi plan adapter/build opt-in/namespace mới, test bằng bộ APK split do dự án tự tạo và build thư viện thực. Nếu phải sửa engine, liệt kê file và ranh giới transaction trước patch; không dùng một test copy file để nhận hỗ trợ clone.
4. Kiểm tra độc lập khả năng chạy cloud (ADB/emulator/ABI), không suy ra Samsung từ emulator. Điện thoại thật vẫn hoãn theo yêu cầu và chưa được ghi đạt; không bắt người dùng đăng nhập trước payload/cách ly.
5. Với blocker không giải quyết được trong phạm vi nguồn/SDK hiện có, lưu bằng chứng cụ thể và phần code đã thực sự hoàn thành; không chỉ tăng số test hoặc thêm UI rồi gọi sản phẩm 100%.

### E3 — kết quả và hướng xử lý trước code

- Blacks-BlackBox `40282a7…` có đường `.apks`, nhưng chọn base theo tên/kích thước và chọn tối đa một ABI/density/language bằng heuristic tên; không parse/verify mọi split như một cluster. Copy split/lib bỏ qua lỗi, parser vẫn parse base và còn hydrate split từ app host. Giữ các crash handler/log/spoof của fork trước. Chưa thể dùng làm engine đạt yêu cầu.
- Renjana `14302a5…` có split classloader nhưng fallback `tryDirectLaunch` mở app gốc không cách ly; VirtualContext ghi rõ không chặn syscall native. Không tích hợp như clone thật. ServenScorpion `23aa5ec…` còn dùng split app host và chưa tìm được baseline đầy đủ có provenance/license tương ứng. Chưa chạy build của ba repo.
- ADB hiện không có thiết bị; không có `/dev/kvm`. Không suy ra runtime từ build hoặc cố khởi emulator ARM trong cloud rồi gọi là Samsung.

## E4 — plan chi tiết kiểm tra tính hợp lệ bộ APK trước import

Đối chiếu bước 17 của phase 4 và phát hiện E3: cần kiểm tra manifest/chữ ký thật của **mọi APK**, không dựa tên `base.apk` hoặc metadata app gốc. Đây là prerequisite cho engine split; không thay capability engine hiện tại thành true.

1. Thêm auditor đọc binary manifest và kiểm tra chữ ký mật mã bằng Android apksig được pin cùng AGP 8.11.1 (Apache-2.0), không tự viết parser/signature verifier. Giới hạn manifest input; không trích ZIP ra kho user. Đọc package/version64/split name/feature/config/dependency từ manifest, bỏ qua tên file khi phân loại base/split.
2. Audit cả tập: đúng một base, ID split duy nhất, package/version/signers khớp nguồn và nhau; dependency feature/config có thật và không cycle. Canonical path phải duy nhất/tệp thường không symlink. Từ chối APK hỏng/unsigned/tampered/mixed signer/version/pkg/missing dependency; trả về danh tính+hash của mọi member để đối chiếu, không import hoặc sửa tệp.
3. Nối auditor ở adapter candidate trước import single APK và khi đọc lại bản APK riêng. Vẫn từ chối split trong SpaceCore; không dùng auditor làm bằng chứng SDK nạp split. Tái sử dụng kiểm tra cũ về private copy/ARM64/hash/SDK readback.
4. Tạo fixture APK thật bằng aapt2/keytool/apksigner trong build output, dùng khóa test sinh riêng không đưa vào Git. Test binary manifest/signature thật (base có tên bất kỳ, split feature/config, source sai, partial/mixed/tampered/duplicate paths/cycle), không chỉ mock certificates. Fixture generation chỉ thuộc test, không chạy native engine hoặc dùng tài khoản.
5. Chạy test liên quan rồi kiểm tra hai cấu hình/lint/audit; báo thay đổi số test và hash artifact. Lưu license/dependency/ràng buộc, cập nhật báo cáo/STATUS và push source. E4 chỉ đóng verifier khi có bằng chứng; E3/runtime và 100% toàn sản phẩm vẫn cần giải quyết riêng.

Rà E4 trước chỉnh thứ tự đọc: kiểm tra kích thước/đọc manifest bounded trước khi gọi apksig, vì thư viện có thể tự đọc manifest trong verify. Manifest chỉ được tin sau crypto verify; parse trước giới hạn không đồng nghĩa chấp nhận chữ ký. Bổ sung fixture manifest XML text đã ký để chứng minh parser từ chối dữ liệu không phải binary, và fixture ZIP manifest quá budget đã ký để kiểm tra chốt tài nguyên trước verifier.

Rà E4 chi phí readback trước chỉnh adapter: không verify/hash lại toàn APK cho mỗi lần inspect từng clone (nhóm shared lớn sẽ đọc cùng file lặp). Verify nguồn một lần trước mỗi import; private copy phải có hash bằng chính file vừa verify theo guard hiện có, nên cũng được ràng buộc với chữ ký đã kiểm. Các inspect định kỳ vẫn dùng archive/SDK identity như trước; không tuyên bố đã crypto-audit mọi file mỗi lần quét hoặc cache chữ ký theo size/mtime thiếu an toàn.

Đối chiếu AOSP ApkLiteParseUtils/SplitDependencyLoader trước bổ sung E4: kiểm tra cả isSplitRequired/requiredSplitTypes/splitTypes để base bị tách không được chấp nhận như standalone nếu thiếu config bắt buộc. Bổ sung APK fixture thật khai báo required type, trường hợp thiếu type và đủ type. Giới hạn uses-split một dependency như framework, không tự gộp nhiều tag mà Android chỉ nhận tag đầu. Rà build: khai báo APK fixture là input của test task để script/fixture đổi phải test lại; truyền SDK/JDK qua tham số từ Gradle, hỗ trợ cả local.properties khi ANDROID_HOME không có. Đóng ZipFile của fixture đúng vòng đời; không thay đổi thư mục HOME/khóa ký app.

Plan đóng gói E4 trước sửa script: giữ candidate/payload/guide/SDK notice/checksums, thêm APKSIG_NOTICE.md cho dependency mới; allowlist ZIP thành đúng 7 entry. Fixture APK/khóa test không thuộc gói điện thoại. Báo cáo 26/10 giữ là lịch sử, báo cáo E4 ghi hash/suite của build mới và không đánh dấu engine split đã hoạt động.

Kết quả E4 cloud: 16 test verifier với APK ký thật đạt, full check hai cấu hình exit 0; 262 test (90 core + 172 app), không lỗi/bỏ qua. Build/lint/manifest/signature audit đạt, cảnh báo vẫn 5 cloud/15 candidate; guard split của SDK giữ nguyên. Báo cáo/hash ở [APK_SET_VALIDATION.md](APK_SET_VALIDATION.md). Chưa đóng engine/runtime hoặc 100% toàn sản phẩm.

Rà ranh giới create trước sửa cuối E4: backend.install trước đây kiểm tra archive/ABI sau createUser, nên nguồn lỗi có thể để lại native user/reservation rỗng. Plan thêm GuestBackend.validateSource chỉ đọc, gọi trên snapshot đã hash-verify trước reserve/createUser; SpaceCore dùng cùng helper archive/crypto/ABI cho preflight và kiểm tra lại trước install. Không sửa/xóa native user để rollback. Test source bị preflight từ chối và mất foreground trong preflight phải không reserve hoặc vào SDK, đồng thời dọn snapshot chưa dùng. Chạy lại kiểm tra hai cấu hình sau thay đổi này; gói 262 test là mốc trước sửa, không dùng làm artifact cuối.

Kết quả cuối E4 sau preflight: full check exit 0 ở cả cloud/candidate, **264 test (92 core + 172 app)**, không failure/error/skipped; APK build/lint/manifest/signature audit đạt. Giữ 5/15 cảnh báo như trước, không mở capability split hoặc đổi SDK/target/chữ ký. Nguồn sai bị chặn trước SDK user; phần source-engine/runtime còn mở trong E3. Gói cuối phải khớp hash mới trong APK_SET_VALIDATION, không dùng gói mốc 262.

Đóng E4 phần cloud: bundle mới exit 0, đúng 7 entry, CRC/SHA-256 khớp, candidate/guide/notices khớp source và báo cáo cuối. Không có binary/khóa fixture trong gói điện thoại hoặc source. Đã kiểm tra source secrets, cú pháp script, link tài liệu và whitespace. Giữ E3/runtime/engine split mở; không gọi toàn sản phẩm 100%.

Đối chiếu [ROADMAP.md](ROADMAP.md), [PLAN.md](PLAN.md), [STATUS.md](STATUS.md) và kết quả `0.4.0`. Yêu cầu hiện tại: tiếp tục hoàn thiện, lập plan trước từng phần việc, code/test tỉ mỉ; thử điện thoại gom về đợt sau. Không gọi 100% khi còn yêu cầu bắt buộc chưa đạt.

## Đối chiếu tổng thể trước lượt triển khai

| Hạng mục | Hiện có | Còn thiếu/điều kiện đạt |
| --- | --- | --- |
| Không root, nhiều app/bản | SDK candidate thật, namespace/user/registry, UI vòng đời | Split APK, Android mới và runtime phải được chứng minh; không hạ yêu cầu đa app. |
| Tài khoản/dữ liệu riêng | Tạo sạch, không copy phiên, marker/ID riêng | SDK thực thi cách ly và giữ phiên chưa kiểm tra trên máy. |
| Mở trực tiếp màn hình | Shortcut/token/readback và lifecycle cloud test | One UI/cold start/tài khoản đúng còn chờ thiết bị. |
| Cập nhật theo app gốc | Snapshot/queue/lock/journal, UI từng bản/tất cả, native adapter | Nhập split, chọn nhiều, scheduler tùy chọn và giữ dữ liệu thực tế. |
| Tiện ích/bảo vệ | Tên/nhóm, quyền runtime và thiết lập engine | Khóa quản lý và shortcut, backup cấu hình có kiểm tra, tìm/lọc nhiều bản, chẩn đoán có giới hạn. |
| Tương thích/ổn định | Guard API/ABI, test cloud, audit APK | Ma trận app/device, lỗi nền, hiệu năng, ký/nâng cấp release và nghiệm thu thật. |

## Thứ tự tiếp tục

1. **Phase 1/4/5 — E1: giải quyết khả năng nhập split.** Kiểm tra SDK hiện tại/nhánh mới và engine có source có giấy phép. Đọc đường parser/import/lib/resources/readback/update, không chỉ dựa API có nhận File. Chỉ nối bộ APK khi mọi split được giữ nguyên chữ ký, đúng phiên bản và engine thật có thể dùng; không merge/re-sign APK. Nếu engine không sửa được, ghi bằng chứng và tiếp tục đánh giá phương án khác, chưa báo đạt.
2. **Phase 6 — S1: bảo vệ truy cập.** Ghi thiết kế khóa cho cả manager và shortcut; xác thực hệ thống, vòng đời nền/rotation, máy không hỗ trợ và fallback. Không lưu PIN/tài khoản và không gọi UI lock là mã hóa guest. Chỉ chạy dispatch sau khi mở khóa. Test chính sách và adapter/lifecycle khả thi.
3. **Phase 6 — S2: sao lưu/nhập cấu hình.** Chỉ tên/nhóm/cấu hình an toàn; không sao lưu APK/session, engine UUID, pending marker, journal hoặc tạo guest qua dữ liệu nhập. Hợp đồng JSON có version, giới hạn input, duplicate/unknown field, preview và transaction không ghi một phần. Xuất qua Android document picker; nhập chỉ áp vào các bản thật được đối chiếu, không ghép nhầm khác engine.
4. **Phase 5/6 — U1: quản lý và tương thích.** Tìm/lọc/nhóm/chọn nhiều, thống kê nguồn/dung lượng trong phạm vi đọc được; ma trận bằng chứng từng app/máy, trạng thái chưa thử phải giữ đúng. Kiểm tra thao tác chỉ nhắm tập ID/token đã chọn và báo phạm vi shared code.
5. **Phase 4 — Q1: tự kiểm tra/cập nhật có tùy chọn.** Planner thủ công và tự động dùng cùng queue. Thiết kế scheduler theo hạn chế Android/khởi tạo SDK, lưu lựa chọn, không chạy engine khi chưa bật hoặc khóa chưa giải. Dừng/hoãn đúng ranh giới và không auto retry kết quả chưa rõ. Không giả định worker nền có quyền mở giao diện/FGS.
6. **Phase 7 — R1: đóng gói và nghiệm thu.** Tự động hóa build tái lập/audit/chữ ký, kiểm tra cài nâng cấp, tài liệu lỗi còn mở, gói thử đầy đủ. Không đưa khóa ký/tài khoản vào Git. Thiết bị thật vẫn là phần đo hiệu năng, thông báo, cách ly, giữ phiên và đa hãng của đợt cuối.

## Plan chi tiết E1 trước khi sửa code engine

**Đầu vào:** SpaceCore commit/hash đang pin; source các engine đã khảo sát; source adapter/snapshot hiện tại; yêu cầu giữ nguyên chữ ký và cập nhật từ bản gốc. Không có thiết bị ARM kết nối đã được xác nhận.

**Các bước kiểm tra:**

1. Đọc API/bytecode/parser của SDK để xác nhận nhận base, thư mục, bộ split hay system source; xác định đường update có dùng chung code và private copy.
2. Kiểm tra revision mới của nguồn chính và các ứng viên có giấy phép rõ, không tự mua/liên hệ. Ghi URL, commit, files và phát hiện cụ thể.
3. Nếu có đường dùng split đủ căn cứ, lập plan sửa adapter với bước validate package/version/signer/splitName/dependency, sao chép bất biến và atomic publish/readback toàn bộ. Viết test lỗi/missing/duplicate/mixed source trước kiểm tra hồi quy.
4. Nếu không có, ghi blocker có bằng chứng, giữ guard từ chối split và tiếp tục phần độc lập S1/S2 với plan chi tiết được ghi trước. Không nối API giả hoặc chỉ copy split ngoài engine rồi gọi là hỗ trợ.

**Điều kiện đạt E1:** engine lưu và sử dụng cả base/split đúng metadata/ABI/resources/native libs, readback đủ và update không mất dữ liệu; phần chạy thật phải được xác minh trong đợt thiết bị sau. Build/static observation chỉ đạt phần tương ứng.

## Nhật ký thực thi

- 26/10/2026: đã đọc lại tổng thể và trạng thái `0.4.0`, ghi plan E1 trước nghiên cứu/sửa engine. E1 đang thực hiện; S1/S2/U1/Q1/R1 chưa được đánh dấu hoàn tất.
- S1 sau sửa/test: `scripts/check.sh --with-engine-candidate` đạt 147 test, hai cấu hình build/lint/audit đạt. Chỉ đạt phần cloud; prompt/biometric/One UI thật còn chờ. Đã đối chiếu lại tổng thể, bắt đầu code S2 theo plan dưới đây; chưa đóng E1 hoặc toàn phase 6.
- S2 đạt kiểm tra cloud hai cấu hình ở mốc 175 test; U1 đạt ở mốc 210; Q1 và rà hiệu năng/URI/shared scope đạt ở mốc 239. Các mốc là tổng suite cùng thời điểm, không cộng dồn thành số test mới.
- R1 sau guard registry/native: `scripts/check.sh --with-engine-candidate` exit 0, tổng 246 test (90 core + 156 Android mô phỏng API 26/34), không lỗi/bỏ qua; cả hai build/lint/audit APK đạt. Lint còn 5 cảnh báo cloud và 15 candidate; không suppress để lấy kết quả xanh. Constructor Application đã xử lý cảnh báo giữ Context mới phát hiện.
- E1/E2 chưa đạt, với bằng chứng commit/API/file trong [ENGINE_SPLIT_AUDIT.md](ENGINE_SPLIT_AUDIT.md). Chưa có fork thay thế được vendor/build/runtime. Không đóng toàn phase 1/2/5/6/7 hoặc toàn sản phẩm từ kết quả cloud.
- R1 đóng gói: script bundle exit 0, ZIP 0.5.0 đúng 6 entry allowlist, CRC/SHA-256 từng entry khớp, guide/NOTICE khớp source, không lẫn artifact 0.4. Đã kiểm tra link tài liệu, cú pháp script, diff whitespace và không thấy khóa private/GitHub credential hoặc APK/AAR/keystore trong tập source. Chi tiết hash và phạm vi chưa chạy trong [PHASE6_VALIDATION.md](PHASE6_VALIDATION.md).

### E1 — kết quả khảo sát, trước khi đổi hướng

- `git ls-remote` của `FSpaceCore/SpaceCore` vẫn trỏ commit `3826a2f…`; không có bản mới được phát hiện để thay SDK pin.
- Bytecode `FCore.installPackageAsUser(String,int)` chỉ lấy `ApplicationInfo.sourceDir`; nhánh File dùng `installByStorage`. `j6` tạo đường `data/app/<pkg>/base.apk`; các class tham chiếu split tìm được chỉ là mirror framework. Đây không phải bằng chứng hỗ trợ bộ APK.
- Source `ALEX5402/NewBlackbox` commit `89b5983…`: `CopyExecutor` chỉ copy `baseCodePath`; `BPackage` chỉ lưu base path; `PackageManagerCompat` thêm một asset path; fallback `BPackageManager` gán splitNames rỗng. Cần sửa parser/storage/parcel/resource/classloader/native libs/update, không phải thêm tham số cho adapter. Các vấn đề log sender/spoofing đã audit vẫn tồn tại.
- **Trạng thái:** E1 chưa đạt và tiếp tục mở. Giữ guard từ chối split, không thêm API giả hoặc sao chép split bên ngoài rồi nhận hỗ trợ. Chuyển S1 độc lập theo thứ tự tổng thể trong khi điều kiện engine còn mở.

## Plan chi tiết S1 — trước code khóa truy cập

**Đối chiếu:** bước 26 của phase 6; phụ thuộc các entry point đã ghi ở SHORTCUTS.md. Build cloud và candidate dùng chung UI, chỉ candidate có native SDK; không thay adapter/import engine trong S1.

**Thiết kế và bước nhỏ:**

1. Chính sách khóa mặc định tắt, lưu riêng bằng AtomicFile; dữ liệu hỏng/không đọc được phải yêu cầu xác thực, không tự tắt khóa. Không đưa chính sách khóa hoặc phiên auth vào backup cấu hình.
2. Core session dùng generation cho từng yêu cầu auth: thành công chỉ mở khi request hiện tại và activity ở foreground; kết quả cũ/thất bại/background không mở. Background khóa lại, rotation giữ phiên trong ViewModel; process mới luôn phải xác thực lại nếu bật khóa.
3. AndroidX BiometricPrompt với strong biometric hoặc mã khóa hệ thống. API 30+ dùng authenticator combo được hỗ trợ; API 26–29 dùng biometric rồi fallback màn hình xác nhận mã khóa Android. Không tự thu PIN, không lưu vân tay hoặc tạo đường bypass khi không có khóa máy.
4. MainActivity và activity shortcut cùng đi qua gate **trước khi dispatch guest**. Chỉ tạo/hiển thị bộ quản lý sau khi mở khóa; shortcut không gọi model.start trước gate. Dùng FLAG_SECURE khi bật khóa để che screenshot/recents của host, không quảng cáo mã hóa/khóa mọi hoạt động guest.
5. Bật/tắt khóa đều cần auth hệ thống và ghi policy thành công trước khi báo đã đổi. Hủy, khóa sinh trắc, không enrollment/hardware, lỗi ghi policy phải giữ trạng thái an toàn và có hướng xử lý bằng khóa máy.
6. Dependency/manifest: thêm AndroidX biometric + theme phù hợp, chuyển host sang FragmentActivity; chỉ thêm USE_BIOMETRIC/USE_FINGERPRINT. Cập nhật **allowlist chính xác** cho APK cloud/candidate/probe-audit, giữ mọi guard SDK hiện tại; không suppress lỗi để thông qua. Mốc build tiếp theo 0.5.0, cập nhật các script artifact tương ứng.

**Kiểm tra trước khi đóng S1 cloud:** test session generation/background/rotation/failure; AtomicFile ON/OFF/corrupt/unknown format/write failure; route chỉ dispatch sau gate; kiểm tra build/lint hai cấu hình và quyền/component/signature của APK thật. Thử prompt/OEM/lifecycle thật để đợt điện thoại sau và ghi chưa chạy. Nếu nền tảng không đủ bảo đảm, khóa giữ nguyên, không suy đoán auth thành công.

**Không đổi trong S1:** không thu tài khoản/OTP, không xóa guest để xử lý auth lỗi, không gọi UI lock là sandbox/mã hóa tài khoản, không đánh dấu engine/split đã đạt từ phần khóa.

**Điều chỉnh S1 sau lượt test đầu:** kiểm tra policy đổi ở hai cửa sổ phát hiện callback Flow có thể xử lý ngay trong lúc ghi policy và làm mất grant của chính chủ vừa xác thực. Sửa theo thứ tự: giữ kết quả auth hợp lệ → ghi thành công → cấp lại riêng session của chủ yêu cầu; các cửa sổ khác vẫn khóa. Test dùng broker riêng theo Context fixture thay vì singleton trỏ Context của sandbox trước, giữ nguyên mọi assertion về policy/khóa. Đồng thời giữ activity shortcut qua màn hình mã khóa Android (noHistory=false, vẫn ẩn recents); noHistory=true không phù hợp nhận credential result. Các thay đổi này phải qua lại test trước S2.

**Điều chỉnh dependency S1 sau lint:** Biometric 1.1.0 kéo Fragment cũ không đủ hợp đồng ActivityResult. Pin Fragment KTX 1.8.8 trực tiếp (APIResult yêu cầu tối thiểu 1.3), không suppress `InvalidFragmentVersionForActivityResult`; chạy lại test/build/lint và audit thật cả hai cấu hình.

## Plan chi tiết S2 — trước code sao lưu/nhập cấu hình

**Đối chiếu:** bước 28 phase 6; giữ nguyên guest, engine registry, queue và mọi chính sách khóa S1. Không sao lưu phiên đăng nhập và không làm xuất/nhập mật khẩu.

1. Format JSON `vn.fonesi.configuration`, version 1, chỉ chứa danh sách `{ref, package, label, group}`. `ref` là số thứ tự tệp, không phải clone ID/token/engine UUID. Không chứa APK, chứng thư, đường dẫn dữ liệu, nhật ký, lựa chọn engine/khóa, quyền hoặc phiên xác thực.
2. Codec đọc UTF-8 nghiêm ngặt với ngân sách 2 MiB, token/type và tập field chính xác, không chấp nhận key trùng, ref trùng, unknown format/version/fields, số bị ép kiểu, Unicode hỏng, label/group quá dài hoặc package sai dạng. Ghi JSON hợp lệ trong bộ nhớ trước khi mở output; lỗi phải không làm đổi DB.
3. Import có preview, không tự ghép theo tên/thứ tự. Người dùng map rõ từng ref sang một bản clone hiện có cùng package; cho chọn một phần và hiển thị số bỏ qua. Không map hai ref vào cùng target. Không tự tạo guest/hàng metadata từ tệp nhập.
4. Apply lấy cùng operation lock, yêu cầu phiên UI được mở khóa, đối chiếu token/namespace và readback SDK trước transaction; chỉ sửa tên/nhóm của các hàng đã chọn. Chưa có engine thì chỉ preview, chưa áp dụng. Hỏng/mất/đổi target phải không ghi một phần. Đồng bộ shortcut sau commit; lỗi launcher không được nói metadata chưa ghi.
5. Android SAF chọn nơi xuất/mở tệp; không xin quyền đọc kho tệp. Đăng ký launcher ở Activity ngoài composition gate; lưu URI/hành động pending trong SavedStateHandle để không mất kết quả khi picker làm host bị khóa/background. URI chỉ `content`, chưa đọc/ghi sau picker cho tới khi người dùng mở khóa và xác nhận. Không persist nội dung file hay kết quả auth vào saved state.
6. UI giải thích file có tên/nhóm có thể nhạy cảm, không phải backup tài khoản. Có preview, target chooser, xác nhận áp dụng một phần và bỏ lựa chọn. Đổi process/rotation không tự áp dụng lại hoặc xuất tự động.

**Kiểm tra:** roundtrip Unicode/null; malformed/trailing/duplicate/oversized/unknown/type input; payload chứa field session/engine/lock bị từ chối; transaction và readback với native SQLite, stale token, wrong package/engine, duplicate mapping, unavailable engine, authorization đổi trước commit, kết quả picker giữ pending mà không tự IO. Sau khi test S1 đạt phần cloud mới code S2; audit hai APK sau tích hợp, thử SAF/OEM/auth thật để đợt thiết bị cuối.

**S2 sau lượt kiểm tra đầu:** core/lưu trữ đạt 175 test, app cloud build/lint đạt; đang audit candidate trước U1. Bổ sung giữ quyền URI persistable cho cả create/open khi provider cho phép, test pending cả import/export; audit route candidate phải giữ được credential result. Kết quả SAF thật còn chờ, không tự coi việc stage URI là xuất/nhập đã thành công.

**R1 — bước nhỏ chuẩn bị artifact 0.5.0 trước sửa script:** thư mục bundle có artifact 0.4 cũ. Đóng gói bằng allowlist đúng candidate hiện tại + payload r1/r2 + hướng dẫn/NOTICE/checksums, không quét toàn bộ APK để tránh lẫn phiên bản. Dùng hướng dẫn thử hiện tại có khóa/config; giữ các gói lịch sử, không xóa đầu ra cũ. Verify mọi checksum và danh sách ZIP sau build; không coi bundle là ký release/nghiệm thu.

## E2 — đánh giá nhánh engine có source, trước import/sửa code

S1/S2 độc lập không thay thế yêu cầu split. Đối chiếu lại E1 và toàn bộ phase 1/4/5: phải kiểm tra khả năng sửa một engine có source được phép thay vì coi việc SDK hiện tại thiếu API là điểm dừng vĩnh viễn.

1. Đọc layout/build/dependency/license của source NewBlackbox đang pin, các native library có source/prebuilt và mọi init/log/network/device-spoof đã phát hiện. Chưa chạy Gradle/script của repo thứ ba hoặc nhập binaries/keystore của demo.
2. Lập danh sách đường split cần sửa: parser cluster/certificates, BPackage parcel/settings, code-store base+split, native libs theo ABI, ApplicationInfo/PackageInfo, resources/classloader, readback và shared update. Không khai báo capability split từ một patch copy file.
3. Nếu nguồn/build/license đủ để tiến hành, ghi quyết định và plan patch cụ thể trước khi vendor module tối thiểu. Tách build opt-in/provider/namespace; giữ branch SpaceCore để so sánh, không đưa guest cũ vào provider khác. Không nhập log sender/spoof mặc định.
4. Đầu tiên build thư viện từ source và kiểm tra manifest/native alignment. Sau đó dùng bộ APK split do dự án tự tạo, test parser/signature/private paths/snapshot/update protocol trong cloud; thực thi guest vẫn để đợt máy thật. Nếu có blocker cụ thể, ghi đúng dependency/file/lỗi và không gắn nhãn đạt.

**Điều kiện qua bước đánh giá E2:** có source/build path được phép và một bản thiết kế đầy đủ thay đổi, không phải chỉ tuyên bố của README. Chưa được ghi engine hỗ trợ split/runtime cho tới khi các phần tương ứng có bằng chứng.

**E2 sau đọc source/build:** có Apache-2.0 ở root nhưng native Dobby là archive prebuilt trong repo và chưa có hồ sơ nguồn/license đầy đủ của binary đó. BlackBoxCore cài nhiều crash handler ngay class-load; SimpleCrashFix nuốt một số uncaught exception rồi gọi sendLogs; còn nhánh disableResourceLoading. Chưa chạy build của engine này và chưa đưa vào sản phẩm: không được dùng các handler/fallback UID 0 hoặc splitNames rỗng để coi guest đã hoạt động. Cần baseline nguồn sạch/nguồn native có provenance hoặc một đợt sửa/audit engine đầy đủ. E2 chưa qua cổng đánh giá, không ghi thành build thất bại hoặc hỗ trợ split. Tiếp tục U1 độc lập.

## Plan chi tiết U1 — trước code quản lý/chọn nhiều/preview cập nhật

**Đối chiếu:** phase 6 bước 25/27, phase 4 bước 19 và phase 5 bước 23. S1/S2 đã qua 175 test và build/lint/audit cloud/candidate; không bỏ guard engine/split.

1. Domain thuần: tìm tên/nhóm/package không phân biệt hoa/dấu tiếng Việt; lọc nhóm và trạng thái từ readback (chưa biết không được thành dừng). Selection dùng launch token, giữ rõ các mục đang ẩn qua filter nhưng loại stale/khác engine; Chọn đang hiển thị chỉ thêm tập đã lọc, không chọn toàn DB ngầm.
2. UI: checkbox từng bản, tìm/lọc/nhóm, số mục đã chọn/đang hiển thị, bỏ chọn và cập nhật tập chọn. Không thêm bulk xóa không được review. Nút tất cả/từng bản/tập chọn dùng cùng service preview/confirm.
3. Preview cập nhật chạy dưới operation lock và authorization, đọc metadata/engine/app nguồn. Hiển thị app/version, đủ nhóm bị ảnh hưởng (kể cả không chọn do shared code), bản đã current và từng lý do bị chặn. Trước confirm so sánh lại nguồn, ID/token, scope/capability và plan; thay đổi thì yêu cầu xem lại, không enqueue âm thầm với ảnh hưởng mới.
4. Declare capability split tường minh trong Ready: mặc định chưa hỗ trợ; SpaceCore vẫn false. Planner phải chặn từng nguồn split và vẫn cho xem các nhóm khác, không bỏ split hoặc gắn nhãn engine hỗ trợ. Test planner giả định split trước đây phải khai báo capability của test fixture rõ, giữ nguyên assertion về nguồn/ảnh hưởng. Runner kiểm tra capability không đổi trước apply.
5. Enqueue nhóm đã consent trong một Room transaction, chống trùng như cũ; lỗi nhóm sau phải rollback cả lần enqueue. Runtime recheck/prepare/apply/readback trong runner vẫn giữ. Preview không persist như lệnh đã xác nhận và không tự enqueue sau process restart.
6. Chẩn đoán chỉ đọc thiết bị/API/ABI/page size, dung lượng trống và stats **chính UID/package Fonesi** nếu Android cấp, trạng thái tối ưu pin. Không xin Usage Access/all-files/QUERY_ALL_PACKAGES để lấy số. Không đọc nội dung hoặc log tên file guest; lỗi quyền/statistics hiển thị chưa biết. Link cài đặt chỉ nhắm Fonesi. Matrix app không suy ra tương thích từ priority: single APK vẫn chưa thử, split ghi đang bị chặn.

**Kiểm tra U1:** unit accent/group/status/selection và planner capability; native SQLite service preview shared scope, stale sources/peer/state/capability/token, dedup và atomic enqueue lỗi giữa nhóm; authorization mất không ghi queue. Build/lint/audit hai cấu hình và rà UI/backend không còn nút đi đường enqueue cũ. Đo/launcher/SAF/thông báo/OEM thật giữ cho đợt cuối; chưa gọi phase 5/6 hoàn tất nếu phần ngoài phạm vi này còn thiếu.

**U1 sau lượt cloud đầu:** test/build/lint đạt, thêm disable nút Chọn đang hiện khi không có target đã đọc lại/đúng namespace để tránh thao tác vô tác dụng với metadata cũ. Đang audit hai cấu hình trước Q1; chưa chạy phép đo Android/launcher thật.

## Plan chi tiết Q1 — trước code tự kiểm tra/cập nhật có điều kiện

**Đối chiếu:** phase 4 bước 17–20, giữ manual preview/consent và queue chung. SpaceCore khởi tạo trong attachBaseContext, chưa có bằng chứng init/FGS hợp lệ khi worker khởi app nền dưới target 36. Vì vậy chưa tự tạo worker/boot/background SDK với giả định được phép; mốc này triển khai rõ **khi Fonesi đang mở và đã mở khóa**. Background độc lập vẫn là điều kiện engine/scheduler còn mở, không đổi tên mode foreground thành chạy nền hoàn chỉnh.

1. Atomic policy riêng, mặc định OFF; các mode OFF, CHECK_ON_OPEN (chỉ báo), APPLY_WHILE_OPEN (user consent rõ mọi nhóm và shared peers). Corrupt/unknown policy về OFF và báo cần xem lại. Không nhập/export behavior policy qua file tên/nhóm. UI thay mode APPLY phải có dialog giải thích ảnh hưởng, không bật ngầm.
2. Broker theo dõi foreground riêng với grant auth kể cả policy khóa OFF. Tự động chỉ chạy nếu có UI ở foreground và có access; process mới không tạo grant. Ra Home khóa lại/không foreground phải ngăn bắt đầu apply, không chỉ dừng vòng lặp UI.
3. Runner nhận execution-policy callback và kiểm tra khi lấy job, sau prepare/recheck, ngay trước durable APPLYING marker. Nếu policy mất trước apply thì DEFERRED với lý do rõ, dọn snapshot và không gọi SDK; đang apply phải hoàn tất verify/review, không giả hủy/rollback. Runner cũ trong test dùng default true; product runner áp guard foreground/access.
4. Auto coordinator chỉ một instance check/run ở một thời điểm trong app, không khởi UI guest. Recover readback trước; preview toàn metadata current provider bằng service U1, CHECK chỉ lưu tóm tắt trong RAM; APPLY chỉ confirm cùng plan rồi gọi runner từng job, recheck foreground/mode/pause giữa tác vụ. Mọi source/busy/split blocker vẫn được giữ và hiển thị.
5. Chỉ retry DEFERRED do execution policy/busy khi plan mới chứng minh đủ điều kiện; không auto retry REVIEW/APPLYING/VERIFYING/unknown outcome. Tác vụ QUEUED/DEFERRED source cũ có thể được hủy theo mode APPLY và plan mới, không xóa journal hay cancel side effect đang chạy. Pause của người dùng áp dụng cả manual/auto; không tự resume.
6. Đồng bộ với AppsViewModel/lifecycle: chỉ auto sau mở khóa và refresh, chống gọi trùng từ onResume/rotation/nút quét; không giữ Activity trong worker hoặc đọc UI từ IO. Khi owner đổi OFF thì không lấy job mới. Thông báo kết quả phân biệt đã kiểm tra/xếp hàng/xử lý/đã xác minh từng task.

**Kiểm tra Q1:** policy default/corrupt/persist, foreground broker khi khóa OFF/ON, runner mất policy ở các ranh giới trước apply, không retry kết quả chưa rõ, concurrent checks/dedup, pause/mode OFF, fresh source/unknown/busy/split, journal recovery. Test/Binder/FSG/OEM nền thật vẫn chưa chạy. Nếu capability background chưa có bằng chứng, UI và STATUS giữ đúng giới hạn foreground, không ghi đạt scheduler nền.

**Trước code Q1:** U1 đã qua kiểm tra hai cấu hình, tổng 210 test. Tick tự động sẽ chạy khi UI unlocked/STARTED và mỗi phút còn hiển thị; không tạo boot/worker. APPLY phải opt-in rõ; CHECK chỉ báo. Foreground policy áp cho cả product runner và đường dispatch shortcut, không chỉ phụ thuộc việc khóa đang OFF/ON. Retry/cancel nguồn cũ chỉ QUEUED/DEFERRED an toàn, giữ REVIEW/in-flight. Các phần source-engine split chưa đạt không được ghi là đã giải quyết từ mode này.

**R1/U1 — bước rà hiệu năng trước chỉnh code:** nhận thấy planner shared lặp quét cả nhóm cho mỗi ID, service/import cũng tìm live ID bằng scan lặp. Plan: group/index một lần mỗi snapshot, cache quyết định source/signer/version/running theo package trong một lần plan, giữ nguyên ưu tiên lỗi/current/busy/coalesce. Không cache qua các lần readback. Thêm test đếm so sánh signer với nhóm lớn để phát hiện chi phí bậc hai, không dùng ngưỡng thời gian dễ flaky. Giữ các assertion nguồn/scope hiện có; kiểm tra hồi quy toàn bộ trước artifact cuối. Đây không thay thế benchmark máy thật.

**R1/S2 — rà quyền document trước chỉnh code:** picker có thể trả content URI từ ứng dụng bên thứ ba. Không chỉ kiểm tra scheme: từ chối authority riêng của Fonesi; trước IO yêu cầu URI DocumentsContract và provider khác UID host để tránh dùng quyền của host ghi nhầm provider SDK nội bộ. Thêm test stage URI nội bộ bị từ chối; giữ test pending URI không tự IO. SAF provider/OEM thật cần kiểm tra trong đợt cuối, không xin thêm quyền storage để bỏ qua lỗi.

**R1/U1 — rà scope incomplete trước chỉnh code:** shared-code preview không được bỏ qua một metadata peer cùng package mà SDK chưa đọc lại được, kể cả peer không chọn. Plan: đối chiếu toàn metadata current engine với live ID/package, giữ mọi nhóm có peer thiếu/đổi danh tính, báo INCOMPLETE_GROUP và chỉ cho nhóm độc lập khác qua. Label của peer chỉ dùng khi package thực sự khớp. Không auto xóa metadata/job không rõ để "sửa"; thêm test unselected peer thiếu và unrelated group vẫn được giữ đúng phạm vi. Đồng thời retry deferred per-clone phải match target chứa ID thay vì lấy đơn nhất theo package.

## R1 — plan rà cuối và giao mã nguồn

1. Đối chiếu lại toàn thể: S1/S2/U1/Q1 chỉ đóng phần cloud có bằng chứng; E1/E2 split, Android mới, runtime/cách ly/tài khoản và nghiệm thu đa máy vẫn mở. Không thay engine bằng một fork chưa audit.
2. Lint mới chỉ ra singleton LocalServices giữ một thuộc tính kiểu Context. Factory đã truyền applicationContext; đổi ràng buộc constructor riêng sang Application để không thể giữ Activity và để công cụ xác minh được. Bỏ biến cục bộ không dùng ở bước retry, giữ nguyên điều kiện chọn target. Không suppress cảnh báo leak hoặc đổi test để thông qua.
3. Chạy lại kiểm tra hai cấu hình sau chỉnh mã. Ghi đúng số test, lỗi/cảnh báo, quyền/component, hash/chữ ký từ artifact cuối; không dùng hash mốc trước.
4. Đồng bộ STATUS/README/PLAN và tài liệu khóa/cấu hình/preview/tự động. Lưu audit E1/E2 với commit và đường code cụ thể, kể cả tag không có source engine. Ghi rõ foreground tự động và phần scheduler nền chưa làm.
5. Đóng gói allowlist 0.5.0, kiểm tra mọi checksum và entry ZIP, kiểm tra source không lẫn binary/khóa. Commit/push source và plan đã được giao, xác minh remote cùng SHA. Không đưa APK debug thành bản phát hành hoặc coi gói thử là bằng chứng chạy điện thoại.

### R1 — rà vòng đời native trước sửa cuối

Đọc ManagedGuestEngine phát hiện hai ranh giới cần chốt ở domain, không chỉ preview UI: registry có thể giữ peer chưa có metadata sau import gián đoạn; listClones loại mục SDK không đọc được. Create/update shared không được dựa riêng danh sách đã lọc đó. Ngoài ra snapshot tạo clone có thể hoàn tất sau khi UI đã khóa/ra nền.

Plan: (1) đọc đầy đủ active registry cùng package và yêu cầu readback từng peer, giữ nhóm có peer thiếu/đang DELETING; dùng cả create/update, không xóa reservation để vượt guard; (2) thêm callback cho quyền bắt đầu SDK mutation, production bind foreground/access, kiểm tra trước tạo snapshot và sau prepare/ngay trước bước đầu SDK, cũng như launch/stop/delete/update; khi đã vào bước tạo user/import thì vẫn hoàn tất readback, không rollback dữ liệu; (3) test peer mất chưa có metadata, marker delete gián đoạn, mất quyền sau prepare, mất quyền trước launch/delete/stop/update và giữ xác minh khi quyền mất sau SDK entry; (4) chạy lại kiểm tra hai cấu hình, thay artifact/bundle cuối bằng bản đã verify. Đây là sửa safety protocol, không mở khả năng split hoặc runtime chưa đạt.

Chi tiết trước chỉnh guard create: sau prepare phải đọc lại shared peers (snapshot có thể lâu), không chỉ auth. Nếu lỗi trước bất kỳ SDK mutation nào nhưng đã reserve user thì đánh dấu reservation DELETED và giữ hàng/ID không tái sử dụng, không xóa user SDK. Khi đã bắt đầu createUser/import vẫn giữ reservation/snapshot uncertain như cũ. Test phân biệt hai ranh giới này.
