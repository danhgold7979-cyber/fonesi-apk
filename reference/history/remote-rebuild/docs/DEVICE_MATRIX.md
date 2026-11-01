# Thiết bị và phạm vi tương thích

## Nguyên tắc

- Mục tiêu là một APK dùng trên nhiều model Samsung và các hãng Android khác, không yêu cầu root.
- Không thêm kiểm tra chỉ cho phép model SM-A525F chạy. Model là thông tin chẩn đoán/kiểm thử, không phải khóa sử dụng.
- Khả năng chạy phụ thuộc phiên bản Android, ABI/kiến trúc CPU, engine, phiên bản ứng dụng cần clone và hạn chế của hệ thống. Khi cần điều kiện tương thích, kiểm tra khả năng thực tế thay vì suy ra chỉ từ tên máy.
- Một APK có thể đóng gói nhiều ABI nếu engine có thư viện tương ứng; không coi việc app quản lý cài được là bằng chứng app clone chạy được.
- Chỉ công bố hỗ trợ trong phạm vi có bằng chứng. "Chưa kiểm thử" khác "không hỗ trợ"; thử thành công trên hai hãng cũng không chứng minh mọi máy Android hoạt động.

## Danh sách thiết bị

| Thiết bị | Nguồn thông tin | Android/ABI | Vai trò | Kết quả runtime |
| --- | --- | --- | --- | --- |
| Samsung SM-A525F | Người dùng cung cấp model | Chưa xác nhận trên máy; không suy ra bản Android đang cài từ model | Máy thử đầu tiên ở phase 2 | Chưa kiểm thử |
| Thiết bị Samsung bổ sung | Chưa chọn | Chưa xác định | Mở rộng độ phủ theo phiên bản Android | Chưa kiểm thử |
| Thiết bị hãng Android khác | Chưa chọn | Chưa xác định | Kiểm tra nhiều hãng ở phase 7 | Chưa kiểm thử |

## Cách ghi kết quả sau mỗi lần thử

Ghi model, Android/API, ABI/page size nếu liên quan, phiên bản Fonesi/engine, ứng dụng gốc và phiên bản. Ghi riêng kết quả tạo/mở clone, đăng nhập, tách dữ liệu, shortcut, thông báo, cập nhật và khôi phục sau khởi động lại. Không ghi mật khẩu, token hoặc nội dung riêng của tài khoản.

Bản APK nền tảng hiện khai báo `minSdk=26`, `targetSdk=36`. Đây là cấu hình build của phần quản lý, không phải kết luận engine tương thích Android 8.0 đến mọi phiên bản mới.

Phần lưu trữ/hàng đợi đã có kiểm tra Robolectric API 26/34 với SQLite native. Đây là môi trường framework mô phỏng trong cloud, không phải kết quả thử trên hai điện thoại hoặc hai hãng.
