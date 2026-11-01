# Kết quả cloud của Phase 1

Ngày 26/10/2026. **Phase 1 chưa hoàn tất runtime.** Không có điện thoại kết nối trong lần kiểm tra ADB cuối; không có kiểm tra nào chạy Facebook/Zalo hoặc đăng nhập tài khoản.

## Đã chạy

- Fetch SDK theo commit/checksum cố định: đạt. Kiểm tra overlay bằng `generate_probe_manifest.py --check`: đạt, không sửa tệp nguồn.
- `:core:test`: 19 test, 0 thất bại, 0 lỗi, 0 bỏ qua; gồm 15 test planner và 4 test điều kiện thử SDK.
- Build debug/lint app chính, engine probe và payload: đạt. Build/lint lại probe sau khi thêm thao tác dừng vùng: đạt.
- Lint cuối: app chính 0 lỗi/2 cảnh báo; probe 0 lỗi/13 cảnh báo; payload 0 lỗi/7 cảnh báo.
- Kiểm tra chữ ký APK và hai app thử cùng signer: đạt.
- Kiểm tra APK cuối bằng `verify_probe_apks.py`: đạt allowlist quyền, package, target SDK 36, component exported và ARM64.
- APK probe chỉ có foreground-service, special-use foreground-service, thông báo và hai quyền chữ ký nội bộ. Không có Internet. Payload không có `uses-permission`.
- Chỉ launcher `vn.fonesi.probe.ProbeActivity` không yêu cầu quyền bảo vệ; 2603 component exported khác có quyền chữ ký hoặc đúng quyền hệ thống của JobService/ProfileInstaller. Đây là kiểm tra manifest, không chứng minh toàn bộ SDK an toàn.
- App chính không có quyền mới hoặc thư viện `libfcore.so`; SDK thử không bị đưa vào app chính.
- `adb devices`: 0 thiết bị; đã dừng server do lần kiểm tra khởi động.

## APK và checksum của lần kiểm chứng Phase 1 (lịch sử)

Các đường dẫn build thông thường có thể bị ghi đè ở lượt sau. Hai APK probe gốc được giữ dưới `engine-probe/build/outputs/baseline-phase1/` và `probe-payload/build/outputs/baseline-phase1/`. Bản app nền tảng trong bảng là kết quả lúc đó, không phải checksum của APK `0.2.0-cloud` hiện tại.

| APK | Byte | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 9949585 | `874ba85d11493427afc7c57c65c806f57575551f92acccc14fea483848db645f` |
| `engine-probe/build/outputs/apk/debug/engine-probe-debug.apk` | 9421115 | `c77da3c1b6ea379b44374bd16a291532beb6beeb7556627c68ab63486dbb0e5a` |
| `probe-payload/build/outputs/apk/debug/probe-payload-debug.apk` | 834556 | `50e3028d0cd467b5fd7b943aa4f8d0f6098d880ad6222d3f02eb22dedd7c6bf9` |

APK app chính vẫn là nền tảng, không clone được. Hai APK thử là bản debug dùng dữ liệu mẫu, không phải APK sản phẩm hoàn chỉnh.

## Giới hạn và cảnh báo chưa giải quyết

- Sáu cảnh báo `Aligned16KB` của các thư viện native cũ được giữ nguyên; probe chỉ cho thử trang 4096 byte. Chưa có bằng chứng hỗ trợ 16 KB hoặc Android/API ngoài 26–34.
- Các cảnh báo còn lại gồm phiên bản dependency mới hơn, giới hạn ABI và gợi ý UI/icon/KTX. Không coi chúng là lỗi runtime đã được loại trừ.
- Lint ban đầu báo nhầm quyền trên dòng gỡ quyền; xử lý có phạm vi và đối chứng APK được ghi tại [audit](PHASE1_ENGINE_AUDIT.md). Không có baseline tắt lỗi toàn dự án.
- Chưa chạy SDK init, tạo/mở payload, cách ly GỐC/A/B, khôi phục sau reboot hoặc xóa A trên điện thoại. Tất cả đều là **chưa chạy**, không phải đạt hoặc bỏ qua có chủ đích.
- Chưa kiểm thử split APK, cập nhật gốc, thông báo, tài khoản thật hoặc thiết bị hãng khác.

Kết quả audit máy đọc được nằm ở `engine-probe/build/reports/probe-apk-audit.json`. Hướng dẫn chạy lại lệnh nằm trong [README](../README.md); bài thử thiết bị ở [PHASE1_PHONE_TEST.md](PHASE1_PHONE_TEST.md).
