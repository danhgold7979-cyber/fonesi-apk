# Trạng thái thực hiện — 0.5.0

**Đã hoàn thành thêm phần cloud của S1/S2/U1/Q1; sản phẩm clone đa ứng dụng chưa đạt 100%.** Các phần khóa, cấu hình, chọn nhiều, preview và tự động khi đang mở đã có code và kiểm tra. SDK candidate vẫn chỉ nhập một APK, giới hạn ARM64/API 26–34/trang 4 KB; chưa thực thi được xác minh trên điện thoại. Thử thiết bị được gom về đợt sau theo yêu cầu, không chặn phát triển phần độc lập và không biến runtime chưa chạy thành đạt.

Plan trước từng phần và điều chỉnh: [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Bằng chứng bản hiện tại: [APK_SET_VALIDATION.md](APK_SET_VALIDATION.md). Giữ nguyên lộ trình [8 phase/32 bước](ROADMAP.md).

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

Gói hiện tại: `app/build/outputs/fonesi-0.5.0-phone-test.zip`; quy trình [PHONE_TEST_GUIDE.md](PHONE_TEST_GUIDE.md). Chưa yêu cầu cài ngay. Gỡ Fonesi có thể mất toàn bộ vùng clone; backup tên/nhóm không chứa dữ liệu đăng nhập.

## Điều kiện còn mở

1. Engine phải thật sự lưu/sử dụng/update cả base/split, đủ metadata/resources/classloader/native libs và readback; không thể giải quyết bằng chỉ copy/merge/re-sign APK.
2. Phải xác minh Android/ABI/page size, target 36 và luồng khởi tạo/chạy nền của engine. SM-A525F là máy thử đầu, không phải giới hạn model của sản phẩm; Android đang cài chưa xác nhận.
3. Payload, cách ly, đăng nhập, thông báo/media/Play Services, update giữ phiên và nhiều ứng dụng phải có kết quả thiết bị. Không hứa tránh ban, không văng hoặc vô hạn tiến trình đồng thời.
4. Worker nền độc lập, màu/huy hiệu riêng, ký/nâng cấp release và nghiệm thu đa hãng/hiệu năng còn thiếu. Không đóng các yêu cầu này bằng test giao diện.

Nếu đổi provider phải namespace/đối chiếu và thiết kế migration riêng, không chạy journal của engine cũ qua engine mới. Không tự xóa guest/metadata/journal để làm kết quả có vẻ đúng.
