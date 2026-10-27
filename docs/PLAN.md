# Kế hoạch triển khai Fonesi

## 1. Yêu cầu sản phẩm đã thống nhất

- Mục tiêu một APK dùng trên nhiều model Samsung và các hãng Android khác; không yêu cầu root. Không khóa hoạt động theo model máy thử đầu tiên. Phạm vi Android/ABI và tính năng công bố phải dựa trên engine và kết quả thử thực tế.
- Quét ứng dụng có thể khởi chạy trên điện thoại trong phạm vi Android cho phép; ưu tiên ứng dụng mạng xã hội, thương mại và nội dung.
- Danh sách mục tiêu: Facebook, Messenger, Zalo, TikTok, Threads, X, Shopee, YouTube. Tương thích phải được xác minh riêng theo phiên bản ứng dụng, Android và thiết bị.
- Một ứng dụng có thể có nhiều bản clone; mỗi bản có tên, biểu tượng mở riêng và vùng dữ liệu tài khoản riêng.
- Tạo bản sạch, người dùng tự đăng nhập. Không sao chép mặc định phiên đăng nhập của app gốc.
- Đưa biểu tượng ra màn hình chính; chạm biểu tượng mở thẳng đúng bản clone, không phải chọn lại trong bộ quản lý. Android/launcher có thể yêu cầu xác nhận ghim biểu tượng.
- Trải nghiệm mở trực tiếp không đồng nghĩa với việc tạo một APK cài đặt độc lập. Hướng ảo hóa phụ thuộc vào bộ quản lý được cài trên máy; phải giải thích việc này trong hướng dẫn sử dụng.
- Có tạo, đặt tên, tìm kiếm, phân nhóm, xóa, yêu cầu ghim biểu tượng và xem trạng thái.
- Không đặt giới hạn sản phẩm tùy ý về số bản; giới hạn thực tế phụ thuộc tài nguyên, Android và engine.
- Ứng dụng hệ thống không thuộc phạm vi bắt buộc của bản đầu.
- Bản thử nghiệm phân phối bằng APK cài trực tiếp. Phát hành cửa hàng và thương mại hóa cần đánh giá riêng.

## 2. Quy tắc cập nhật từ ứng dụng gốc

Nguồn cập nhật là phiên bản ứng dụng đã được người dùng cài/cập nhật trên điện thoại. Fonesi không tự thay chữ ký hoặc đổi mã gói APK bên thứ ba.

Các thao tác người dùng:

1. Kiểm tra phiên bản gốc và trạng thái bản clone.
2. Chọn Cập nhật cho một bản.
3. Chọn nhiều bản hoặc Cập nhật tất cả.
4. Xem tiến độ, lỗi, bản đang chờ và kết quả thực tế.
5. Tạm dừng giữa các tác vụ và thử lại tác vụ lỗi.

Cập nhật tự động là tùy chọn. Tác vụ thủ công và tự động dùng cùng một hàng đợi để không chạy trùng. Bản 0.5.0 có OFF/kiểm tra/cập nhật khi UI đang mở và đã mở khóa; chạy nền độc lập chưa triển khai vì init/FGS SDK chưa được chứng minh. Chi tiết [UPDATE_AUTOMATION.md](UPDATE_AUTOMATION.md); không gọi mode foreground là scheduler nền đã hoàn tất.

### Trình tự xử lý

1. Nhận biết thay đổi gói gốc và đối chiếu lại khi ứng dụng mở hoặc người dùng yêu cầu.
2. Đọc mã gói, phiên bản, chữ ký/kế thừa chữ ký và bộ APK base/split đang cài. Phải kiểm thử split APK; không mặc định chỉ sao chép `base.apk` là đủ.
3. Lập nguồn cập nhật nhất quán. Nếu app gốc thay đổi trong khi chuẩn bị, hủy nguồn tạm và lập lại; không cài lẫn tệp của hai phiên bản.
4. Kiểm tra tương thích, dung lượng và phạm vi ảnh hưởng mà engine báo cáo.
5. Chỉ xử lý một tác vụ cập nhật tại một thời điểm. Hoãn nếu có bản bị ảnh hưởng đang sử dụng hoặc không xác định được trạng thái an toàn; không tự ngắt phiên đang dùng.
6. Chỉ thay mã ứng dụng bằng cơ chế cập nhật của engine; giữ vùng dữ liệu riêng. Không xóa rồi cài lại để làm cập nhật có vẻ thành công.
7. Đọc lại phiên bản thực tế từ engine rồi ghi kết quả. Không đánh dấu thành công chỉ vì lệnh đã được xếp hàng.
8. Khi lỗi, giữ dữ liệu, dừng các tác vụ phụ thuộc của cùng ứng dụng, báo nguyên nhân và cho thử lại.

### Engine dùng chung mã ứng dụng

Phải xác định ngay trong thử nghiệm engine liệu phiên bản APK được quản lý theo bản clone hay dùng chung theo mã gói.

- Nếu theo từng bản: có thể cập nhật riêng và lần lượt thực sự.
- Nếu dùng chung: một lần cập nhật ảnh hưởng mọi bản của mã gói đó. Giao diện phải nói rõ phạm vi này; không giả lập các bản đang có phiên bản khác nhau. Hàng đợi xử lý từng nhóm ứng dụng, còn bước khởi chạy/di chuyển dữ liệu từng bản chỉ làm riêng nếu engine hỗ trợ.
- Không hứa quay lui an toàn chỉ bằng hạ phiên bản APK: dữ liệu có thể đã được ứng dụng nâng cấu trúc.

## 3. Kiến trúc dự kiến

- Giao diện Android native: Kotlin và Jetpack Compose.
- Siêu dữ liệu: Room; tùy chọn: DataStore. Không lưu mật khẩu tài khoản vào các bảng quản lý.
- Bộ quét: dùng API PackageManager và khai báo package visibility phù hợp; không coi tên gói là bằng chứng tương thích.
- Engine adapter: các thao tác kiểm tra khả năng, tạo bản, mở, xem trạng thái thực, cập nhật và xóa. Chỉ chốt hợp đồng API sau khi kiểm tra engine thật.
- Shortcut: ID ổn định gắn với đúng bản clone; kiểm tra bản còn tồn tại trước khi mở. Bản bị xóa phải có thông báo rõ ràng và xử lý lối tắt cũ theo khả năng launcher.
- Hàng đợi: trạng thái lưu bền vững, chống tác vụ trùng, ghi kết quả theo phiên bản thật và khôi phục sau khi tiến trình bị đóng. Cơ chế chạy nền được chọn theo engine và hạn chế Android, không mặc định một worker có thể làm mọi việc.
- Không gọi mở app gốc hoặc mở web thay cho việc chạy một vùng tài khoản riêng.

Không chọn phiên bản Android tối thiểu, SDK mục tiêu hoặc quyền nhạy cảm chỉ để làm engine cũ có vẻ chạy được. Chốt chúng sau kiểm chứng và ghi rõ thiết bị đã thử.

## 4. Khảo sát engine ban đầu

Đây là quan sát mã nguồn/tài liệu, **không phải kiểm thử khả năng chạy ứng dụng**. Các nguồn khảo sát nằm ngoài checkout sản phẩm; chưa sao chép engine vào dự án.

| Nguồn | Quan sát | Quyết định hiện tại |
| --- | --- | --- |
| [FBlackBox/BlackBox](https://github.com/FBlackBox/BlackBox), HEAD `a13734339f85a85b4400926f7142557cb6f97dd9` | Checkout hiện tại chỉ còn tài liệu thông báo đã xóa dự án. | Không dùng làm dependency có thể tái lập. |
| [asLody/VirtualApp](https://github.com/asLody/VirtualApp), HEAD `d6b35d39c7166c7e5f2c3c93da03da17cd6a8892` | README ghi mã công khai ngừng cập nhật từ 2017; bản thương mại cập nhật riêng và cần giấy phép. | Chưa tích hợp, chưa có SDK/giấy phép bản hiện hành. |
| [twoyi/twoyi](https://github.com/twoyi/twoyi), HEAD `dd052a07a812e742ec7583da393466ccc066a8b1` | Tài liệu mô tả container Android, hỗ trợ máy chủ Android 8.1–12, hệ điều hành khách Android 8.1. | Chưa chứng minh phù hợp các ứng dụng mục tiêu và Android hiện nay. |
| [ALEX5402/NewBlackbox](https://github.com/ALEX5402/NewBlackbox), HEAD `89b59836c66f173756a4ae258cf379a957649820` | Có mã engine và khai báo Apache-2.0; README tuyên bố tương thích Android mới. Có chức năng gửi log qua dịch vụ bên ngoài trong `LogSender.java`, được gọi bởi chức năng gửi log khi có cấu hình đích. | Chưa tích hợp. Cần kiểm tra nguồn gốc/giấy phép các thành phần, kiểm soát việc xuất log/quyền, build, split APK, cách cập nhật và kiểm thử thiết bị. Không tải hoặc chạy APK của engine với tài khoản người dùng. Tuyên bố README không phải bằng chứng tương thích. |
| [FSpaceCore/SpaceCore](https://github.com/FSpaceCore/SpaceCore), HEAD `3826a2fa1ac492fbbe7435ccb52074c1e2b702de` | SDK mã đóng, tác giả công bố cho phép dùng miễn phí/kể cả thương mại. Có API theo user; AAR có native 4 KB, tài liệu Android 6–14, chưa có API nhập bộ split. | Giữ probe ngoại tuyến và thêm build candidate bật chủ động với UI chính. Build/static audit đạt, runtime/cách ly chưa chạy; chưa chốt engine sản phẩm. Xem [candidate](INTEGRATED_CANDIDATE.md). |

Không tự động liên hệ nhà cung cấp, mua SDK, gửi thông tin hoặc tải APK không rõ nguồn gốc để thử tài khoản.

## 5. Các phase và điều kiện chuyển bước

Lộ trình chuẩn nằm tại [ROADMAP.md](ROADMAP.md): **8 phase (0–7), 32 bước và 4 mốc thử điện thoại**. Tài liệu đó thay thế cách chia M0–M4 trước đây.

Thứ tự: nền tảng → chọn engine → PoC clone thật trên Samsung → quản lý vòng đời → thực thi cập nhật → nhiều ứng dụng → trải nghiệm/bảo vệ dữ liệu → ổn định/phát hành.

Theo yêu cầu cập nhật, tiến hành code/cloud test trước và gom các mốc điện thoại sang đợt thử sau. Mốc phase 2 vẫn xác minh engine trước tài khoản; các mốc sau kiểm tra cập nhật, nhiều ứng dụng và nghiệm thu trong đợt đó. Chưa vượt engine/PoC thì không coi giao diện hoặc bản build thành công là sản phẩm clone hoạt động. Lỗi runtime có thể cần sửa và thử lại.

## 6. Dữ liệu, cảnh báo và giới hạn

- Dữ liệu trong mỗi bản là một vùng riêng, không hứa một file duy nhất có thể mang sang máy khác và giữ đăng nhập.
- Sao lưu ban đầu chỉ gồm cấu hình, tên và nhóm. Phiên đăng nhập/Keystore cần đánh giá riêng.
- Xóa phải có xác nhận; gỡ ứng dụng gốc phải giữ siêu dữ liệu và báo khả năng mở thực tế. Không tự xóa vùng clone theo.
- Cảnh báo dung lượng, quyền, hạn chế nền và phiên bản chưa kiểm thử dựa trên bằng chứng thu thập được.
- Nhật ký không chứa token, mật khẩu, nội dung tin nhắn hoặc dữ liệu riêng của ứng dụng clone. Không xuất danh sách ứng dụng lên máy chủ mặc định.
- Có khóa giao diện quản lý và shortcut bằng sinh trắc mạnh hoặc mã khóa Android trong bản 0.5.0; không mô tả đây là mã hóa mọi dữ liệu nếu engine chưa cung cấp. Xem [ACCESS_CONTROL.md](ACCESS_CONTROL.md).
- Không bảo đảm tài khoản không bao giờ bị khóa, app không bao giờ văng hoặc số bản chạy đồng thời vô hạn.
- Không triển khai giả mạo danh tính thiết bị, vượt kiểm tra của nền tảng hoặc tự động tương tác hàng loạt trong phạm vi này.

## 7. Trạng thái thực tế

- Yêu cầu và kế hoạch: đã ghi trong tài liệu này; đủ để bắt đầu triển khai từng mốc, chưa phải cam kết đã đáp ứng kỹ thuật.
- GitHub read: đã xác minh.
- Engine sản phẩm: chưa chốt. SDK ứng viên có trong probe riêng và build candidate tích hợp UI; build cloud mặc định không chứa SDK. Chưa kiểm chứng runtime; split APK còn bị chặn.
- Đã code scanner, metadata/registry Room, mở/ghim, tạo/thêm bản sạch/dừng/xóa và hàng đợi journal/snapshot/recovery; candidate đã nối các lệnh vào API SDK thật. Chưa thực thi được xác minh trên thiết bị hoặc tài khoản thật.
- Bản 0.5.0 bổ sung khóa hệ thống, backup tên/nhóm có preview/map/transaction, tìm/lọc/chọn nhiều, preview cập nhật atomic và tự động foreground có opt-in. Plan trước code và kết quả riêng từng phần trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md); bằng chứng [PHASE6_VALIDATION.md](PHASE6_VALIDATION.md). Không đóng toàn phase 6/7 khi phần tùy biến/thiết bị/release còn thiếu.
- Đánh giá E1/E2 vẫn chưa tìm được đường nhập/cập nhật split đủ điều kiện tích hợp; bằng chứng cụ thể trong [ENGINE_SPLIT_AUDIT.md](ENGINE_SPLIT_AUDIT.md). Đây là yêu cầu kỹ thuật còn mở, không chỉ là runtime chờ máy.
- Toolchain cloud đã cài; đã chạy unit test và build APK nền tảng. Kết quả kiểm tra cuối cùng được ghi riêng trong `VALIDATION.md` sau khi hoàn tất.
- Đã build/audit APK probe; test runtime engine, chạy ứng dụng clone thật, test trên Samsung/hãng khác: chưa chạy. Trạng thái cập nhật ở [STATUS.md](STATUS.md).
- Máy thử đầu tiên người dùng cung cấp: Samsung SM-A525F. Phiên bản Android đang cài chưa được xác nhận; không suy ra từ model. Chưa có kết luận thiết bị/ứng dụng nào đã được hỗ trợ qua kiểm thử.
- Máy thử đầu tiên không phải giới hạn sản phẩm. Theo dõi thiết bị và phạm vi tương thích tại [DEVICE_MATRIX.md](DEVICE_MATRIX.md). Người dùng đã yêu cầu hoãn thử điện thoại để tiếp tục các phần cloud; xem [CLOUD_BACKEND.md](CLOUD_BACKEND.md).
