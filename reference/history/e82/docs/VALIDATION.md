# Kiểm chứng bản nền tảng

Ngày: 26/10/2026. Đây là kết quả của phiên cloud hiện tại, không phải kiểm chứng sản phẩm clone hoặc môi trường đã publish.

| Kiểm tra | Kết quả |
| --- | --- |
| `python3 scripts/bootstrap_android.py --accept-sdk-license` | Thành công; chạy lại cũng thành công. |
| `./scripts/gradle.sh :core:test :app:assembleDebug :app:lintDebug --console=plain` | Thành công. |
| Unit test `vn.fonesi.core.UpdatePlannerTest` | 15 test, 0 thất bại, 0 lỗi, 0 bỏ qua. |
| Build/lint lại sau khi ghim Build Tools 36 và bổ sung quy tắc sao lưu | Thành công. |
| Android lint cuối cùng | 0 lỗi; 2 cảnh báo: có phiên bản coroutines mới hơn và gợi ý dùng hàm KTX cho bitmap. Không tắt các kiểm tra này. |
| `apksigner verify --verbose` | Thành công, APK debug có chữ ký v2. |
| Kiểm tra manifest bằng `aapt2 dump permissions` | Không có quyền Internet; chỉ có quyền nội bộ receiver do AndroidX khai báo. |
| `adb devices` | Lệnh chạy thành công, danh sách thiết bị trống. Đã dừng ADB server do phiên kiểm tra khởi động. |

Các test lập kế hoạch kiểm tra phạm vi cập nhật từng bản/dùng chung, chọn trùng bản, bản khác đang mở, trạng thái không rõ, thiếu app gốc, thay chữ ký, tránh hạ phiên bản, nguồn sai/thiếu, bản đã bị xóa và engine chưa có. **Không có test nào chạy hoặc đăng nhập ứng dụng mạng xã hội.**

## APK đã tạo

- Đường dẫn bản baseline được giữ lại: `app/build/outputs/baseline-phase0/app-debug.apk`. Đường dẫn APK debug thông thường có thể được ghi đè bởi build tiếp theo.
- Phiên bản: `0.1.0-dev`; mã gói: `vn.fonesi.app`.
- Kích thước: 9.949.585 byte.
- SHA-256: `fafb9e4a1881233b2688ae3252d5a1115b2489b7674b86d05d56773a55310ed5`.
- Đây là APK quét/xem thông tin ứng dụng, chưa phải APK clone hoạt động.

## Chưa kiểm chứng hoặc chưa triển khai

- Chạy giao diện và quét trên điện thoại Samsung/hãng khác: chưa kiểm chứng trên thiết bị.
- Engine tạo vùng chạy ứng dụng, cách ly tài khoản, giữ phiên đăng nhập, thông báo, camera: chưa tích hợp/kiểm chứng.
- Ghim biểu tượng mở bản clone: chưa triển khai.
- Thực thi cập nhật từ app gốc và hàng đợi khôi phục sau khi tiến trình bị đóng: chưa triển khai; mới có logic lập kế hoạch.
- Khóa sinh trắc học, nhóm tài khoản, sao lưu thủ công: chưa triển khai.
- Phát hành APK release, đẩy mã lên GitHub, publish môi trường hoặc kiểm chứng trong task mới: chưa thực hiện.

## Cấu hình cloud

Đã lưu `install_script` vào bản nháp cấu hình: bootstrap toolchain rồi chạy unit test, build debug và lint trong `/workspace/fonesi-apk`. Không lưu tiến trình chạy nền vì ứng dụng Android phải chạy trên thiết bị/emulator; không có web server của sản phẩm.

Lưu bản nháp không tự áp dụng hoặc publish. Muốn tái sử dụng snapshot môi trường, cần xem lại và lưu cấu hình trong environment settings rồi publish. Snapshot/build thành công cũng không chứng minh chức năng clone đã hoạt động.
