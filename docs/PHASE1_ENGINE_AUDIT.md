# Phase 1 — Quyết định kỹ thuật và phạm vi kiểm chứng

## Các ứng viên

### NewBlackbox — chưa tích hợp

Nguồn: `ALEX5402/NewBlackbox`, commit `89b59836c66f173756a4ae258cf379a957649820`.

Các phát hiện khi đọc nguồn:

- Manifest Bcore khai báo 323 mục quyền, nhiều proxy component exported; chưa có biện pháp host bảo vệ các điểm vào này.
- `Utils/VirtualSpoof.cpp` có constructor tự cài hook thay thông tin thiết bị khi nạp thư viện; `AntiDetection.cpp` nằm trong danh sách native build. Đây không phải chức năng cần cho phép thử cách ly dữ liệu.
- Có chức năng gửi log ra dịch vụ bên ngoài; cấu hình mẫu có đích gửi mặc định. Chưa đưa bất kỳ dữ liệu người dùng vào engine.
- `BPackageSettings` chứa một gói mã và bản đồ trạng thái theo user; đường dẫn APK/lib nằm theo package, dữ liệu theo user/package. Đây là bằng chứng tĩnh của mô hình mã dùng chung, chưa phải kiểm thử cập nhật.
- Đường `CopyExecutor` chỉ sao chép base APK trong nhánh storage. Chưa chứng minh hỗ trợ đầy đủ split APK.

Quyết định: không đưa nguyên trạng vào Fonesi và không chạy với tài khoản thật. Chưa build engine này vì chưa qua điều kiện tích hợp tĩnh; không báo nó là build thất bại.

### SpaceCore — chỉ dùng cho probe ngoại tuyến

Nguồn và quyền sử dụng công khai được ghi tại [NOTICE](../third_party/spacecore/NOTICE.md). Tác giả cho phép dùng SDK miễn phí, kể cả thương mại; SDK là mã đóng. Không sao chép keystore hoặc code app demo.

Thông tin thu được:

- AAR được lấy từ commit cố định và xác minh SHA-256 trước build.
- API có tạo/xóa user, cài/mở/xóa ứng dụng theo user, đọc thông tin và trạng thái chạy.
- Tài liệu nêu ARM32/ARM64 và Android 6–14. Native core ARM64 có LOAD alignment 4096 byte. Probe chỉ ARM64/API 26–34/trang 4096; đây là điều kiện thử, không phải danh sách hỗ trợ đã xác minh.
- AAR gốc có 192 mục quyền (có trùng tên), 2552 activity, 52 provider. Không chấp nhận nguyên manifest vào sản phẩm.
- Không tìm thấy literal URL trong lớp Java bằng quét chuỗi đơn giản. Điều này **không chứng minh** SDK không có mạng/telemetry; mã native/obfuscation chưa được audit đầy đủ.

Biện pháp giới hạn của probe:

1. Tách thành application ID riêng `vn.fonesi.probe`; app chính không phụ thuộc SDK. Chỉ thêm module khi có `-PincludeEngineProbe=true`.
2. Chưa chủ động gọi `FCore.init` trước khi người dùng bật và mở lại probe. Có chặn cấu hình ngoài phạm vi, ghi dấu khởi tạo dang dở và báo lỗi thay vì giả thành công.
3. Chỉ được cài package payload do dự án kiểm soát; xác minh payload cùng chữ ký với probe. Chỉ thao tác user 1001/1002 của bài thử. Không có chức năng chọn app mạng xã hội.
4. Overlay gỡ 180 tên quyền thừa kế; chỉ giữ quyền foreground-service/thông báo và quyền nội bộ chữ ký. Không có Internet, SMS, danh bạ, điện thoại, vị trí, quyền đọc kho tệp hay QUERY_ALL_PACKAGES trong APK cuối.
5. Các component thừa kế mặc định yêu cầu quyền chữ ký của host. Launcher công khai của probe là ngoại lệ không yêu cầu quyền; JobService/ProfileInstaller có đúng quyền hệ thống tương ứng. Kiểm tra lại trên manifest APK đã đóng gói, không chỉ đọc XML nguồn.
6. Tắt các tùy chọn hide-device/root/SIM/VPN/path, preloading và optimization rule trong adapter khởi tạo; không yêu cầu SDK giả danh thiết bị. Việc cấu hình này không phải chứng nhận mọi hành vi nội bộ SDK đã được kiểm soát.
7. Giữ target SDK 36; không hạ target để che lỗi tương thích. Disable variant release của probe; không phát hành nó như sản phẩm clone đã hoàn thành.

## Code và phép thử hiện có

- `SpaceCoreProbe`: gọi SDK thật để tạo, đọc lại, mở, dừng và xóa payload. Không triển khai bộ máy ảo hóa giả hoặc gọi mở app gốc để báo thành công.
- `probe-payload`: tạo ID ngẫu nhiên, lưu nhãn bằng SharedPreferences và tệp riêng. Người dùng kiểm tra ba vùng GỐC/A/B.
- `ProbePolicyTest`: kiểm tra biên API, ABI và page size; cộng 15 test planner hiện có thành 19 unit test. Không test nào trong số này chạy engine ARM trên điện thoại.
- `verify_probe_apks.py`: kiểm tra chữ ký APK, cùng signer của hai bản thử, package/target SDK, allowlist quyền chính xác, quyền bảo vệ component exported và ABI. Kiểm tra thêm app chính không có quyền mới hoặc `libfcore.so`.

## Xử lý lint và cảnh báo thực

Lần lint đầu báo 32 lỗi trên các dòng `uses-permission tools:node="remove"`, coi chúng là yêu cầu quyền. Manifest trong APK đã xác nhận chỉ có allowlist dự kiến. Ngoại lệ lint được đặt **chỉ trên các dòng gỡ quyền**; không tắt lint toàn dự án hoặc tạo baseline bỏ lỗi. Script audit APK độc lập vẫn từ chối bất kỳ quyền mới nào ngoài allowlist.

Cảnh báo `Aligned16KB` của thư viện cũ là giới hạn thật, không bị tắt. Vì không có mã native của SDK để rebuild, probe chặn cấu hình trang khác 4096 thay vì tuyên bố hỗ trợ 16 KB. Cảnh báo ABI cũng phù hợp với giới hạn thử ARM64 hiện tại. Chưa chốt phạm vi Android/ABI của sản phẩm chính theo giới hạn probe này.

## Cổng runtime còn thiếu

SDK chưa chạy trong thiết bị kết nối. Chưa xác minh init, launch, cách ly, khôi phục, xóa riêng hoặc tương thích target SDK 36. Không thể đánh dấu phase 1 hoàn tất từ kết quả build/static audit.

Bước tiếp theo: [bài thử dữ liệu mẫu trên điện thoại](PHASE1_PHONE_TEST.md), rồi sửa theo kết quả hoặc thay engine. Chỉ khi bài thử đạt mới làm PoC tài khoản Facebook ở phase 2. Đây là phần kiểm tra sớm của mốc điện thoại đầu tiên, trước khi dùng tài khoản thật.
