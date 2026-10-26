# Trạng thái thực hiện

**Đang triển khai Phase 3, bước 15–16 ở phần cloud; đã có nền tảng Phase 4, bước 17–20.** Người dùng đã yêu cầu hoãn thử điện thoại và tiếp tục code. Runtime phase 1–2 vẫn chưa được nghiệm thu; việc này không chặn phần độc lập đã được cho phép.

| Phase/bước | Trạng thái | Bằng chứng hoặc phần còn thiếu |
| --- | --- | --- |
| Phase 0, bước 1–4 | Đạt phần cloud | Project, toolchain, build nền tảng và baseline trong [VALIDATION.md](VALIDATION.md). |
| Phase 1, bước 5 | Đã đánh giá ứng viên ban đầu | [Audit](PHASE1_ENGINE_AUDIT.md); chọn SpaceCore chỉ cho phép thử ngoại tuyến, chưa chốt engine sản phẩm. |
| Phase 1, bước 6 | Đã giới hạn gói thử và kiểm tra tĩnh | SDK pin nguồn/hash; app thử không Internet; payload cùng chữ ký; quyền/component được kiểm tra trên APK cuối. SDK mã đóng chưa được audit toàn bộ nội bộ. |
| Phase 1, bước 7 | Đạt build/kiểm tra cloud trong phạm vi ghi nhận | 19 unit test đạt; app chính và hai app thử build/lint không có lỗi; kiểm tra APK/chữ ký đạt. Còn cảnh báo thật, gồm native 4 KB. |
| Phase 1, bước 8 | Đã code adapter và payload; runtime tạm hoãn | [Checklist điện thoại](PHASE1_PHONE_TEST.md) để dùng về sau; không yêu cầu người dùng thử ngay để tiếp tục việc cloud. |
| Phase 2 | Chưa nghiệm thu | Chưa chạy tài khoản Facebook riêng trên điện thoại. |
| Phase 3, bước 13–14 | Đã có phần lưu trữ/controller và UI quản lý metadata | Room/schema, namespace engine, readback trước khi lưu/xóa, đổi tên/nhóm. Test cloud có engine double, không thay cho chạy SDK thật. |
| Phase 3, bước 15–16 | Có controller mở, ghim shortcut, migration và phối hợp khóa | UUID theo lần tạo, đối chiếu namespace/package/journal, đồng bộ tên và vô hiệu hóa mục đã xóa; kiểm thử cloud không thay cho mở guest/launcher thật. Chi tiết [SHORTCUTS.md](SHORTCUTS.md). |
| Phase 4, bước 17–18 | Đã có phần điều phối/lưu bền vững trong cloud | Snapshot base/split, chống trùng, OS lock, write-ahead marker, CAS/pause, readback và recovery. Chưa kiểm chứng engine import APK thật. |
| Phase 4, bước 19–20 | Có UI quản lý queue và fault-injection test; chưa nối cập nhật thật | Chưa có scheduler/cập nhật SDK sản phẩm hoặc xác minh giữ phiên đăng nhập. |
| Phase 5–7 | Chưa hoàn tất | Không suy ra tương thích/ổn định trên điện thoại từ unit test. |

## Bản đã tạo

- App chính `vn.fonesi.app`, `0.3.0-cloud`: scanner, UI metadata/hàng đợi, lưu trữ và luồng shortcut; chưa tích hợp engine sản phẩm.
- `vn.fonesi.probe`: chỉ thử SDK ứng viên; mặc định chưa chủ động khởi tạo SDK, chỉ cho phép thao tác payload của dự án.
- `vn.fonesi.payload`: ứng dụng dữ liệu mẫu, không có quyền; dùng để đọc/ghi ID và nhãn độc lập.

Chi tiết phần mới ở [CLOUD_BACKEND.md](CLOUD_BACKEND.md) và kết quả [CLOUD_VALIDATION.md](CLOUD_VALIDATION.md). Kết quả probe ban đầu ở [PHASE1_VALIDATION.md](PHASE1_VALIDATION.md). Quy luật phát triển ở [CODE_TEST_RULES.md](CODE_TEST_RULES.md).

Luồng shortcut/schema mới được ghi ở [SHORTCUTS.md](SHORTCUTS.md), với bằng chứng bản `0.3.0-cloud` tại [PHASE3_VALIDATION.md](PHASE3_VALIDATION.md).

## Việc cần làm tiếp

Tiếp tục các phần code cloud độc lập và chuẩn bị tích hợp engine qua hợp đồng đã ghi. Kiểm thử điện thoại được hoãn theo yêu cầu; không cần cài hai APK ngay lúc này. Về sau vẫn cần thử runtime trước khi xác nhận clone, dữ liệu, cập nhật và tài khoản hoạt động.

Không chuyển yêu cầu nghiệm thu chưa chạy thành đạt và không tự bỏ ứng dụng bắt buộc khỏi phạm vi. Nếu thay engine, phải đối chiếu namespace/khả năng import snapshot thay vì áp dụng hàng đợi cũ vào một provider khác.
