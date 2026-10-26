# Phase 1 — Thử engine bằng dữ liệu mẫu, không dùng tài khoản thật

Mục đích: xác minh engine có thực sự mở ứng dụng và giữ hai vùng dữ liệu riêng trên Samsung SM-A525F. Build/lint/unit test không thay thế bài kiểm tra này.

## Hai APK cần cài

- `probe-payload/build/outputs/apk/debug/probe-payload-debug.apk` — tên trên máy: **Fonesi dữ liệu mẫu**.
- `engine-probe/build/outputs/apk/debug/engine-probe-debug.apk` — tên trên máy: **Fonesi thử engine**.

Đây là hai ứng dụng thử nghiệm riêng với app Fonesi chính. Không cần root hoặc ADB. Android có thể yêu cầu cho phép cài APK từ nguồn bạn mở. Cả hai APK không có quyền Internet; payload không yêu cầu quyền. Không nhập mật khẩu, OTP hoặc tài khoản mạng xã hội vào hai app này.

SDK thử nghiệm là mã đóng từ bên thứ ba, có nguồn/checksum pin trong [NOTICE](../third_party/spacecore/NOTICE.md). Chưa xác minh runtime. Chỉ bật SDK khi đồng ý chạy thử với dữ liệu mẫu. Nếu không thử, không bật SDK.

## Checklist

1. **Cài cả hai APK cùng bản build.** Mở *Fonesi dữ liệu mẫu*, nhập `GỐC`, bấm **Lưu và đọc lại**. Hai dòng SharedPreferences/tệp riêng đều phải là `GỐC`. Ghi ID vùng dữ liệu hiện trên màn hình.
2. **Mở Fonesi thử engine.** Ghi Android/API, ABI và kích thước trang bộ nhớ đang hiển thị. Probe hiện chỉ cho thử ARM64, API 26–34, trang 4096 byte. Nếu bị chặn, gửi nguyên văn thông báo; không tìm cách vượt chặn. Đây là giới hạn SDK ứng viên, không phải khóa model của sản phẩm.
3. **Bật SDK.** Bấm **Bật SDK và đóng bản thử**, rồi tự mở lại biểu tượng. Việc đóng ở bước này là chủ động. Nếu khởi tạo lỗi, app văng hoặc bị giữ ở cảnh báo lần khởi tạo trước, ghi kết quả là lỗi; không nhập tài khoản hay bấm thử lại liên tục.
4. **Tạo và mở vùng A.** Chờ kết quả tạo, bấm mở A. Phải xuất hiện màn hình dữ liệu mẫu. Nhập `A`, lưu và ghi ID. Nếu chỉ thấy app gốc hoặc chỉ thông báo đã yêu cầu mở mà không có màn hình mới, chưa đạt.
5. **Tạo và mở vùng B.** Nhập `B`, lưu và ghi ID. ID của GỐC/A/B phải khác nhau. Mở qua lại ba vùng, cả hai dòng dữ liệu phải giữ đúng nhãn tương ứng.
6. **Thử khôi phục.** Đóng/mở các vùng; khởi động lại điện thoại, mở lại probe rồi mở A/B. Kiểm tra lại ID và nhãn. Thất bại ở bước nào thì ghi bước đó, không đánh dấu đạt chỉ vì đã tạo được bản cài.
7. **Thử xóa riêng.** Trong probe bấm **Dừng vùng A**, sau đó **Xóa dữ liệu mẫu vùng A…** và xác nhận. Bản B và bản gốc phải vẫn mở được với dữ liệu cũ. Không dùng thao tác xóa trên dữ liệu cá nhân.

## Phản hồi ngắn

```text
Máy: SM-A525F
Android / API:
Trang bộ nhớ:
SDK khởi tạo: đạt / lỗi / bị chặn
Mở A / B: đạt / lỗi
ID GỐC, A, B khác nhau: có / không
Nhãn GỐC / A / B giữ riêng: có / không
Sau khởi động lại: đạt / lỗi
Xóa A, B và GỐC còn nguyên: có / không
Bước lỗi + thông báo (nếu có):
```

Không cần gửi dữ liệu tài khoản hoặc nội dung riêng trên điện thoại. Khi xong, có thể **Tắt SDK và đóng bản thử**; gỡ hai ứng dụng thử nghiệm sẽ xóa dữ liệu mẫu của chúng. Chưa dùng bản probe để thử Facebook/Zalo/TikTok.

## Ý nghĩa kết quả

- Các bước trên đạt: có bằng chứng ban đầu cho runtime/vùng dữ liệu của payload trên cấu hình máy này; xem xét đóng phần runtime bước 8 của phase 1.
- Chưa chứng minh thông báo, đăng nhập mạng xã hội, nhiều app, cập nhật hoặc mọi máy đều chạy được.
- Lỗi/không thử được: phase 1 chưa hoàn tất; dùng kết quả để sửa hoặc thay engine. Không chuyển sang thử tài khoản thật để thay cho bài kiểm tra này.
