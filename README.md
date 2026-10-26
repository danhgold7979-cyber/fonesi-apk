# Fonesi

Ứng dụng Android dự kiến quản lý nhiều bản clone của các ứng dụng trên điện thoại, ưu tiên mạng xã hội, không yêu cầu root.

Kế hoạch sản phẩm và các điều kiện kỹ thuật nằm trong [docs/PLAN.md](docs/PLAN.md).

Lộ trình chi tiết: [8 phase, 32 bước và 4 mốc thử điện thoại](docs/ROADMAP.md).

Theo dõi [phase hiện tại](docs/STATUS.md) và [quy luật code/test](docs/CODE_TEST_RULES.md).

**Trạng thái app chính `0.3.0-cloud`:** có quét ứng dụng, quản lý metadata, UI hàng đợi, lưu trữ Room và luồng mở/ghim shortcut. App chính chưa tích hợp engine; APK này chưa tạo hoặc chạy được bản clone và chưa cập nhật được tài khoản nào. Không dùng APK này thay cho sản phẩm clone hoàn chỉnh. Theo yêu cầu mới, kiểm thử điện thoại được hoãn để tiếp tục code cloud.

Không dùng lối tắt mở app gốc hoặc trang web để giả lập một bản clone có tài khoản độc lập.

## Chức năng hiện có

- Quét các ứng dụng có màn hình mở trong hồ sơ Android hiện tại; tìm kiếm, lọc nhóm ứng dụng ưu tiên và xem chi tiết.
- Đọc phiên bản, chứng thư ký và các đường dẫn APK base/split để chuẩn bị nguồn cập nhật. Không xuất danh sách này lên máy chủ.
- Planner/runner cập nhật: phân biệt phạm vi từng bản/dùng chung, sao chép nguồn base/split, chống trùng, OS lock, journal, pause và khôi phục không tự chạy lại kết quả chưa rõ.
- Room lưu metadata theo engine/clone ID và lịch sử hàng đợi; có các tab Bản clone/Cập nhật, sửa tên/nhóm, pause, hủy yêu cầu chưa chạy. Không seed dữ liệu clone giả.
- Mở/ghim shortcut qua UUID theo lần tạo; kiểm tra metadata/engine/cập nhật trước dispatch, đồng bộ tên và vô hiệu hóa mục đã xóa. Có migration Room 1 → 2 giữ dữ liệu. Xem [SHORTCUTS.md](docs/SHORTCUTS.md); chưa thử launcher hoặc guest thật.
- Chặn thao tác clone khi engine chưa có; không tạo hồ sơ giả hoặc báo thành công giả.
- Không khai báo quyền Internet, không có analytics/SDK quảng cáo. Tắt sao lưu tự động và khai báo loại trừ truyền dữ liệu cho Android mới.

Runner/lưu bền vững đã có và được kiểm tra với engine double trong test; **chưa nối vào SDK sản phẩm hoặc scheduler**, nên chưa cập nhật ứng dụng thật. Sinh trắc học và sao lưu thủ công chưa có. Chi tiết [CLOUD_BACKEND.md](docs/CLOUD_BACKEND.md).

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

Unit test kiểm tra planner/runner/filesystem; Robolectric kiểm tra Room với SQLite native và định tuyến/shortcut mô phỏng trên API 26/34. Không test nào chạy Facebook/Zalo/TikTok hoặc chứng minh cách ly tài khoản. Kiểm thử điện thoại được hoãn theo yêu cầu; vẫn cần thực hiện trước khi nghiệm thu runtime. Xem [mốc cloud ban đầu](docs/CLOUD_VALIDATION.md) và [mốc shortcut/schema mới](docs/PHASE3_VALIDATION.md).

## Phase 1: build bộ thử engine riêng

Đọc [audit engine](docs/PHASE1_ENGINE_AUDIT.md) và [thông tin SDK mã đóng](third_party/spacecore/NOTICE.md) trước khi dùng bản thử. SDK không được đưa vào app chính. Không dùng tài khoản thật trong hai APK thử.

```bash
python3 scripts/fetch_engine_sdk.py
python3 scripts/generate_probe_manifest.py --check
./scripts/check.sh --with-engine-probe
```

Script `--check` không sửa source. Chỉ dùng chế độ không có `--check` khi chủ động phát triển overlay và kiểm tra thay đổi. Build thường không có `includeEngineProbe` không tải hoặc liên kết SDK ứng viên.

Hai APK nằm dưới `engine-probe/build/outputs/apk/debug/` và `probe-payload/build/outputs/apk/debug/`. Cài/thử theo [checklist phase 1](docs/PHASE1_PHONE_TEST.md). Phải thử thành công dữ liệu mẫu trước khi dùng tài khoản mạng xã hội ở phase 2.
