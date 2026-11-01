# Lộ trình triển khai: 8 phase, 32 bước, 4 mốc thử điện thoại

## Cách làm và phạm vi cam kết

Yêu cầu sản phẩm đủ để bắt đầu. Khả năng engine đáp ứng yêu cầu vẫn cần chứng minh bằng mã chạy thật. Không coi build APK thành công, unit test đạt hoặc có biểu tượng ngoài màn hình là đã clone được ứng dụng.

Triển khai theo các phase bên dưới, tiếp tục những việc đã được giao mà không yêu cầu duyệt lại từng bước. Mỗi phase có đầu ra và điều kiện chuyển bước. Khi một phần chưa đạt, sửa hoặc đánh giá phương án khác; không tự đánh dấu hoàn tất để chuyển sang phần giao diện.

Theo yêu cầu mới, tiếp tục code và kiểm tra cloud trước, gom thử điện thoại về đợt nghiệm thu sau. Các phần quét, Kotlin/domain logic, lưu trạng thái, giao diện và đóng gói có thể phát triển/kiểm tra trong cloud. Đăng nhập, cách ly dữ liệu thực tế, thông báo và ổn định trên Samsung vẫn cần thiết bị thật. Đợt nghiệm thu đó có thể phát hiện lỗi phải code/sửa/test tiếp; không hứa chỉ thử một lần là xong. Emulator không thay thế bằng chứng trên Samsung.

Hiện cloud chưa có thiết bị kết nối. Người dùng sẽ tự cài APK và tự đăng nhập tài khoản thử nghiệm trên điện thoại; không cần root. Không yêu cầu mật khẩu, mã OTP hoặc token trong chat. Máy thử đầu tiên là Samsung SM-A525F; phiên bản Android đang cài cần ghi nhận trước mốc thử thiết bị đầu tiên. Thông tin còn thiếu này không chặn khảo sát engine trong cloud.

**Điều chỉnh theo yêu cầu mới:** được triển khai trước toàn bộ phần có thể code/kiểm tra trên cloud, kể cả adapter SDK ứng viên trong bản tích hợp có bật chủ động, dù cổng runtime phase 1–2 chưa đạt. Không buộc người dùng cài thử giữa mỗi phase. Điều này thay đổi thứ tự phát triển, không biến kiểm tra chưa chạy thành đạt và không chốt engine sản phẩm. Chỉ nghiệm thu clone/tài khoản/thiết bị sau khi thực sự thử; các giới hạn engine chưa đáp ứng vẫn là yêu cầu chưa hoàn thành.

Mục tiêu là một APK dùng trên nhiều thiết bị, không khóa theo SM-A525F. Chọn máy thử đầu tiên để có kết quả cụ thể, rồi mở rộng kiểm tra theo [DEVICE_MATRIX.md](DEVICE_MATRIX.md); chưa thử một model không đồng nghĩa model đó không hỗ trợ.

## Phase 0 — Chốt yêu cầu và nền tảng cloud — bước 1–4

1. Tổng hợp yêu cầu và giới hạn: nhiều ứng dụng, nhiều vùng tài khoản, mở trực tiếp bằng biểu tượng, không root, cập nhật theo ứng dụng gốc. Ghi rõ sự phụ thuộc bộ quản lý và giới hạn tài nguyên.
2. Kiểm tra mã hiện có: phân biệt chức năng đã code, chức năng mới có hợp đồng API, chức năng chưa có và kết quả chưa được kiểm thử. Không coi `UnavailableCloneEngine` là engine.
3. Cài/pin toolchain; build APK nền tảng và chạy kiểm tra logic lập kế hoạch cập nhật. Lưu hướng dẫn tái tạo, không đưa khóa ký phát hành vào Git.
4. Ghi báo cáo baseline, đường dẫn APK, phiên bản và các kiểm tra đã/chưa chạy để so sánh các bản sau.

**Đầu ra:** project có thể build, tài liệu yêu cầu và báo cáo thực tế.

**Điều kiện đạt:** build thành công, có kiểm thử thực sự và không hiển thị chức năng clone giả. Phần cloud của phase này đã thực hiện: 15 unit test đạt và lint không có lỗi; chức năng quét chưa được thử trên điện thoại.

## Phase 1 — Chọn và kiểm chứng engine — bước 5–8

5. Lập bảng ứng viên: giấy phép, phiên bản mã nguồn, Android/ABI hỗ trợ, yêu cầu SDK/NDK, base/split APK, Google Play Services và cách engine quản lý phiên bản dùng chung/từng bản. Tuyên bố của README chỉ là thông tin cần kiểm tra.
6. Rà đường chạy khởi tạo, quyền, dependency/native binary, việc ghi/gửi log và các thay đổi thông tin thiết bị. Loại khỏi tích hợp các hành vi không thuộc yêu cầu; không thu thập tài khoản hoặc bí mật đăng nhập.
7. Build engine từ nguồn có quyền sử dụng hoặc SDK đã được cấp phép. Giữ TLS/chữ ký/checksum; chạy các kiểm tra khả thi trong môi trường cloud. Ghi riêng phần không chạy được do thiếu thiết bị/ABI.
8. Tích hợp một adapter tối thiểu; thử tạo, mở, đọc trạng thái và xóa vùng dữ liệu với ứng dụng kiểm thử do dự án kiểm soát. Xác định chính xác mô hình dữ liệu và phạm vi cập nhật. Chuẩn bị APK PoC chỉ khi phần nền an toàn để thử.

**Đầu ra:** quyết định engine có bằng chứng, build tái lập và APK kiểm chứng tối thiểu; hoặc báo cáo vì sao từng ứng viên không đạt.

**Điều kiện chuyển sang thử tài khoản:** nguồn/giấy phép đủ rõ; không có xuất dữ liệu không được kiểm soát; engine thực sự chạy mã ứng dụng trong vùng riêng. Nếu chưa có bằng chứng runtime trong cloud thì ghi rõ và xác minh ở phase 2 trước khi nhập tài khoản.

**Điểm dừng kỹ thuật:** nếu không có engine đạt, tiếp tục đánh giá ứng viên khác hoặc sửa phần được phép. Nếu cần SDK thương mại, báo đúng yêu cầu và chi phí được cung cấp; không tự mua/liên hệ bên thứ ba. Không thay bằng webview, app gốc hoặc đổi sang yêu cầu root/hồ sơ giới hạn mà không thống nhất lại phạm vi.

## Phase 2 — Chứng minh clone thật trên Samsung — bước 9–12

9. Đưa bản PoC lên Samsung không root. Xác minh thông tin máy/Android/ABI và thử ứng dụng kiểm thử trước; sau đó tạo Facebook phụ A và B bên cạnh Facebook gốc. Cài đặt APK trên máy cần thao tác xác nhận của người dùng.
10. Người dùng đăng nhập hai tài khoản thử nghiệm do mình kiểm soát. Kiểm tra dữ liệu không trộn, đăng xuất A không đăng xuất B, làm sạch/xóa A không ảnh hưởng B hoặc Facebook gốc. Không tuyên bố cách ly tương đương sandbox hệ điều hành chỉ từ mô hình engine.
11. Đóng/mở lại, hệ thống dừng tiến trình, khởi động lại máy và thử mất mạng/thu hồi quyền. Kiểm tra camera, chọn ảnh, liên kết đăng nhập, video và thông báo; lưu kết quả từng tính năng, kể cả lỗi.
12. Ghim hai biểu tượng thử nghiệm và xác minh từng biểu tượng mở thẳng đúng bản/tài khoản. Gửi checklist kết quả; sửa và thử lại đúng lỗi đã phát hiện.

**Đầu ra:** APK PoC và biên bản **mốc điện thoại 1**.

**Điều kiện đạt:** Facebook gốc và hai bản phụ mở đúng vùng tài khoản, các kiểm tra cách ly/khôi phục thiết yếu đạt. Tính năng bắt buộc như thông báo chưa đạt phải được ghi là lỗi còn mở, không chuyển thành "đã hỗ trợ đầy đủ".

Theo thứ tự mặc định, ưu tiên chứng minh engine trước giao diện phụ. Theo điều chỉnh mới của người dùng, có thể làm trước giao diện/lưu trạng thái độc lập qua hợp đồng engine; phải giữ chức năng chạy thật bị khóa khi engine chưa có và ghi rõ rủi ro cần điều chỉnh nếu đổi engine.

## Phase 3 — Quản lý vòng đời bản clone — bước 13–16

13. Thiết kế dữ liệu Room theo ID bản clone ổn định: app nguồn, ID engine, tên, trạng thái và phiên bản thực. Không lưu mật khẩu. Thiết kế migration từ phiên bản đầu.
14. Hoàn thiện tạo bản sạch, đọc danh sách từ engine, đổi tên và xóa có xác nhận. Xử lý tạo/xóa thất bại hoặc bị đóng tiến trình giữa chừng bằng đối chiếu dữ liệu, không để bản "ma" hoặc xóa nhầm.
15. Hoàn thiện shortcut theo ID ổn định: mở đúng bản, xử lý bản bị xóa, tên mới, launcher không hỗ trợ và xác nhận ghim của Android. Lối tắt không mở nhầm app gốc khi clone lỗi.
16. Đối chiếu trạng thái sau khi khởi động lại hoặc cập nhật chính Fonesi. Khi gỡ app gốc, giữ dữ liệu clone và báo khả năng mở thực tế của engine; không tự xóa tài khoản.

**Đầu ra:** bộ quản lý clone cốt lõi hoạt động cùng engine.

**Điều kiện đạt:** dữ liệu quản lý khớp engine; mọi thao tác nhắm đúng ID; test migration và lỗi gián đoạn đạt. Kiểm tra nhanh lại shortcut/vòng đời trên điện thoại khi thay đổi có ảnh hưởng, không gửi APK chỉ vì sửa tài liệu.

## Phase 4 — Cập nhật theo app gốc và hàng đợi — bước 17–20

17. Chuẩn bị nguồn base/split APK nhất quán; đọc phiên bản/chữ ký; xác minh quyền đọc và tính toàn vẹn. Nếu app gốc thay đổi trong lúc chuẩn bị, bỏ nguồn tạm và lập lại. Không trộn split của hai phiên bản.
18. Lưu hàng đợi và trạng thái bền vững. Gom tác vụ trùng, chỉ một tác vụ cập nhật chạy cùng lúc. Có kiểm tra lại nguồn, trạng thái đang dùng và phạm vi ảnh hưởng ngay trước khi thực thi.
19. Nối các nút Cập nhật, cập nhật bản đã chọn và Cập nhật tất cả vào thực thi thật. Nếu engine dùng chung mã APK, giao diện phải báo cập nhật ảnh hưởng cả nhóm; không giả lập cập nhật riêng. Hoãn bản đang dùng, cho tạm dừng ở ranh giới an toàn và chỉ báo thành công sau khi đọc lại phiên bản.
20. Thử cập nhật thật từ app gốc, hết dung lượng, nguồn bị gỡ, khác chữ ký, tác vụ trùng và tiến trình bị đóng ở các điểm quan trọng. Đối chiếu trạng thái engine trước khi tiếp tục, không chạy lại mù quáng. Kiểm tra dữ liệu và phiên đăng nhập sau cập nhật trên điện thoại.

**Đầu ra:** hàng đợi thực thi và biên bản **mốc điện thoại 2**.

**Điều kiện đạt:** không mất dữ liệu trong các tình huống đã kiểm tra; không thực thi trùng; báo đúng phạm vi/thành công/lỗi; giữ phiên đăng nhập trong các trường hợp thử hoặc ghi rõ khi app yêu cầu đăng nhập lại. Không hứa rollback an toàn bằng cách hạ APK.

Trạng thái dự kiến: chờ → chuẩn bị → cập nhật → xác minh → thành công/thất bại. Có trạng thái hoãn do đang sử dụng, tạm dừng và gián đoạn cần đối chiếu. "Đã xếp hàng" không phải "đã cập nhật".

## Phase 5 — Mở rộng nhiều ứng dụng — bước 21–24

21. Tích hợp/kiểm thử Zalo và TikTok trước; tạo ít nhất hai vùng tài khoản mỗi app. Ghi riêng mọi lỗi đăng nhập, thông báo và chức năng thiết yếu.
22. Kiểm thử Messenger, Threads, X, Shopee và YouTube. Không suy ra Messenger hoạt động từ Facebook hoặc suy ra Google sign-in hoạt động từ việc mở được màn hình YouTube.
23. Lập ma trận ứng dụng × phiên bản ứng dụng × Android × model/ABI. Trạng thái gồm đã kiểm thử, có giới hạn, chưa kiểm thử, không hỗ trợ; không có nhãn "không bị ban".
24. Trên điện thoại, thử nhiều loại app xen kẽ, mở đúng tài khoản, nhận thông báo, cập nhật nhóm và xóa một bản trong khi giữ các bản khác. Sửa lỗi rồi thử lại phần bị ảnh hưởng.

**Đầu ra:** danh sách tương thích có bằng chứng và biên bản **mốc điện thoại 3**.

**Điều kiện đạt:** các ứng dụng bắt buộc nằm trong phạm vi phát hành phải đạt tiêu chí đã thống nhất. App chưa đạt vẫn là yêu cầu chưa hoàn thành. Nếu phải thu hẹp danh sách phát hành, cần người dùng quyết định; không âm thầm bỏ app khỏi yêu cầu.

## Phase 6 — Hoàn thiện trải nghiệm và bảo vệ dữ liệu — bước 25–28

25. Hoàn thiện quét, tìm kiếm, phân nhóm, tên/màu/huy hiệu và thao tác nhiều bản. Mọi cảnh báo tương thích lấy từ kết quả đã ghi, không chỉ từ tên gói.
26. Thêm khóa giao diện bằng sinh trắc học với đường xử lý phù hợp khi máy không hỗ trợ hoặc thay đổi đăng ký. Giải thích rõ gỡ Fonesi có thể làm mất bản clone; không gọi khóa giao diện là mã hóa toàn bộ dữ liệu nếu engine không cung cấp.
27. Hoàn thiện màn hình quyền/thông báo, dung lượng, hạn chế pin và lỗi. Nếu quyền hệ điều hành được cấp cho host và engine không tách được theo từng bản, phải báo đúng giới hạn đó.
28. Sao lưu/khôi phục cấu hình, tên và nhóm; dữ liệu xuất không chứa mật khẩu/token. Thử dữ liệu nhập sai/hỏng và lọc thông tin nhạy cảm trong chẩn đoán. Sao lưu phiên đăng nhập/chuyển máy không nằm trong cam kết bản đầu.

**Đầu ra:** bản beta sử dụng được, có hướng dẫn và kiểm tra dữ liệu.

**Điều kiện đạt:** luồng chính hoàn chỉnh, không có nút báo thành công cho chức năng chưa có; không xuất dữ liệu riêng trong log; lỗi được giải thích và có cách xử lý thực tế.

## Phase 7 — Ổn định, tương thích thiết bị và đóng gói — bước 29–32

29. Đo tài nguyên trên máy thật: một bản, ba bản, năm bản và tăng tiếp nếu máy cho phép; đo riêng số bản lưu được với số bản chạy đồng thời. Đây là các mức thử, không phải giới hạn sản phẩm. Chọn cảnh báo từ số đo thay vì cam kết chạy vô hạn.
30. Thử sử dụng/hạn chế nền tối thiểu hai giờ với nhóm ứng dụng ưu tiên trên thiết bị đích; thử khởi động lại, tắt màn hình và mạng thay đổi. Kiểm tra qua đêm là kiểm tra bổ sung. Chỉ kết luận trong phạm vi thời gian và cấu hình đã thử.
31. Kiểm thử ít nhất một thiết bị Samsung và một hãng khác trước khi tuyên bố đã xác minh nhiều hãng. Xác minh tương thích API/ABI và thư viện native/page size khi liên quan; không nói hỗ trợ mọi máy từ kết quả của một máy.
32. Chạy kiểm tra hồi quy cuối, build APK bằng khóa phát hành được bảo quản đúng cách, xác minh chữ ký/cài nâng cấp giữ dữ liệu và tạo ghi chú phát hành. Cung cấp APK, checksum, hướng dẫn và danh sách giới hạn. Thực hiện **mốc điện thoại 4** để nghiệm thu.

**Đầu ra:** APK phát hành thử nghiệm đã kiểm chứng trên danh sách thiết bị/app công bố, mã nguồn và báo cáo kiểm tra.

**Điều kiện hoàn tất:** các yêu cầu bắt buộc trong phạm vi đã chốt đạt kiểm tra; không còn lỗi mất/trộn dữ liệu đã biết; build/chữ ký/nâng cấp đạt; công bố đúng phần đã và chưa hỗ trợ. Không tự đăng cửa hàng, gửi thông tin cho bên thứ ba hoặc publish dịch vụ nếu chưa được yêu cầu.

## Bốn mốc đưa APK sang điện thoại

| Mốc | Khi nào | Cần xác minh |
| --- | --- | --- |
| 1 | Cuối phase 1 → phase 2, khi có PoC engine | Thử payload ngoại tuyến trước để đóng phần runtime của phase 1, rồi hai bản Facebook/hai tài khoản, cách ly, biểu tượng, khôi phục và chức năng thiết yếu của phase 2. |
| 2 | Phase 4 | Cập nhật từ app gốc, hàng đợi, dữ liệu/phiên, gián đoạn và báo lỗi. |
| 3 | Phase 5 | Các ứng dụng mục tiêu còn lại và hoạt động nhiều loại app. |
| 4 | Phase 7 | Độ ổn định, thiết bị hãng khác, cài nâng cấp và APK phát hành. |

Đây là bốn mốc chính, không phải giới hạn số lần sửa/thử. Khi lỗi chỉ xuất hiện trên máy thật, cần bản sửa và kiểm tra lại trước khi đóng lỗi. Mỗi APK thử có số phiên bản, thay đổi và checklist ngắn; phản hồi ưu tiên mã lỗi/chẩn đoán đã lọc, không yêu cầu nội dung riêng của tài khoản.

## Làm liên tục như thế nào

- Trong cloud: code → chạy kiểm tra phù hợp → đọc lỗi → sửa → build lại; cập nhật tài liệu trạng thái theo bằng chứng. Không yêu cầu người dùng nhắc tiếp sau từng bước đã được giao.
- Không chờ người dùng thử để làm những phần độc lập có ích. Có thể chuẩn bị lưu trạng thái, kiểm thử nguồn cập nhật hoặc tài liệu trong lúc chờ; không hoàn thiện sâu những phần phụ thuộc kiến trúc engine chưa chứng minh.
- Khi tới mốc cần điện thoại: cung cấp APK và checklist; người dùng cài và tự đăng nhập. Cloud hiện không điều khiển được điện thoại của người dùng, vì vậy không tự đánh dấu các kiểm tra này là đạt.
- Chỉ yêu cầu quyết định mới khi có vấn đề thật sự vượt phạm vi: SDK cần mua/giấy phép, ứng dụng bắt buộc không tương thích, thay đổi kiến trúc hoặc dữ liệu có nguy cơ bị xóa.
- Không hứa "một lần code chắc chắn thành công" hoặc thời hạn hoàn thành toàn bộ trước khi vượt phase 1–2. Sau PoC, mới có cơ sở ước lượng công sức phần còn lại.

## Trạng thái tại lúc lập lộ trình

- Phase 0 phần cloud: đã có kết quả trong [VALIDATION.md](VALIDATION.md).
- Phase 1: đã dựng APK probe riêng với SDK ứng viên; build/static audit đạt, runtime payload còn chờ điện thoại. Engine sản phẩm chưa chốt. Xem [STATUS.md](STATUS.md).
- Phase 2–7: chưa đạt điều kiện hoàn tất. Giao diện quét và logic lập kế hoạch đã có chỉ là nền tảng, không chứng minh các phase sau hoàn thành.
- Theo yêu cầu hoãn điện thoại, phần code hiện tại tập trung lưu metadata/điều phối cập nhật cloud của phase 3–4. Xem [STATUS.md](STATUS.md). Bài thử payload/Facebook vẫn là mốc nghiệm thu runtime về sau; không chặn phần code độc lập.
