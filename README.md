# Fonesi

Ứng dụng Android dự kiến quản lý nhiều bản clone của các ứng dụng trên điện thoại, ưu tiên mạng xã hội, không yêu cầu root.

Kế hoạch sản phẩm và các điều kiện kỹ thuật nằm trong [docs/PLAN.md](docs/PLAN.md).

Lộ trình chi tiết: [8 phase, 32 bước và 4 mốc thử điện thoại](docs/ROADMAP.md).

Theo dõi [phase hiện tại](docs/STATUS.md) và [quy luật code/test](docs/CODE_TEST_RULES.md).

**Trạng thái `0.4.0`:** build mặc định `cloud` không chứa SDK; build `candidate` đã nối SDK thật cho tạo/mở/dừng/xóa và cập nhật trong cùng giao diện. Candidate chỉ nhận một APK đầy đủ, ARM64/API 26–34/trang 4 KB và phải bật chủ động. Chưa có runtime/đăng nhập/cách ly trên điện thoại; chưa hoàn thiện đa app split, sinh trắc học, sao lưu hay phát hành. Kiểm thử điện thoại được gom về đợt sau theo yêu cầu. Chi tiết [INTEGRATED_CANDIDATE.md](docs/INTEGRATED_CANDIDATE.md).

Không dùng lối tắt mở app gốc hoặc trang web để giả lập một bản clone có tài khoản độc lập.

## Chức năng hiện có

- Quét các ứng dụng có màn hình mở trong hồ sơ Android hiện tại; tìm kiếm, lọc nhóm ứng dụng ưu tiên và xem chi tiết.
- Đọc phiên bản, chứng thư ký và các đường dẫn APK base/split để chuẩn bị nguồn cập nhật. Không xuất danh sách này lên máy chủ.
- Planner/runner cập nhật: phân biệt phạm vi từng bản/dùng chung, sao chép nguồn base/split, chống trùng, OS lock, journal, pause và khôi phục không tự chạy lại kết quả chưa rõ.
- Room lưu metadata theo engine/clone ID và lịch sử hàng đợi; có các tab Bản clone/Cập nhật, sửa tên/nhóm, pause, hủy yêu cầu chưa chạy. Không seed dữ liệu clone giả.
- Mở/ghim shortcut qua UUID theo lần tạo; kiểm tra metadata/engine/cập nhật trước dispatch, đồng bộ tên và vô hiệu hóa mục đã xóa. Có migration Room 1 → 2 giữ dữ liệu. Xem [SHORTCUTS.md](docs/SHORTCUTS.md); chưa thử launcher hoặc guest thật.
- Candidate: registry Room schema 3, snapshot trước tạo, mã user không tái sử dụng, launch-pending bền vững, đối chiếu guest, tạo thêm bản sạch, dừng/xóa có xác nhận và nút chạy hàng đợi từng nhóm. Kiểm tra archive/chữ ký và bản APK thuộc kho riêng; không dùng dữ liệu app gốc làm readback giả.
- Chặn thao tác clone khi engine chưa có; không tạo hồ sơ giả hoặc báo thành công giả.
- Build cloud không có Internet/SDK quảng cáo. Candidate có Internet, camera/mic và quyền thông báo/foreground để thử app khách; SDK mã đóng chưa được audit toàn bộ. Tắt sao lưu tự động/truyền dữ liệu hệ thống.

Runner đã nối vào adapter candidate, còn scheduler tự chạy nền chưa có: người dùng bấm chạy và giữ Fonesi mở. Chưa chứng minh SDK cập nhật giữ dữ liệu trên thiết bị. Sinh trắc học và sao lưu thủ công chưa có. [CLOUD_BACKEND.md](docs/CLOUD_BACKEND.md) mô tả hợp đồng nền; [INTEGRATED_CANDIDATE.md](docs/INTEGRATED_CANDIDATE.md) mô tả tích hợp hiện tại.

Build candidate và chuẩn bị gói thử cho đợt sau:

```bash
./scripts/check.sh --with-engine-candidate
python3 scripts/build_phone_bundle.py
```

APK có SDK: `app/build/outputs/candidate/fonesi-0.4.0-candidate.apk`. Gói payload 1/2 và hướng dẫn: `app/build/outputs/fonesi-0.4.0-phone-test.zip`. [123 test và audit hai bản](docs/RUNTIME_INTEGRATION_VALIDATION.md) đã đạt trong cloud; runtime chưa xác minh.

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

Trên máy phát triển khác, cài JDK 21 và SDK 36, cấu hình `ANDROID_HOME` hoặc `local.properties`, rồi chạy `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Không đưa keystore phát hành hoặc thông tin tài khoản vào Git. Khóa debug không dùng để phát hành.

## Giới hạn kiểm chứng

Unit test kiểm tra planner/runner/filesystem và protocol adapter; Robolectric kiểm tra Room/registry với SQLite native và định tuyến/shortcut mô phỏng trên API 26/34. Không test nào chạy SDK ARM/Facebook/Zalo/TikTok hoặc chứng minh cách ly tài khoản. Kiểm thử điện thoại được hoãn theo yêu cầu; vẫn cần thực hiện trước nghiệm thu runtime. Xem [mốc cloud ban đầu](docs/CLOUD_VALIDATION.md), [mốc shortcut](docs/PHASE3_VALIDATION.md) và [mốc tích hợp 0.4.0](docs/RUNTIME_INTEGRATION_VALIDATION.md).

## Phase 1: build bộ thử engine riêng

Đọc [audit engine](docs/PHASE1_ENGINE_AUDIT.md) và [thông tin SDK mã đóng](third_party/spacecore/NOTICE.md) trước khi dùng bản thử. SDK không được đưa vào app chính. Không dùng tài khoản thật trong hai APK thử.

```bash
python3 scripts/fetch_engine_sdk.py
python3 scripts/generate_probe_manifest.py --check
./scripts/check.sh --with-engine-probe
```

Script `--check` không sửa source. Chỉ dùng chế độ không có `--check` khi chủ động phát triển overlay và kiểm tra thay đổi. Build thường không có `includeEngineProbe` không tải hoặc liên kết SDK ứng viên.

Hai APK nằm dưới `engine-probe/build/outputs/apk/debug/` và `probe-payload/build/outputs/apk/debug/`. Cài/thử theo [checklist phase 1](docs/PHASE1_PHONE_TEST.md). Phải thử thành công dữ liệu mẫu trước khi dùng tài khoản mạng xã hội ở phase 2.
