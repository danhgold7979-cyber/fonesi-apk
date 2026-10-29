# Fonesi

Ứng dụng Android dự kiến quản lý nhiều bản clone của các ứng dụng trên điện thoại, ưu tiên mạng xã hội, không yêu cầu root.

**Trạng thái mới nhất (29/10/2026):** E39 Room ownership registry đã PASS cloud/candidate CI ([#40803931491](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40803931491), 334 test mỗi gate). E40 đang tích hợp registry với metadata clone; chưa chứng minh split import hoặc cách ly tài khoản thật. Tiến độ sản phẩm theo rubric vẫn 28% (~30%), không phải phần trăm E hoàn thành.

Kế hoạch sản phẩm và các điều kiện kỹ thuật nằm trong [docs/PLAN.md](docs/PLAN.md).

Lộ trình chi tiết: [8 phase, 32 bước và 4 mốc thử điện thoại](docs/ROADMAP.md).

Theo dõi [phase hiện tại](docs/STATUS.md) và [quy luật code/test](docs/CODE_TEST_RULES.md).

Ngày 27/10/2026, bước E4 bổ sung kiểm tra chữ ký mật mã/binary manifest của APK và tính nhất quán bộ split trước import: [bằng chứng](docs/APK_SET_VALIDATION.md). Đây là kiểm tra đầu vào; engine candidate vẫn chưa nhập được split. Plan và kết quả khảo sát thêm engine được lưu cùng source.

E5 đã build [native runtime riêng từ source](docs/SOURCE_NATIVE_VALIDATION.md), có libc routing chạy thật trong hai subprocess host và AAR ARM64 align 16 KB. Module chưa nối vào app chính, chưa chạy trên Android hoặc thành engine split. Build riêng bằng `python3 scripts/prepare_source_engine.py --install-tools` rồi `python3 scripts/check_source_engine.py`; build thường không include module.

E7/E8 đã bổ sung kho private giữ đủ base/split và bộ chuẩn bị thư viện native theo ABI/4 KB/16 KB; **301 test** đạt ở các tầng cloud/JVM, build/lint/audit hai bản app và component riêng đạt. [Báo cáo hiện tại](docs/SOURCE_ENGINE_VALIDATION.md) có hash APK/bundle và kết quả E6: máy ảo Android chưa chạy được probe, engine Java/Binder/classloader/lifecycle còn thiếu. Các component mới chưa thay SpaceCore hoặc mở capability split trong app.

E9 tiếp tục thêm code image loader DEX/tài nguyên private và guard isolatedSplits. **309 test** và build/lint/audit đạt; [báo cáo E9](docs/GUEST_CODE_VALIDATION.md) là mốc artifact hiện tại. Code loader chưa thực thi trên Android, chưa có guest Context/Application/Activity/Binder hoặc provider đầy đủ. Không nhận là đã chạy được app clone hoặc chỉ còn thử điện thoại.

**Trạng thái `0.5.0`:** đã bổ sung khóa quản lý/shortcut bằng xác thực hệ thống, xuất/nhập tên và nhóm, tìm/lọc/chọn nhiều bản, preview phạm vi cập nhật và tự kiểm tra/cập nhật tuần tự khi Fonesi đang mở và đã mở khóa. Build mặc định `cloud` không chứa SDK; build `candidate` nối SDK thật nhưng chỉ nhận một APK đầy đủ, ARM64/API 26–34/trang 4 KB và phải bật chủ động. Split APK, Android mới và khả năng chạy/cách ly tài khoản thật vẫn chưa đạt nghiệm thu. Không gọi đây là sản phẩm đã hoàn thiện 100%. Thử điện thoại được gom về đợt sau theo yêu cầu; theo dõi [STATUS.md](docs/STATUS.md).

Không dùng lối tắt mở app gốc hoặc trang web để giả lập một bản clone có tài khoản độc lập.

## Chức năng hiện có

- Quét các ứng dụng có màn hình mở trong hồ sơ Android hiện tại; tìm kiếm, lọc nhóm ứng dụng ưu tiên và xem chi tiết.
- Đọc phiên bản, chứng thư ký và các đường dẫn APK base/split để chuẩn bị nguồn cập nhật. Không xuất danh sách này lên máy chủ.
- Planner/runner cập nhật: phân biệt phạm vi từng bản/dùng chung, sao chép nguồn base/split, chống trùng, OS lock, journal, pause và khôi phục không tự chạy lại kết quả chưa rõ.
- Room lưu metadata theo engine/clone ID và lịch sử hàng đợi; có các tab Bản clone/Cập nhật, sửa tên/nhóm, pause, hủy yêu cầu chưa chạy. Không seed dữ liệu clone giả.
- Mở/ghim shortcut qua UUID theo lần tạo; kiểm tra metadata/engine/cập nhật trước dispatch, đồng bộ tên và vô hiệu hóa mục đã xóa. Có migration Room 1 → 2 giữ dữ liệu. Xem [SHORTCUTS.md](docs/SHORTCUTS.md); chưa thử launcher hoặc guest thật.
- Candidate: registry Room schema 3, snapshot trước tạo, mã user không tái sử dụng, launch-pending bền vững, đối chiếu guest, tạo thêm bản sạch, dừng/xóa có xác nhận và nút chạy hàng đợi từng nhóm. Kiểm tra archive/chữ ký và bản APK thuộc kho riêng; không dùng dữ liệu app gốc làm readback giả.
- Chặn thao tác clone khi engine chưa có; không tạo hồ sơ giả hoặc báo thành công giả.
- Tìm bản clone không phân biệt dấu tiếng Việt, lọc nhóm/trạng thái, chọn các mục đang hiển thị và cập nhật một/tập chọn/tất cả qua cùng preview. Xác nhận đọc lại nguồn/phạm vi trước khi xếp hàng, ghi batch trong một transaction.
- [Khóa giao diện](docs/ACCESS_CONTROL.md) mặc định tắt; sinh trắc mạnh hoặc mã khóa Android. Manager và shortcut cần mở khóa trước dispatch, không lưu PIN/phiên xác thực hoặc gọi UI lock là mã hóa guest.
- [Xuất/nhập cấu hình](docs/CONFIGURATION.md) qua Android document picker, chỉ tên/nhóm/package/ref. JSON nghiêm ngặt tối đa 2 MiB, preview và map rõ bản nhận, transaction không ghi một phần; không backup tài khoản hoặc tạo guest từ file.
- [Tự động khi đang mở](docs/UPDATE_AUTOMATION.md): mặc định tắt; chọn chỉ kiểm tra hoặc cập nhật có đồng ý. Dùng cùng journal/queue, giữ pause, không bắt đầu import khi UI ra nền/khóa, không tự retry kết quả chưa rõ.
- Chẩn đoán API/ABI/page size, dung lượng trống/thống kê chính Fonesi và tối ưu pin; số không đọc được ghi chưa biết. Không xin Usage Access hoặc quyền đọc toàn bộ tệp.
- Build cloud không có Internet/SDK quảng cáo. Candidate có Internet, camera/mic và quyền thông báo/foreground để thử app khách; SDK mã đóng chưa được audit toàn bộ. Tắt sao lưu tự động/truyền dữ liệu hệ thống.

Scheduler chạy độc lập khi Fonesi ở nền chưa triển khai; mode tự động hiện tại chỉ chạy khi có UI đang mở/đã mở khóa. Chưa chứng minh SDK giữ phiên trên thiết bị. [CLOUD_BACKEND.md](docs/CLOUD_BACKEND.md) mô tả hợp đồng nền; [INTEGRATED_CANDIDATE.md](docs/INTEGRATED_CANDIDATE.md) mô tả adapter và [ENGINE_SPLIT_AUDIT.md](docs/ENGINE_SPLIT_AUDIT.md) ghi blocker engine. Quy trình plan trước mỗi phase/bước nằm trong [EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md).

Build candidate và chuẩn bị gói thử cho đợt sau:

```bash
./scripts/check.sh --with-engine-candidate
python3 scripts/build_phone_bundle.py
```

APK có SDK: `app/build/outputs/candidate/fonesi-0.5.0-candidate.apk`. Gói payload 1/2 và hướng dẫn: `app/build/outputs/fonesi-0.5.0-phone-test.zip`. [Báo cáo E4 hiện tại](docs/APK_SET_VALIDATION.md) ghi test/build/lint/audit hai cấu hình; runtime chưa xác minh. Báo cáo PHASE6_VALIDATION là mốc 26/10 trước E4.

## Build trong môi trường cloud

Dùng checkout hiện tại; không cần Git worktree. Script bootstrap dành cho Linux x86_64, cài công cụ dưới `/workspace/.fonesi-tools` thay vì sửa thư mục hệ thống. Có thể đổi vị trí bằng `FONESI_TOOLS_DIR`.

```bash
cd /workspace/fonesi-apk
python3 scripts/bootstrap_android.py --accept-sdk-license
./scripts/check.sh
```

Tham số `--accept-sdk-license` chấp nhận các giấy phép Android SDK. Script kiểm tra checksum của JDK, command-line tools và Gradle; SDK Manager tải các package SDK bằng cơ chế kiểm tra của Google. Không tắt TLS verification. Các bước đã chạy được trong cloud; script có thể chạy lại để kiểm tra/cài bổ sung.

Toolchain: Temurin JDK 21.0.13+9, Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.1.21, SDK/Build Tools 36. APK nền tảng khai báo Android 8.0+; đây không phải tuyên bố engine clone đã hỗ trợ các phiên bản Android đó.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Kết quả unit test: `core/build/reports/tests/test/index.html`. Lint: `app/build/reports/lint-results-debug.html`.

Kết quả SQLite/Robolectric: `app/build/reports/tests/testDebugUnitTest/index.html`. Tổng hợp chữ ký/quyền/artifact: `app/build/reports/cloud-verification.json`. `check.sh` giữ exit status của bước lỗi và từ chối kết quả không có test thực sự; không chạy thử điện thoại.

Trên máy phát triển khác, cài JDK 21 và SDK 36, cấu hình `ANDROID_HOME` hoặc `sdk.dir` trong `local.properties`, rồi chạy `./gradlew :core:test :apk-runtime:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Module `apk-runtime` dùng SDK/JDK này để tạo APK ký thật cho test trong build output. Không đưa keystore phát hành hoặc thông tin tài khoản vào Git. Khóa debug không dùng để phát hành.

## Giới hạn kiểm chứng

Unit test kiểm tra planner/runner/filesystem và protocol adapter; Robolectric kiểm tra Room/registry với SQLite native, xác thực, cấu hình, update preview/automation và định tuyến/shortcut mô phỏng trên API 26/34. Không test nào chạy SDK ARM/Facebook/Zalo/TikTok hoặc chứng minh cách ly tài khoản. Kiểm thử điện thoại được hoãn theo yêu cầu; vẫn cần thực hiện trước nghiệm thu runtime. Các báo cáo 0.1–0.4 là mốc lịch sử; báo cáo hiện tại là [PHASE6_VALIDATION.md](docs/PHASE6_VALIDATION.md).

## Phase 1: build bộ thử engine riêng

Đọc [audit engine](docs/PHASE1_ENGINE_AUDIT.md) và [thông tin SDK mã đóng](third_party/spacecore/NOTICE.md) trước khi dùng bản thử. Build cloud không chứa SDK; candidate tích hợp và probe riêng có SDK. Không dùng tài khoản thật trước khi payload/cách ly đạt trên thiết bị.

```bash
python3 scripts/fetch_engine_sdk.py
python3 scripts/generate_probe_manifest.py --check
./scripts/check.sh --with-engine-probe
```

Script `--check` không sửa source. Chỉ dùng chế độ không có `--check` khi chủ động phát triển overlay và kiểm tra thay đổi. Build thường không có `includeEngineProbe` không tải hoặc liên kết SDK ứng viên.

Hai APK nằm dưới `engine-probe/build/outputs/apk/debug/` và `probe-payload/build/outputs/apk/debug/`. Cài/thử theo [checklist phase 1](docs/PHASE1_PHONE_TEST.md). Phải thử thành công dữ liệu mẫu trước khi dùng tài khoản mạng xã hội ở phase 2.
