# Phase 6 — khóa giao diện quản lý và shortcut

Plan trước code nằm trong S1 của [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Mốc nguồn 0.5.0, áp dụng cùng cho build cloud và candidate. Đây là khóa truy cập UI, không mã hóa guest hoặc thay sandbox Android.

## Cách dùng

- Khóa mặc định tắt. Vào Thiết lập → Xác thực và bật khóa; Android xác thực sinh trắc học mạnh hoặc mã khóa máy, sau đó app mới lưu lựa chọn.
- Khi rời Fonesi và quay lại, hoặc mở một shortcut, phải xác thực. Xoay màn hình giữ ViewModel trong cùng lần hiển thị; process mới không khôi phục phiên đã mở khóa.
- Tắt khóa cũng cần xác thực lại. Không có PIN riêng của Fonesi và không nhập PIN/OTP/tài khoản vào chat.
- Nếu không có cảm biến/enrollment hoặc bị khóa sinh trắc, dùng nút mã khóa máy. Nếu chưa đặt khóa màn hình, đặt trong Cài đặt Android trước. Hủy/thất bại giữ khóa; request quá hai phút cần thử lại.
- Cấu hình hỏng/không đọc được yêu cầu xác thực để sửa; không tự coi là tắt. Có thể vào Thiết lập sau khi xác thực bằng khóa máy để lưu lại lựa chọn.

## Điều kiện trước dispatch

MainActivity và CloneLaunchActivity dùng cùng LockedActivity/AccessGateModel. Activity shortcut không gọi model.start trước gate. Ngoài UI gate, CloneLaunchController kiểm tra broker trước lookup và một lần nữa ngay trước gọi SDK, để background trong lúc đọc DB/engine không thành lệnh mở.

Request auth có generation, thời hạn monotonic và callback gắn với đúng attempt; callback cũ không dùng ID của request mới. Rời foreground hủy grant/prompt. Màn hình mã khóa Android được đánh dấu là external credential đang chờ để nhận kết quả khi quay lại, không tự mở trước khi có RESULT_OK. Activity nhận shortcut giữ history đủ để nhận kết quả đó nhưng vẫn ẩn recents; sau dispatch thì finish.

Policy chỉ lưu `{version, required}` trong AtomicFile ở noBackupFilesDir. Corrupt/unknown/oversized/không đọc được được xử lý theo hướng yêu cầu khóa; không xóa policy để bỏ qua lỗi. Không persist token auth, dữ liệu sinh trắc hoặc mã khóa. Những cửa sổ khác được thông báo khi đổi policy và không được kế thừa grant của cửa sổ vừa bật khóa.

FLAG_SECURE bảo vệ screenshot/recents của host khi bật khóa. Không áp cờ đó lên mọi app khách và không ngăn SDK/app khách đã chạy thực hiện công việc nền. Product runner còn kiểm tra foreground/access trước lấy việc và trước APPLYING marker, kể cả khi khóa OFF. Rời UI trước marker thì hoãn; import đã bắt đầu vẫn phải verify hoặc giữ REVIEW. Đây không phải khóa dữ liệu toàn bộ container hay cơ chế chống SDK độc hại cùng UID.

## Kiểm chứng

Adapter candidate còn bind callback foreground/access ở ManagedGuestEngine: create kiểm tra trước snapshot, sau prepare và trước bước SDK đầu tiên; launch/stop/delete/update kiểm tra trước mutation. Đọc lại đủ active registry của nhóm shared, kể cả peer chưa có metadata sau import gián đoạn. Peer thiếu/đang xóa giữ nhóm lại; không tự xóa reservation. Nếu chưa vào SDK thì reservation không dùng được đánh dấu DELETED nhưng giữ ID không tái sử dụng; bước đã vào SDK vẫn phải hoàn tất readback hoặc giữ trạng thái chưa rõ.

Test core kiểm tra foreground/background, callback cũ, rotation, credential return, expiry và đổi policy. Robolectric kiểm tra AtomicFile, policy hai cửa sổ, broker grant, enable/disable và chặn dispatch khi auth mất giữa readback. Build/lint/audit kiểm tra đúng hai quyền normal USE_BIOMETRIC/USE_FINGERPRINT cùng các quyền đã được review trước đó, không thêm quyền truy cập dữ liệu.

Prompt thật, đăng ký sinh trắc thay đổi, PIN hệ thống, Home/rotation/cold start trên One UI và OEM khác vẫn thuộc đợt thử điện thoại sau. Test ViewModel/protocol không chứng minh phần cứng sinh trắc hoặc hành vi OEM đã đạt.
