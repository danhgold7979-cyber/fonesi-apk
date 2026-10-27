# Đợt thử điện thoại sau — bản 0.5.0 candidate

Gói này là bản thử chưa nghiệm thu, không phải sản phẩm hoàn thiện. Chưa yêu cầu cài ngay; dùng khi đến đợt thử thiết bị đã thống nhất. Giữ nguyên các yêu cầu đa app/split còn mở trong STATUS.md.

## Trước khi thử

Ghi model, Android/API, ABI, page size, launcher và phiên bản ứng dụng. Candidate cho phép thử ARM64/API 26–34/trang 4 KB; không khóa theo model Samsung nhưng chưa chứng nhận các máy trong khoảng đó. Android mới hơn/16 KB hoặc app split vẫn bị chặn. Không hạ Android/target hay bỏ split để báo đạt.

Kiểm tra tên/hash trong `checksums.json`. Cài `fonesi-0.5.0-candidate.apk` và `fonesi-payload-r1.apk`; hai APK cần đúng bộ/chữ ký đã tạo. Đây là bản debug, chưa dùng khóa phát hành. Không đưa mật khẩu/OTP vào báo cáo hoặc chat.

## Payload trước tài khoản thật

1. Mở payload gốc, lưu GỐC và ghi ID vùng. Trong Fonesi, bật engine ở Thiết lập rồi mở lại; ghi SDK init thành công/thất bại, không suy ra chạy được chỉ từ nút bật.
2. Tạo hai bản sạch của payload, lưu A/B. Đối chiếu ID/SharedPreferences/tệp riêng khác nhau và không đổi GỐC. Mở/ghim từng icon, dừng rồi mở lại, Home/khởi động lại máy, sửa tên, xóa A và kiểm tra B/GỐC không đổi.
3. Cài `fonesi-payload-r2.apk` đè nâng cấp bản gốc, **không gỡ r1**. Dừng các bản đang dùng bằng Fonesi, kiểm tra/xếp cập nhật rồi Chạy hàng đợi. Đọc lại version 2 và ID/nhãn dữ liệu của B/GỐC còn đúng. Nếu SDK báo partial/không xác minh, giữ journal và ghi thất bại; không xóa/cài lại để giả giữ dữ liệu.
4. Thử pause, app bị đóng ở prepare/apply/verify và đối chiếu sau mở lại. Phân biệt đã xếp hàng, SDK đã trả về và phiên bản/dữ liệu thực sự đúng.

## Khóa truy cập

Bật khóa bằng xác thực hệ thống. Thử sinh trắc, mã khóa máy, hủy/thất bại, không enrollment, Home, rotation và mở trực tiếp shortcut. Bản clone không được dispatch trước auth hoặc từ callback cũ. Tắt khóa cần auth lại. Kiểm tra screenshot/recents host khi bật; không coi đây là mã hóa hoặc khóa guest đã chạy.

## Tập chọn, preview và tự động

Tìm tên tiếng Việt có/không dấu, lọc nhóm/trạng thái và chọn các mục đang hiển thị. Lọc tiếp phải vẫn báo số đã chọn đang ẩn; Bỏ chọn xóa tập chọn. Bản không đọc được trạng thái phải là chưa biết. Thử nút cập nhật từng bản/tập chọn/tất cả: preview phải nêu đủ peer dùng chung kể cả không chọn, nguồn/version, nhóm bị chặn và đã current. Đổi app gốc/peer sau preview thì confirm phải yêu cầu xem lại, không tự áp phạm vi mới.

Tự động mặc định Tắt. Chọn Chỉ kiểm tra: phải báo kết quả nhưng không xếp/import. Chọn Cập nhật khi đang mở phải có dialog đồng ý shared group; dùng payload r1/r2 kiểm tra xếp/chạy tuần tự, dedup, pause không tự resume. Thử Home/khóa/tắt mode trong batch: chưa bắt đầu import phải hoãn; bước đã vào SDK phải xác minh/giữ REVIEW rồi không lấy bước mới. Giữ REVIEW sau gián đoạn, không được tự retry. Khi đóng Fonesi không có worker cập nhật độc lập; không ghi thử nền là đạt từ việc mở lại rồi cập nhật.

Trong Thiết lập, đối chiếu model/API/ABI/page size và dung lượng của chính Fonesi với Android. Số không có quyền đọc phải ghi chưa biết; cache nằm trong data, không cộng hai lần. Kiểm tra link pin/quyền chỉ mở thiết lập Fonesi.

## Sao lưu tên/nhóm

Chọn nơi xuất qua Android, quay lại/mở khóa rồi xác nhận xuất và đọc lại. File chỉ có tên/nhóm/package/ref; không có session/engine ID/token/khóa. Chọn tệp nhập, mở khóa, đọc preview rồi map rõ bản nhận cùng app. Thử chọn một phần, duplicate target, stale token, file hỏng/quá 2 MiB và mất quyền provider. Dữ liệu sai phải không ghi một phần hoặc tạo/xóa guest. Thử rotation/process restart khi picker trả kết quả; chưa được tự IO hay apply trước xác nhận.

## Các app bắt buộc và nghiệm thu

Chỉ sau payload/cách ly đạt mới dùng tài khoản thử do người dùng kiểm soát. Ghi riêng Facebook/Messenger/Zalo/TikTok/Threads/X/Shopee/YouTube, phiên bản và từng chức năng đăng nhập, thông báo, media, camera, Play Services, cập nhật giữ phiên. App split đang bị chặn là yêu cầu chưa đạt, không tự loại khỏi phạm vi. Đo tài nguyên/nền và thử một hãng khác ở đợt này; lỗi runtime phải được sửa và thử lại trước kết luận hoàn thiện. Không có cam kết tránh ban hoặc chạy đồng thời vô hạn.
