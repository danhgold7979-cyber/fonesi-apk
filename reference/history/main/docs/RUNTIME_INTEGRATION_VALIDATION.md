# Kết quả tích hợp candidate 0.4.0

Ngày 26/10/2026. Chỉ kiểm tra cloud; chưa thực thi native engine, tài khoản hoặc UI trên điện thoại. Yêu cầu gom thử máy về đợt sau được giữ; bản này không được đánh dấu hoàn thiện toàn bộ.

## Lệnh và kết quả

```bash
./scripts/check.sh --with-engine-candidate
python3 scripts/build_phone_bundle.py
```

Cả hai kết thúc với exit code 0. Lệnh đầu kiểm tra cả cloud và candidate; lệnh sau xác minh lại hash candidate đã audit, build/lint hai bản payload với mã phiên bản 1/2, kiểm tra cùng chữ ký/không quyền và đóng gói để dùng về sau. Không cài APK hay chạy SDK trong các lệnh này.

| Nhóm | Kết quả |
| --- | --- |
| Core/JVM | 61 test, 0 thất bại/lỗi/bỏ qua. |
| Android mô phỏng API 26/34, SQLite native | 62 test, 0 thất bại/lỗi/bỏ qua. |
| Tổng logic/lưu trữ/protocol | **123 test đạt**; không phải 123 phép thử SDK trên thiết bị. |
| Cloud APK | Build/lint đạt; 0 lỗi, 5 cảnh báo. Không chứa `libfcore.so`/Internet. |
| Candidate APK | Build/lint đạt; 0 lỗi, 15 cảnh báo. Có SDK ARM64 thật, source pin/hash đạt. |
| Audit APK candidate | Đúng package/target 36, chữ ký, 9 quyền dự kiến; 2 activity public và 2603 component exported được bảo vệ bằng đúng quyền. |
| Payload 1/2 | Build/lint đạt; đúng versionCode, không xin quyền, cùng signer candidate. |

Kết quả Gradle có thể dùng lại đầu ra up-to-date; hai cấu hình chạy cùng bộ kiểm thử không được cộng thành hai lần số test độc lập.

## Bằng chứng mới

- Protocol gọi backend đúng user, dùng nguồn snapshot, đối chiếu trước READY và không ghi clone giả.
- Chặn split trước reserve/call SDK, chặn tạo bản làm đổi shared code hoặc gián đoạn bản đang dùng.
- Reservation bền vững trước tạo user, không ghi đè user có sẵn; giữ reservation và nguồn khi native side effect chưa rõ.
- Pending trước launch, giữ trạng thái chưa biết qua việc tạo lại adapter; exception không tự retry. Dừng/readback không được biến trường hợp SDK vẫn báo chạy thành đang rảnh.
- Xóa chỉ gửi đúng user; không tái dùng reservation đã xóa. Cập nhật shared code chỉ nhập một lần, kiểm tra toàn nhóm; partial readback không dẫn tới cài lại hoặc xóa dữ liệu để giả rollback.
- Registry Room giữ pending/state qua reopen, không sửa chéo namespace, 8 writer concurrent có ID riêng; import metadata chỉ từ record backend có đọc lại và từ chối token xóa cũ.
- Migration schema 1 → 2 → 3 chạy với SQLite native và giữ metadata/queue/pause. Schema cũ vẫn có trong source.

Các backend double chỉ ở `src/test`. `SpaceCoreBackend` candidate thực tế tham chiếu API SDK khi build; các guard của nó được compile/lint/audit tĩnh nhưng chưa được gọi trên ARM. Vì vậy chưa xác minh riêng việc SDK copy APK, guest startup, phiên dữ liệu hay shared-package behavior.

## Artifact đã xác minh

| Build | Đường dẫn | Byte | SHA-256 |
| --- | --- | ---: | --- |
| Cloud | `app/build/outputs/cloud/app-debug.apk` | 10111874 | `cd90412d15c61185a93b125d592f48b44e9cbb892d656f72476a87e358163307` |
| Candidate | `app/build/outputs/candidate/fonesi-0.4.0-candidate.apk` | 17105222 | `7424bd3ad6f64ec33123c01eedb104de8a341f7dd58ac43eb27a4b91d6139484` |

Machine reports nằm cạnh từng APK trong `verification.json`. File APK debug chung trong `outputs/apk/debug/` là cấu hình build gần nhất; dùng thư mục cloud/candidate tách riêng để tránh lấy nhầm.

Gói chuẩn bị thử: `app/build/outputs/fonesi-0.4.0-phone-test.zip`. Chứa candidate, payload revision 1/2, hướng dẫn, thông tin SDK và `checksums.json`; không chứa keystore hoặc tài khoản. Chưa yêu cầu cài thử ngay. Build bundle không phải chứng nhận nghiệm thu cuối.

## Cảnh báo và phần còn thiếu

Không suppress lỗi/cảnh báo mới để làm xanh. Candidate còn 6 cảnh báo `Aligned16KB`, 1 `ChromeOsAbiSupport`, 4 `UseKtx`, 2 dependency mới hơn, 1 `CustomSplashScreen` và 1 thứ tự manifest. Giới hạn 4 KB/ARM64 là thật, guard không chứng minh runtime tương thích. App vẫn target 36, không hạ target.

Split APK, Android ngoài phạm vi ứng viên, đăng nhập/cách ly, cập nhật giữ phiên, Play Services và ổn định đa máy chưa đạt. Sinh trắc học, sao lưu cấu hình, scheduler nền và bản ký phát hành chưa hoàn thiện. Chi tiết và thứ tự thử sau ở [INTEGRATED_CANDIDATE.md](INTEGRATED_CANDIDATE.md); không coi build/test cloud là đã đáp ứng tất cả yêu cầu.
