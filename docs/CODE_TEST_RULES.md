# Quy luật code → test → sửa → test lại

Áp dụng cho toàn bộ [lộ trình](ROADMAP.md). Đây là quy trình phát triển, không có nghĩa mọi phase đã vượt kiểm tra.

1. **Ghi phase/bước trước khi làm:** nêu chức năng đang thực hiện và điều kiện đạt. Không nhận phase sau hoàn tất bằng một phần code chuẩn bị trước.
2. **Code theo một lát chức năng có thể kiểm tra:** ưu tiên engine và luồng mở/cách ly dữ liệu trước giao diện phụ. Không tạo bản clone giả, không mở app gốc để giả lập tài khoản riêng.
3. **Kiểm tra đúng tầng:** unit test cho logic; build/lint cho tích hợp; runtime/emulator khi phù hợp; thiết bị thật cho đăng nhập, dữ liệu, thông báo và hành vi hệ thống. Kết quả một tầng không thay thế tầng khác.
4. **Giữ kiểm tra có ý nghĩa:** không sửa assertion, bỏ test, chấp nhận checksum khác, tắt TLS hoặc hạ target SDK chỉ để báo xanh. Có thể thay đổi yêu cầu kỹ thuật khi có lý do được ghi rõ và không làm mất phạm vi sản phẩm. Nếu công cụ báo nhầm, phải chứng minh bằng kiểm tra độc lập và giới hạn ngoại lệ đúng vị trí; không tắt kiểm tra toàn dự án. Ví dụ: lint đọc dòng `tools:node="remove"` như xin quyền, trong khi kiểm tra APK xác nhận quyền đã bị gỡ; ngoại lệ chỉ áp dụng các dòng gỡ đó, APK vẫn phải qua allowlist nghiêm ngặt.
5. **Đọc lỗi trước khi sửa:** phân biệt lỗi toolchain, dependency, code dự án, SDK và giới hạn thiết bị. Chạy lại sau một chẩn đoán/thay đổi cụ thể, không lặp nguyên lệnh vô hạn.
6. **Kiểm tra lại phần bị ảnh hưởng:** khi đạt thì tiếp tục; không lặp mọi suite nếu không có thay đổi hoặc rủi ro mới. Với APK xuất cho người dùng, kiểm tra chữ ký và manifest/quyền thực tế.
7. **Ghi bằng chứng:** lệnh, exit status, số test đạt/lỗi/bỏ qua, phiên bản APK/SDK và phạm vi chạy. Phân biệt build thành công, SDK khởi tạo, bản cài tồn tại và thực sự dùng được hai tài khoản riêng.
8. **Cổng nghiệm thu phase:** chỉ đóng phase khi điều kiện của nó đạt. Người dùng đã cho phép hoãn thử điện thoại và code tiếp: có thể phát triển phần độc lập của phase sau, nhưng không đóng cổng runtime hoặc gọi engine đã đạt. Các APK/checklist điện thoại vẫn là bước kiểm chứng về sau, không phải điều kiện để tiếp tục mọi việc cloud.
9. **Không phát tán dữ liệu:** dùng dữ liệu mẫu trước tài khoản thật; không đưa mật khẩu/token/tin nhắn vào log. Gói SDK thử nghiệm được pin nguồn/checksum và tách khỏi app chính.
10. **Báo trạng thái rõ:** `đang làm`, `đạt trong cloud`, `chờ thiết bị`, `thất bại` hoặc `bị chặn`. Không gọi `chưa chạy` là `đạt`.

Mỗi cập nhật làm việc sẽ có dạng: **Phase N — bước X: việc đang làm → kiểm tra vừa chạy → kết quả → phần còn lại.** Không yêu cầu người dùng duyệt lại từng bước đã giao; chỉ cần quyết định mới khi giấy phép, chi phí, phạm vi hoặc dữ liệu vượt yêu cầu hiện có.

Lệnh chung hiện tại: `./scripts/check.sh`; thêm `--with-engine-probe` khi cần kiểm tra hai module thử. Script chạy test/build/lint và kiểm tra APK thật; dừng khi bước lỗi, không cho một lượt zero-test trở thành kết quả đạt. Các engine double ở `src/test` không phải provider sản phẩm.
