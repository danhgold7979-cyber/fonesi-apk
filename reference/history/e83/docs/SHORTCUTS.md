# Phase 3 — Mở bản clone từ màn hình chính

Phần cloud của bước 15–16. Kiểm thử điện thoại vẫn được hoãn theo yêu cầu. App chính chưa có engine sản phẩm nên các nút này chưa mở được ứng dụng clone thật; không tạo bản mẫu để lấp danh sách.

## Luồng đã triển khai

1. Mỗi lần lưu một bản clone đã được engine xác nhận, Room tạo một UUID ngẫu nhiên riêng. Tên/nhóm thay đổi không làm đổi UUID. Xóa metadata rồi tạo lại sẽ có UUID mới, kể cả engine cấp lại cùng mã bản clone.
2. “Đưa ra màn hình” đối chiếu metadata, namespace engine, danh tính package và trạng thái cập nhật. Nếu đạt, gửi `requestPinShortcut` cho launcher. Người dùng vẫn xác nhận trong giao diện Android; `true` chỉ là nhận yêu cầu, chưa chứng minh đã ghim.
3. Shortcut dùng tên đã đặt, icon app nguồn có huy hiệu F; khi không đọc được icon nguồn thì dùng icon Fonesi. Không xuất dynamic shortcut hàng loạt hoặc áp một hạn mức clone từ quota dynamic shortcut.
4. Intent đi tới activity riêng trong Fonesi, chỉ mang URI `fonesi://clone/<UUID>`. Không nhận package, engine hay mã bản clone từ extras. Activity không đăng ký liên kết web/browsable. URI phải đúng định dạng; không chấp nhận query, fragment, port hoặc path phụ.
5. Khi mở, app tìm lại UUID từ DB, kiểm tra engine/ID/package và journal. Sau đó chỉ gọi `engine.launch` cho ID đã đối chiếu. Không mở app gốc hoặc trang web làm phương án thay thế.
6. Mở, tạo, xóa và runner cập nhật dùng cùng OS file lock. Nếu còn marker chuẩn bị/áp dụng/xác minh hoặc kết quả cần đối chiếu cho package, không mở/tạo/xóa cho tới khi được xử lý. Tác vụ còn chờ chưa tự chặn mở; updater vẫn phải hoãn ứng dụng đang chạy.
7. Sửa tên gửi đồng bộ cho các shortcut đã ghim. Khi metadata đã xóa, lần đồng bộ tiếp theo gửi vô hiệu hóa shortcut cũ. Trước lần đồng bộ đó, bộ định tuyến đã từ chối UUID không còn tồn tại. Đồng bộ chạy sau đổi tên và khi quét lại/mở trình quản lý; launcher có thể xử lý chậm hoặc từ chối.

Nút “Mở” trong trình quản lý đi qua cùng controller với shortcut. Nếu SDK ném lỗi hoặc kết quả cập nhật chưa rõ, app báo cần kiểm tra và không tự thử lại. ViewModel giữ một lần dispatch qua thay đổi cấu hình màn hình. Hàm SDK trả về chưa chứng minh giao diện khách hay tài khoản đã mở thành công.

## Nâng cấp dữ liệu

Room nâng schema 1 → 2 bằng migration có transaction: giữ nguyên tên, nhóm, namespace, mã bản, thời gian, queue và lựa chọn pause; thêm UUID riêng cho từng hàng và unique index. Không dùng fallback xóa dữ liệu. Schema JSON phiên bản 1 vẫn được giữ để kiểm thử nâng cấp thật với SQLite native.

UUID là định danh ổn định cho một lần tạo, không phải mật khẩu hoặc cơ chế khóa ứng dụng. Lối tắt không chứa dữ liệu đăng nhập. Đây vẫn là shortcut phụ thuộc Fonesi và engine, không phải APK cài độc lập có UID riêng.

## Hợp đồng cần giữ khi nối engine

- Adapter phải cung cấp ID ổn định và không tái sử dụng ID cho một instance khác khi metadata cũ còn tồn tại. Namespace phải thay đổi khi đổi provider hoặc mất vùng dữ liệu. Không thể phát hiện đầy đủ việc SDK tự thay tài khoản dưới cùng ID/package chỉ bằng URI.
- Sau khi nhận lệnh mở, adapter phải báo `running=true` hoặc chưa biết trước khi nhả khóa; updater không được coi một lần khởi chạy bất đồng bộ là đang rảnh.
- Mọi thao tác thay đổi engine phải dùng cùng khóa; khóa UI không khóa các tiến trình khách đã chạy. SDK vẫn phải dừng/hoãn cập nhật đúng cách.
- Giao diện guest, ghim/tên/icon trên Samsung/launcher khác, cold start, thông báo và tài khoản thật còn cần thử runtime. Không suy ra các kết quả đó từ Robolectric.
- Khi bổ sung khóa sinh trắc học ở phase 6, phải áp dụng cùng chính sách cho cả trình quản lý và activity nhận shortcut.

## Checklist runtime để dùng về sau

Không yêu cầu thực hiện ngay. Chỉ dùng khi engine sản phẩm đã được tích hợp và vượt kiểm tra dữ liệu mẫu:

1. Ghi model, Android/API, ABI/page size, phiên bản app nguồn, engine và launcher. Tạo hai bản cùng ứng dụng, đăng nhập dữ liệu thử khác nhau, ghim từng bản và xác nhận màn hình hệ thống.
2. Mở từng icon khi Fonesi chưa chạy, sau khi đóng app và sau khởi động lại. Đối chiếu đúng dữ liệu bản A/B; thử cả nút Mở trong Fonesi. Ghi riêng dispatch SDK và màn hình guest thực sự xuất hiện.
3. Đổi tên/nhóm, quét lại và đối chiếu tên/icon/huy hiệu trên màn hình chính. Hủy hộp thoại ghim phải không báo đã tạo biểu tượng.
4. Xóa A rồi tạo bản mới. Shortcut A cũ không được mở bản mới, B hay app gốc; B vẫn giữ dữ liệu. Kiểm tra khi launcher chưa kịp đồng bộ và sau đồng bộ.
5. Thử lúc runner giữ khóa, sau gián đoạn apply/verify và khi engine không sẵn sàng. Không được mở sai bản hoặc tự cập nhật lại kết quả chưa rõ.
6. Thử xoay màn hình khi định tuyến, chạm lặp, từ chối launcher, engine lỗi và thiếu dữ liệu quản lý. Ghi nhận các kết quả thật; chưa đánh dấu đạt chỉ vì xuất được APK.

## Giới hạn của bài kiểm tra launcher mô phỏng

Robolectric 4.15.1 `ShadowShortcutManager.disableShortcuts` chuyển ID vào bản đồ disabled nhưng không cập nhật `ShortcutInfo.isEnabled`. Đã đối chiếu bytecode thư viện: bài kiểm tra xác nhận shortcut cũ bị từ chối ghim lại với lỗi disabled, shortcut còn lại vẫn được nhận, và controller từ chối UUID đã xóa. Không sửa code sản phẩm để phù hợp cờ mô phỏng, không tắt kiểm tra. Trạng thái icon thật trên launcher vẫn chưa được kiểm chứng.
