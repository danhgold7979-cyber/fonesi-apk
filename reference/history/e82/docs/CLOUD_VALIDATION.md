# Kết quả Phase 3–4 phần cloud

Đây là mốc `0.2.0-cloud` với 70 test. Mốc shortcut/schema mới `0.3.0-cloud` được ghi riêng ở [PHASE3_VALIDATION.md](PHASE3_VALIDATION.md); không dùng checksum cũ cho APK mới.

Ngày 26/10/2026. Kiểm thử điện thoại được hoãn theo yêu cầu người dùng. Các kết quả dưới đây không chứng minh clone ứng dụng hay tài khoản mạng xã hội đã hoạt động.

## Kiểm tra đã đạt

| Nhóm | Thực hiện | Kết quả |
| --- | --- | --- |
| Core/JVM | Planner, queue, file snapshot, khóa và recovery | 46 test; 0 thất bại, 0 lỗi, 0 bỏ qua. |
| Android framework mô phỏng | 12 bài kiểm tra lưu trữ/Flow chạy trên API 26 và 34, SQLite native dưới Robolectric | 24 test; 0 thất bại, 0 lỗi, 0 bỏ qua. |
| App chính | Build debug và Android lint | Thành công; 0 lỗi lint, 3 cảnh báo. |
| APK | Chữ ký, package/target SDK, allowlist quyền | Đạt; không có Internet hoặc native engine thử trong app chính. |
| Probe hồi quy | Build/lint hai APK probe với core mới | Thành công; kiểm tra quyền, component, ABI và chữ ký vẫn đạt. Không chạy SDK trên thiết bị. |
| Quy trình chung | `./scripts/check.sh` | Thành công; tổng 70 test có kết quả đạt và artifact được kiểm tra. Gradle có thể tái sử dụng kết quả up-to-date khi đầu vào không đổi. |

Lệnh kiểm tra cụ thể đã chạy:

```bash
./scripts/gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
./scripts/check.sh
./scripts/check.sh --with-engine-probe
```

Báo cáo máy đọc được: `app/build/reports/cloud-verification.json`. Report các suite: `core/build/reports/tests/test/` và `app/build/reports/tests/testDebugUnitTest/`.

## Các hành vi có bằng chứng cloud

- Chống trùng yêu cầu, thứ tự chèn bền vững và pause được kiểm tra trong cùng transaction với marker apply.
- Không gọi engine trước khi lưu marker; kết quả receipt không thay readback.
- Hai runner không đồng thời lấy được cùng OS file lock.
- Sau gián đoạn ở apply/verify, không tự áp dụng lại; có thể xác nhận phiên bản đã ghi mà không gọi cập nhật lần hai.
- Đổi nguồn/scope, bản đang dùng, source mất/empty/tampered và lỗi chuẩn bị chặn apply trong các trường hợp đã thử.
- Base/split được sao chép byte-for-byte, nguồn gốc không bị ghi/xóa; symlink/path traversal bị từ chối.
- Room giữ metadata theo engine/clone ID, dữ liệu Unicode và số phiên bản Long; CAS từ chối revision cũ hoặc thay danh tính tác vụ.
- Đóng/mở database vẫn còn queue/pause/metadata; native SQLite không bị tự xóa khi schema/encoding không hỗ trợ hoặc khi thử với file database hỏng.
- Flow phát thay đổi queue/pause sau khi ghi, không cần polling.

Engine double chỉ tồn tại trong mã test. Các tệp `.apk` tổng hợp trong test filesystem là byte mẫu để thử copy/hash, không phải bằng chứng cài APK thật.

## APK app chính

- Package: `vn.fonesi.app`.
- Phiên bản: `0.2.0-cloud`, versionCode `2`.
- Đường dẫn: `app/build/outputs/apk/debug/app-debug.apk`.
- Kích thước: `9976665` byte.
- SHA-256: `f219e0127f587cfe78c31657f34eb10945de2bb23c23e7249c801cab260d5b49`.
- Chức năng: scanner, các tab quản lý metadata/hàng đợi và lưu trạng thái; engine sản phẩm vẫn chưa được tích hợp.

Ba cảnh báo lint app chính gồm hai thông báo dependency có phiên bản mới hơn và một gợi ý KTX bitmap. Không tắt các kiểm tra này. Cảnh báo 16 KB của SDK probe vẫn là giới hạn thật, ghi trong audit phase 1.

## Chưa đạt hoặc chưa chạy

- Chưa chạy UI mới hoặc engine trên điện thoại Samsung/hãng khác.
- Chưa chạy đăng nhập, cách ly tài khoản, thông báo, camera, shortcut hoặc giữ phiên sau cập nhật.
- Chưa xác minh SDK import toàn bộ base/split và không giữ đường dẫn snapshot tạm sau khi hàm update trả về.
- Chưa có engine adapter sản phẩm hoặc scheduler tự chạy queue. App chính không tạo/cập nhật ứng dụng thật.
- Test gián đoạn là fault injection trong tiến trình kiểm thử, không phải chứng nhận mất điện/phần cứng trên mọi thiết bị.
- Chưa publish môi trường, phát hành release hoặc đẩy code lên GitHub trong lượt này.

Các cổng runtime còn mở không chặn tiếp tục code độc lập theo yêu cầu mới, nhưng không được đánh dấu đạt khi nghiệm thu sản phẩm.

Đã cập nhật `install_script` trong bản nháp cấu hình cloud để bootstrap rồi chạy cùng lệnh `check.sh --with-engine-probe`. Đây là lưu cấu hình nháp, chưa publish hoặc chứng minh phục hồi trong task mới. Muốn áp dụng snapshot tái sử dụng, cần xem/lưu thay đổi trong Environment settings rồi publish môi trường.
