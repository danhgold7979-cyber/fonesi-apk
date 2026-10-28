# Fonesi APK — E32 source/provenance gate complete / E33 source-acquisition decision next (29/10/2026)

**Latest verified technical milestone E29 PASS:** [workflow #40731817711](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40731817711), source SHA `10fc6b623a42b93e5fb62601b2f849c82b1fd471`. This proves signed, project-owned synthetic component dispatch only. The receipt keeps real guest Activity registration, third-party component execution, kernel UID isolation and production clone acceptance false. [E29 results](phases/E29_PRIVATE_COMPONENT_DISPATCH_RESULTS.md).

**E30 decision:** pinned SpaceCore is not accepted for production split APKs, 16 KB native compatibility, real guest component lifecycle or per-clone account isolation. [E30 matrix](phases/E30_PROVIDER_FEASIBILITY_RESULTS.md).

**E31 decision:** no existing inspected alternative passed the minimum source/license/security gate. [E31 results](phases/E31_PROVIDER_SHORTLIST_RESULTS.md).

**E32 complete — NO-GO on current source/provenance evidence.** New candidate research found that WaxMoon/MultiApp's public API describes directory-based split installation, but its engine delegates to opaque `moon.jar` and the repository says it is no longer maintained. ServenScorpion/VirtualApp includes an opaque `lib-release.aar` without a verified root license/provenance package. BlackBox/NewBlackbox forks still have known cluster/resource/security/provenance gaps. No candidate binary was imported or executed. [E32 results](phases/E32_CANDIDATE_SOURCE_PROVENANCE_RESULTS.md).

**Next: E33 — source-acquisition decision gate.** Seek full source/build inputs and native provenance for the strongest leads, including licensing review; if those cannot be obtained, maintain NO-GO and assess a first-party or properly licensed engine architecture. No production provider switch until source, license, split/component and isolation requirements can be independently verified.

**E26/E27/E28 PASS:** [E26 #40690951828](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40690951828), [E27 #40719612538](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40719612538), [E28 #40727455497](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40727455497).

**Product readiness remains ~30% estimated (28.00% weighted rubric), not measured.** The original plan remains 8 product phases / 32 steps. Real third-party clone acceptance remains 0 applications. Technical milestone count is not product completion. See [progress rubric](PRODUCT_PROGRESS.md).

`main` remains at `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d`; cumulative work remains on `phase/e21-android-split-code-proof-20261028`; PR #12 remains open. Do not merge this technical branch as a finished product.



# Fonesi APK — E25 component preflight PASS / E24 lifecycle race fixed (28/10/2026)

**Latest verified technical source SHA:** `d911ee266a92a0c85ec368dbd8df77c0dba6af62`. All five technical CI workflows passed on this exact commit:
- [E21 #40670347156](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670347156) — **SUCCESS**
- [E22 #40670346768](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346768) — **SUCCESS**
- [E23 #40670346702](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346702) — **SUCCESS**
- [E24 #40670346892](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346892) — **SUCCESS**
- [E25 #40670346860](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40670346860) — **SUCCESS**

The E24/E25 failure was diagnosed from actual Android receipts: AOSP34 changed Activity configuration while the test-only private loader was preparing. The runner now waits for stable display/locale values and the synthetic host handles expected configuration changes. Final receipts show the full lifecycle and an attached private View. See [E24 results](phases/E24_SYNTHETIC_SCREEN_RESULTS.md) and [E25 results](phases/E25_PRIVATE_COMPONENT_PREFLIGHT_RESULTS.md).

**Product completion remains ~30% (heuristic 28.00%), not measured or release-ready.** E21–E25 are synthetic runtime tests, not real third-party clone acceptance. The actual guest Activity remains unregistered; process isolation remains same-UID; no social accounts/Samsung ARM64/release APK have passed. The production candidate remains single-APK with `importsSplitApks=false`. The product master plan remains **8 phases / 32 steps**; Phase 1/2 engine/device acceptance is still blocked. Next technical milestone: **E26**, safe durable journal recovery when Fonesi is closed, without background engine initialization or APK import. See [E26 plan](phases/E26_BACKGROUND_JOURNAL_RECOVERY_PLAN.md).

`main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` is unchanged. The cumulative source remains in Draft PR #12; do not merge it as a finished product.

---

# Fonesi APK — E24 signed synthetic Android screen proof PASS (28/10/2026)

**Technical E24 PASS** on source `66cd3a1d79fdd505b852db15f6b6213f8d0681a5`: Android 34 host Activity displayed an attached View created from signed private project-owned DEX and relayed the entire CREATE→DESTROY transcript. [E24 CI #40660505892](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505892) **SUCCESS**. E21 [#40660505783](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505783), E22 [#40660505960](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505960), E23 [#40660505786](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40660505786) also **SUCCESS** at this exact code SHA. [E24 detailed results](phases/E24_SYNTHETIC_SCREEN_RESULTS.md).

**NOT a real guest Activity registration or production virtualized package.** Same host UID, no third-party account, actual guest Activity/Service/Provider/PackageManager/Binder, Play Services, ARM64 Samsung, session update or release acceptance. SpaceCore candidate remains single APK, `importsSplitApks=false`. [Weighted product readiness](PRODUCT_PROGRESS.md) remains estimated **~30%**, and the original 8-phase/32-step roadmap is unfinished. Keep test-only PR in Draft; `main` unchanged.

---

# Fonesi APK — technical E23 PASS / product ~30% estimate (28/10/2026)

**Verified technical milestone E23:** [Android 34 CI #40658194704](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40658194704) **SUCCESS** on code `e9be7c7cc211df80c27d7c811d1463244ab4e4db`: signed private 4-member base/alpha/beta/French-alpha-config APK, correct `configForSplit` attachment, dependent DEX execution and locale resource override. E21 [#40658194522](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40658194522) and E22 [#40658194520](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40658194520) regressions also **SUCCESS** on exactly the same code. [Detailed E23 results](phases/E23_SIGNED_CONFIG_SPLIT_RESULTS.md).

**User application readiness: approximately 30% estimated, not measured or release-ready.** The [explicit weighted rubric](PRODUCT_PROGRESS.md) totals 28.00%, rounded for user communication; it does NOT advance for a synthetic Android proof alone. Mandatory real third-party clone/login and Samsung acceptance is **zero accepted**, not a working production feature. SpaceCore `0.5.0-candidate` still imports only a single APK under ARM64/API26–34/4 KB guard. Real guest lifecycle, multi-account safety, notifications, Play Services, signed release and device testing remain open. Main unchanged; technical work is in stacked Draft PR #12.

---

# Fonesi APK — E22 Android synthetic dependent-feature proof (28/10/2026)

**E22 TECHNICAL CI PASS, NOT A COMPLETE PRODUCT:** [E22 Actions #40655822440](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40655822440) passed on exact source `1ec2b4d0271da1b5a9a77c00112b88086fe81a16`. On fresh AOSP34 x86_64, three project-owned signed private APKs (`base → feature.alpha → feature.beta`) were installed, audited, and exercised: beta bytecode really invoked alpha class through its parent `DexClassLoader`, and scoped resource markers resolved only from private base/alpha/beta paths. Strict SHA-256, UID/process receipt and no-account checks passed. [E21 regression #40655822472](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40655822472) also passed on the same source. [E22 results](phases/E22_DEPENDENT_ANDROID_SPLITS_RESULTS.md) record exact evidence.

**Still NOT VERIFIED:** arbitrary third-party split/config APKs, real guest Android Application/Activity/Service/Provider, Play Services, accounts/sessions, strong UID isolation, Samsung ARM64, release signing, update persistence or production multi-account compatibility. The `0.5.0-candidate` SpaceCore provider still rejects split imports and the separate E22 probe remains test-only. `main@c055df6` is unchanged. The 8-phase/32-step master plan remains unfinished; keep the accumulated technical PR in Draft.

---

# Fonesi APK — E21 signed isolated synthetic split Android proof (28/10/2026)

**E21 TECHNICAL GATE PASS, NOT PRODUCT COMPLETE:** [GitHub Actions 40653400751](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40653400751) ended **success** on source `2714148230815b90b57037e2aec0d4fa4a1271bc`. A new AOSP Android 34 x86_64 KVM emulator installed independently signed base + feature APKs from the project-owned fixture, executed separately loaded **private** DEX code, verified feature resources and immutable private readback. Source/APK tests, x86_64 probe compile/lint, exact permission/component audit and receipts also passed. [E21 results](phases/E21_SPLIT_ANDROID_RESULTS.md) document exact limits. 

**NOT VERIFIED:** arbitrary real-world split APKs, Android guest Activity/Service/Provider lifecycle, Play Services, UID-level isolation, social app accounts, ARM64/Samsung or release packaging. `0.5.0-candidate` is still SpaceCore **single-APK ARM64/API26–34/4 KB**; no release product flag is enabled. `main@c055df6` is unchanged; E21 work is in [draft PR #12](https://github.com/danhgold7979-cyber/fonesi-apk/pull/12) stacked on E20. The project's 8-phase/32-step master roadmap is **not finished**.

---

# Fonesi APK — trạng thái tích hợp E19 (28/10/2026)

**Master plan:** [8 phase / 32 bước](ROADMAP.md). Các mốc E10–E19 bên dưới là *lát cắt kỹ thuật* thuộc master plan, không có nghĩa toàn bộ 8 phase sản phẩm đã hoàn thành. Các thay đổi tích lũy có trên nhánh [phase/e19-clone-style-metadata-20261028](https://github.com/danhgold7979-cyber/fonesi-apk/tree/phase/e19-clone-style-metadata-20261028), kế thừa E18→E17→...→E10. `main@c055df6` chưa được merge/chỉnh bởi chuỗi này.

## Cổng kiểm chứng đã có bằng chứng

| Mốc | Bằng chứng CI chính xác | Điều được chứng minh |
| --- | --- | --- |
| E11 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977) | Nạp DEX/resources private từ APK ký thật, ứng dụng mẫu |
| E12 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641518802) | Gọi bytecode mẫu qua classloader private |
| E13 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40641656320) | File/SQLite mẫu A/B ở tiến trình khác nhau, cùng Android UID |
| E14 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40643847891) | Scoped Context **chỉ dành cho mẫu thử**: file/preferences/SQLite |
| E15 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40644938313) | Kênh Binder nội bộ và PID/UID/nonce |
| E16 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40646052743) | Binder khởi chạy bytecode từ APK private của mẫu |
| E17 | [Android 34 PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40647059820) | Attach/onCreate Application **mẫu** bằng API công khai |
| E18 | [CI tích hợp PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40647494054) | 299 test app/shared, cloud/candidate/source-native, gói debug ZIP 7 tệp/hash |
| E19 | [CI đầu PASS](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40648215912) | 307 test app/shared, Room 3→4, màu/huy hiệu, build/lint/candidate/bundle. Schema 4 đúng KSP đã commit sau lượt CI này; HEAD mới phải tái kiểm tra. |

Chi tiết E19: [plan](phases/E19_CLONE_STYLE_PLAN.md), [results](phases/E19_RESULTS.md). Các kết quả trên là từ **Android Emulator AOSP34 x86_64** hoặc test cloud, không phải Samsung/ARM64 thực tế dùng tài khoản.

## Cổng sản phẩm chưa hoàn thành

- **Engine sản phẩm:** SDK SpaceCore candidate vẫn chỉ nhập một APK hoàn chỉnh trên ARM64/API26–34/4KB. Không import split đầy đủ; source engine E11–E17 chưa tích hợp vào APK chính như provider chạy được Activity/Service/Provider của app bất kỳ.
- **Cách ly tài khoản:** ContextWrapper và nhiều process dưới *cùng UID* không tạo kernel/UID sandbox. Facebook/Messenger/Zalo/TikTok/Threads/X/Shopee/YouTube chưa có nghiệm thu đăng nhập, dữ liệu, thông báo, Play Services, cập nhật giữ phiên.
- **Thiết bị và phát hành:** chưa thử ARM64/One UI, hãng khác, overnight/performance, cài nâng cấp và khóa ký release. Không tuyên bố debug test ZIP là APK phát hành.
- **UI/khóa:** style mới đã qua Robolectric/Room; màn hình launcher/hardware auth/SAF thực còn chờ kiểm tra trên máy. Background worker đóng app vẫn chưa có bằng chứng/triển khai.

---

## Mốc nền tảng 0.5.0/E9 — báo cáo lịch sử trước E10

# Trạng thái thực hiện — 0.5.0

**Đã hoàn thành thêm phần cloud của S1/S2/U1/Q1; sản phẩm clone đa ứng dụng chưa đạt 100%.** Các phần khóa, cấu hình, chọn nhiều, preview và tự động khi đang mở đã có code và kiểm tra. SDK candidate vẫn chỉ nhập một APK, giới hạn ARM64/API 26–34/trang 4 KB; chưa thực thi được xác minh trên điện thoại. Thử thiết bị được gom về đợt sau theo yêu cầu, không chặn phát triển phần độc lập và không biến runtime chưa chạy thành đạt.

Plan trước từng phần và điều chỉnh: [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Bằng chứng hiện tại: [GUEST_CODE_VALIDATION.md](GUEST_CODE_VALIDATION.md); báo cáo [E4](APK_SET_VALIDATION.md)/[E6–E8](SOURCE_ENGINE_VALIDATION.md) là mốc lịch sử. Giữ nguyên lộ trình [8 phase/32 bước](ROADMAP.md).

**E9:** code image loader dùng private DEX/native/resource paths đã compile/lint; 309 test đạt (284 app/shared + 25 source JVM), không lỗi/bỏ qua. Có guard dedicated process/native ENABLED/thread IO/một attempt, chặn isolatedSplits chưa hỗ trợ, readback trước trả handle. Chưa có guest Context/Application/Activity/Binder, custom resource classloader hoặc provider đầy đủ. ART/resources thực thi Android chưa chạy, không có clone/tài khoản được chứng minh. Candidate vẫn SpaceCore/single APK; gói debug hiện tại có checksum E9.

**E6/E7/E8:** 283 test cho app/shared code + 18 test ELF/native extraction = 301 test đạt, không lỗi/bỏ qua. Verifier đã chuyển sang apk-runtime dùng chung; kho code private publish/readback toàn base/split và native preparer đã có thực thi filesystem/JVM. Native ARM64/x86_64 build/static audit 16 KB đạt. Đây chưa phải classloader/resources/Binder/lifecycle Android hoặc adapter source hoàn chỉnh. E6 boot Google/AOSP và reuse đều chưa chạy probe; framework AOSP có DeadSystemException. Đã xác nhận dừng máy ảo sở hữu sau khi dừng mềm không thoát. Không dùng lỗi hạ tầng để kết luận tương thích social app. SDK SpaceCore vẫn giữ capability split false.

**E5:** module native source riêng đã build ARM64/ELF 16 KB, không quyền/component, có kiểm tra thực thi libc routing trong hai subprocess Linux và giữ marker/output riêng. Không nối vào APK chính, không phải Android guest runtime. [Báo cáo E5](SOURCE_NATIVE_VALIDATION.md) ghi input/patch/license/hash và phần còn thiếu. SDK SpaceCore, dữ liệu guest và capability split của bản app hiện tại chưa thay đổi.

**Cập nhật E3/E4 ngày 27/10/2026:** đã khảo sát thêm ba source engine, chưa có ứng viên đạt cổng split/runtime. Đã bổ sung kiểm tra mật mã chữ ký và binary manifest của toàn bộ bộ APK, gồm version 64-bit, base/feature/config, dependency/cycle và required split types, bằng apksig pin. Candidate có preflight chỉ đọc trước tạo user và audit lại trước import một APK, ràng buộc private copy bằng hash đã kiểm. Verifier hiểu cluster không có nghĩa SpaceCore nạp được split; capability vẫn false. Kết quả mới trong [APK_SET_VALIDATION.md](APK_SET_VALIDATION.md); mốc PHASE6_VALIDATION là lịch sử nguồn a1d70d2.

| Phase/bước | Trạng thái | Bằng chứng/phần còn thiếu |
| --- | --- | --- |
| 0, bước 1–4 | Đạt phần cloud | Nền tảng, toolchain pin, build/audit tái tạo và baseline. |
| 1, bước 5–7 | Có SDK ứng viên được pin và audit build | Probe riêng ngoại tuyến; candidate tích hợp có opt-in, permission allowlist và guard component chữ ký. Chưa chốt engine sản phẩm; SDK mã đóng chưa audit toàn bộ. |
| 1, bước 8 | Adapter thật, runtime chưa nghiệm thu | Tạo/mở/dừng/xóa/cập nhật đã gọi SDK. Guard single APK/ARM64/API 26–34/4 KB; target 36 vẫn giữ. |
| 2, bước 9–12 | Chưa chạy thiết bị | Payload/cách ly/tài khoản/One UI phải thử thật trước nghiệm thu. Không suy ra thành công từ test double. |
| 3, bước 13–16 | Có code/kiểm tra cloud | Registry bền vững, Room schema 3/migration, user không tái sử dụng, launch-pending, đối chiếu và shortcut UUID không mở app gốc thay thế. Vòng đời/launcher/guest thật chưa thử. |
| 4, bước 17–20 | Có điều phối và tự động foreground | Snapshot/journal/CAS/lock/pause/recovery; preview/confirm một/tập chọn/tất cả, atomic enqueue và shared peer guards. Auto OFF mặc định, CHECK hoặc APPLY khi mở/đã mở khóa; không có worker nền độc lập. Import thật/giữ phiên chưa chứng minh. |
| 5, bước 21–24 | Bị chặn một phần bởi engine | Split APK chưa được nhập, Android mới/16 KB chưa đạt; các app bắt buộc vẫn còn trong phạm vi. E1/E2 ghi tại [ENGINE_SPLIT_AUDIT.md](ENGINE_SPLIT_AUDIT.md); không nhập fork có log/spoof/crash fallback chưa audit. |
| 6, bước 25–28 | Đã triển khai thêm; chưa nghiệm thu toàn phase | Tìm/lọc/chọn nhiều, tên/nhóm, quyền, dung lượng/pin, khóa hệ thống và backup cấu hình đã có cloud tests. Tùy biến màu/huy hiệu riêng chưa triển khai; hardware auth/SAF/đo thiết bị thật còn chờ. |
| 7, bước 29–32 | Có gói debug kiểm tra, chưa phát hành | Audit APK và bundle allowlist/checksums. Chưa đo độ ổn định/tài nguyên đa hãng, thử nâng cấp/cài thật hoặc ký bản release. |

## Bản tạo ra

- `vn.fonesi.app`, `0.5.0-cloud`: không SDK/Internet; có quản lý và các luồng bảo vệ/cấu hình/preview. Không tạo clone khi engine unavailable.
- Cùng ID `0.5.0-candidate`: SDK thật, khởi tạo bật chủ động; bản debug chưa nghiệm thu. Không cài song song với cloud. Không đưa debug signer thành khóa phát hành.
- `vn.fonesi.probe`: probe riêng chỉ dùng payload do dự án kiểm soát; mặc định chưa chủ động init SDK.
- `vn.fonesi.payload` r1/r2: không quyền, dữ liệu mẫu riêng để thử cách ly và cập nhật giữ ID/nhãn trong đợt sau.
- `vn.fonesi.nativeprobe`: APK riêng 0 quyền/Internet, hai service nội bộ ở process khác nhau để thử JNI/file/SQLite mẫu. Build/static audit đạt; chưa có kết quả Android execution. Không nằm trong bundle điện thoại.

Gói hiện tại: `app/build/outputs/fonesi-0.5.0-phone-test.zip`; quy trình [PHONE_TEST_GUIDE.md](PHONE_TEST_GUIDE.md). Chưa yêu cầu cài ngay. Gỡ Fonesi có thể mất toàn bộ vùng clone; backup tên/nhóm không chứa dữ liệu đăng nhập.

## Điều kiện còn mở

1. Engine phải thật sự lưu/sử dụng/update cả base/split, đủ metadata/resources/classloader/native libs và readback; không thể giải quyết bằng chỉ copy/merge/re-sign APK.
2. Phải xác minh Android/ABI/page size, target 36 và luồng khởi tạo/chạy nền của engine. SM-A525F là máy thử đầu, không phải giới hạn model của sản phẩm; Android đang cài chưa xác nhận.
3. Payload, cách ly, đăng nhập, thông báo/media/Play Services, update giữ phiên và nhiều ứng dụng phải có kết quả thiết bị. Không hứa tránh ban, không văng hoặc vô hạn tiến trình đồng thời.
4. Worker nền độc lập, màu/huy hiệu riêng, ký/nâng cấp release và nghiệm thu đa hãng/hiệu năng còn thiếu. Không đóng các yêu cầu này bằng test giao diện.

Nếu đổi provider phải namespace/đối chiếu và thiết kế migration riêng, không chạy journal của engine cũ qua engine mới. Không tự xóa guest/metadata/journal để làm kết quả có vẻ đúng.
