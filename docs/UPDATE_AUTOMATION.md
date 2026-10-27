# Preview cập nhật và tự động khi đang mở — 0.5.0

Plan U1/Q1 và các điều chỉnh được ghi trước code trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Nguồn luôn là app gốc đã cài trên điện thoại; không tải APK dịch vụ khác, đổi chữ ký hoặc copy phiên đăng nhập.

## Một bản, tập chọn và tất cả

Mọi nút cập nhật đi qua UpdateRequestService: đọc metadata/engine/source dưới OS operation lock → preview → người dùng xác nhận → đọc lại → so sánh phạm vi → enqueue toàn batch trong một Room transaction. Preview không phải lệnh, không persist như yêu cầu đã duyệt hoặc tự khôi phục để chạy sau process restart.

Preview hiển thị source/version, các bản bị ảnh hưởng, đã current và lý do bị chặn. Với shared package, bao gồm cả peer không chọn. Bất kỳ metadata peer nào cùng provider bị mất/đổi danh tính trong readback đều giữ cả nhóm; không âm thầm bỏ peer. Split không được engine hỗ trợ thì giữ riêng nhóm đó, nhóm độc lập đủ điều kiện vẫn xem/xếp được.

Confirm đối chiếu nguồn/path/signers/version, request token/ID, scope/capability và tập peer. Có thay đổi thì yêu cầu preview mới; không tự mở rộng phạm vi consent. Runner vẫn recheck ngay trước apply và verify readback sau SDK; confirm không thay cho các kiểm tra này.

## Các mode

| Mode | Hành vi |
| --- | --- |
| Tắt | Mặc định. Không tạo tick SDK, không tự xếp/cập nhật. |
| Chỉ kiểm tra khi mở | Đọc trạng thái/nguồn và báo các nhóm đủ điều kiện; không tự enqueue/import. Có thể đối chiếu readback để phục hồi journal, không tự re-apply kết quả chưa rõ. |
| Cập nhật khi đang mở | Cần đồng ý rõ ảnh hưởng các nhóm dùng chung, kể cả peer hiện tại/tạo về sau. Kiểm tra khi UI mở/đã mở khóa và mỗi phút còn hiển thị, xếp/chạy tuần tự qua cùng queue/runner. |

Policy AtomicFile riêng noBackup, không nhập từ file tên/nhóm. Hỏng/unknown thì OFF và báo xem lại. Chỉ khởi tác vụ khi có owner UI foreground và access; khóa OFF cũng không cho bắt đầu import ở nền. Không có boot receiver, background worker hoặc cam kết cập nhật độc lập khi Fonesi đã đóng. Điều kiện SDK/FGS/background target 36 chưa có bằng chứng để triển khai worker an toàn.

Mode APPLY tôn trọng pause, không tự resume. Tắt mode/ra nền/khóa trước durable APPLYING marker sẽ hoãn, dọn snapshot và không gọi import. Nếu SDK đã bắt đầu, phải hoàn tất verify hoặc giữ REVIEW; không giả hủy/rollback một side effect đã có thể xảy ra. Sau đó không lấy tác vụ mới khi điều kiện mất. Cơ chế này áp cho product runner thủ công và tự động.

Auto chỉ retry DEFERRED ở lý do an toàn khi preview mới xác nhận đủ source/ID; không retry REVIEW/APPLYING/VERIFYING/unknown. QUEUED/DEFERRED nguồn cũ có thể được ghi CANCELLED với SUPERSEDED_SOURCE trước xếp nguồn mới; không xóa journal. OS lock riêng ngăn hai auto coordinator chạy trùng, operation lock/CAS vẫn phối hợp manual và lifecycle.

Đếm “đã xử lý” không có nghĩa mọi tác vụ thành công. Chỉ task được verify/readback mới báo thành công; phiên đăng nhập/dữ liệu thực tế sau update còn phải kiểm tra trên máy.

## Bằng chứng

Unit tests kiểm tra execution-policy trước lấy job/sau prepare/trước marker, giữ verify sau SDK entry và split capability đổi. Robolectric/native SQLite kiểm tra preview stale/shared scope/atomic enqueue, mode default/corrupt, dedup/pause/OS locks, đổi OFF trong batch, safe retry, superseded source và giữ REVIEW. Nguồn byte mẫu/engine double chỉ nằm trong test; không chứng minh import APK hoặc tài khoản thật.
