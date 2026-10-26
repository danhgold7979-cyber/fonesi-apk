# Trạng thái thực hiện

**Đã triển khai bản tích hợp thử Phase 1–4, `0.4.0-candidate`; chưa hoàn thiện toàn bộ sản phẩm.** SDK thật đã nối vào luồng tạo/mở/dừng/xóa/cập nhật trong build candidate. Người dùng yêu cầu gom thử điện thoại về đợt sau. Runtime vẫn chưa nghiệm thu; split APK/Android mới và phần phase 6–7 còn thiếu. Xem [INTEGRATED_CANDIDATE.md](INTEGRATED_CANDIDATE.md).

| Phase/bước | Trạng thái | Bằng chứng hoặc phần còn thiếu |
| --- | --- | --- |
| Phase 0, bước 1–4 | Đạt phần cloud | Project, toolchain, build nền tảng và baseline trong [VALIDATION.md](VALIDATION.md). |
| Phase 1, bước 5 | Đã đánh giá ứng viên ban đầu | [Audit](PHASE1_ENGINE_AUDIT.md); chọn SpaceCore chỉ cho phép thử ngoại tuyến, chưa chốt engine sản phẩm. |
| Phase 1, bước 6 | Có hai phạm vi thử được giới hạn | Probe vẫn ngoại tuyến; candidate có Internet/camera/mic/thông báo/foreground, init bật chủ động. Pin hash, guard component chữ ký và audit APK cuối; SDK mã đóng chưa được audit toàn bộ. |
| Phase 1, bước 7 | Đạt build/kiểm tra cloud trong phạm vi ghi nhận | 19 unit test đạt; app chính và hai app thử build/lint không có lỗi; kiểm tra APK/chữ ký đạt. Còn cảnh báo thật, gồm native 4 KB. |
| Phase 1, bước 8 | Có adapter SDK thật và payload; runtime tạm hoãn | Candidate thực sự gọi SDK; chỉ nhận một APK, ARM64/API 26–34/4 KB. Chưa chứng nhận SDK khởi tạo/chạy target 36. |
| Phase 2 | Chưa nghiệm thu | Chưa chạy tài khoản Facebook riêng trên điện thoại. |
| Phase 3, bước 13–14 | Đã nối vòng đời và registry bền vững vào candidate | Tạo/thêm bản sạch/dừng/xóa, Room schema 3, user không tái sử dụng, launch-pending và phục hồi metadata có readback. Test cloud không thay cho SDK thật. |
| Phase 3, bước 15–16 | Có controller mở, ghim shortcut, migration và phối hợp khóa | UUID theo lần tạo, đối chiếu namespace/package/journal, đồng bộ tên và vô hiệu hóa mục đã xóa; kiểm thử cloud không thay cho mở guest/launcher thật. Chi tiết [SHORTCUTS.md](SHORTCUTS.md). |
| Phase 4, bước 17–18 | Đã có phần điều phối/lưu bền vững trong cloud | Snapshot base/split, chống trùng, OS lock, write-ahead marker, CAS/pause, readback và recovery. Chưa kiểm chứng engine import APK thật. |
| Phase 4, bước 19–20 | Đã nối planner/queue/runner với adapter candidate | Cập nhật một bản/tất cả theo nhóm, chạy tuần tự khi người dùng yêu cầu, có pause trong lúc runner chạy. Chưa có scheduler tự chạy nền hoặc xác minh giữ phiên đăng nhập. |
| Phase 5 | Bị chặn một phần bởi engine ứng viên | Chưa nhập split APK; các app bắt buộc vẫn chưa đạt và chưa được loại khỏi phạm vi. Cần engine đủ khả năng và test từng app. |
| Phase 6 | Có thiết lập runtime/quyền; chưa hoàn tất | Chưa có sinh trắc học hoặc sao lưu/nhập cấu hình đầy đủ. |
| Phase 7 | Chưa hoàn tất | Chưa đo máy thật, đa hãng hoặc ký bản phát hành. Không suy ra ổn định từ unit test. |

## Bản đã tạo

- App `vn.fonesi.app`, `0.4.0-cloud`: không SDK/Internet; có quản lý và hợp đồng runtime.
- App cùng ID `0.4.0-candidate`: có SDK thật, mặc định chưa chủ động init; chỉ là bản debug để thử, chưa phải engine sản phẩm đã nghiệm thu. Không cài song song với cloud.
- `vn.fonesi.probe`: chỉ thử SDK ứng viên; mặc định chưa chủ động khởi tạo SDK, chỉ cho phép thao tác payload của dự án.
- `vn.fonesi.payload`: ứng dụng dữ liệu mẫu, không có quyền; dùng để đọc/ghi ID và nhãn độc lập.

Chi tiết phần mới ở [CLOUD_BACKEND.md](CLOUD_BACKEND.md) và kết quả [CLOUD_VALIDATION.md](CLOUD_VALIDATION.md). Kết quả probe ban đầu ở [PHASE1_VALIDATION.md](PHASE1_VALIDATION.md). Quy luật phát triển ở [CODE_TEST_RULES.md](CODE_TEST_RULES.md).

Luồng shortcut/schema mới được ghi ở [SHORTCUTS.md](SHORTCUTS.md), với bằng chứng bản `0.3.0-cloud` tại [PHASE3_VALIDATION.md](PHASE3_VALIDATION.md).

Bằng chứng bản `0.4.0`: [RUNTIME_INTEGRATION_VALIDATION.md](RUNTIME_INTEGRATION_VALIDATION.md), 123 test đạt; cloud và candidate build/lint/audit đạt. Đây không phải kết quả engine trên điện thoại. Đã chuẩn bị gói candidate/payload 1–2 để dùng ở đợt thử sau.

## Việc cần làm tiếp

Tiếp tục xử lý engine đủ khả năng nhập split/cập nhật đầy đủ và các phần chưa hoàn thiện. Kiểm thử điện thoại được hoãn theo yêu cầu; chưa cần cài APK ngay lúc này. Đợt thử sau vẫn phải kiểm chứng payload trước tài khoản và có thể cần sửa/thay engine. Không thể gọi code hoàn thiện toàn bộ chỉ vì đã build candidate.

Không chuyển yêu cầu nghiệm thu chưa chạy thành đạt và không tự bỏ ứng dụng bắt buộc khỏi phạm vi. Nếu thay engine, phải đối chiếu namespace/khả năng import snapshot thay vì áp dụng hàng đợi cũ vào một provider khác.
