# Phase 3–4 — Lưu trữ và hàng đợi độc lập với engine

Người dùng đã yêu cầu tạm hoãn thử điện thoại và tiếp tục code. Tài liệu này mô tả phần đã triển khai trong cloud, không chứng nhận engine hay ứng dụng mạng xã hội đã chạy được.

## Phase 3: dữ liệu quản lý

- Room database version 1, có schema xuất trong `app/schemas/`.
- Metadata dùng khóa kép `engineKey + cloneId`; đổi engine không tự coi ID cũ là cùng tài khoản.
- Chỉ lưu metadata sau khi lệnh tạo trả về và danh sách engine xác nhận đúng package/phiên bản/chữ ký. Không tạo metadata thay cho clone thật.
- Đổi tên/nhóm có thể lưu khi engine offline; không đổi dữ liệu đăng nhập.
- Xóa metadata chỉ sau khi xác nhận bản clone vắng mặt. Nếu ID đổi package, đang chạy hoặc trạng thái không rõ, không gọi xóa. Lỗi engine không làm mất tên/nhóm trong database.
- Không dùng destructive migration. OpenHelper tắt cơ chế tự xóa database khi phát hiện corruption; schema/encoding chưa hỗ trợ phải báo lỗi và giữ dữ liệu để kiểm tra.
- Test SQLite native qua Robolectric xác minh đóng/mở database, khóa namespace, ghi cạnh tranh, schema chưa hỗ trợ và file hỏng. Không phải thử trên Samsung thật.

Các phần còn thiếu: đối chiếu đầy đủ bản orphan/missing sau lỗi engine, ghim shortcut, sinh trắc học và tích hợp engine sản phẩm.

## Phase 4: đường chạy cập nhật

1. `UpdatePlanner` xác định mục tiêu và phạm vi dùng chung/theo từng bản.
2. `UpdateQueue` tạo yêu cầu với phiên bản/chữ ký cố định và khóa chống trùng. Database giữ thứ tự chèn, không dùng đồng hồ hoặc UUID để suy ra thứ tự.
3. `UpdateRunner` lấy OS file lock; bộ chạy thứ hai không được áp dụng đồng thời.
4. Đối chiếu engine key, scope, trạng thái bản clone và ứng dụng gốc.
5. `FileSourceSnapshotter` sao chép base/split APK vào thư mục riêng theo UUID tác vụ; không ghi vào APK gốc hoặc dữ liệu tài khoản. Tính SHA-256, sync tệp, xuất bản snapshot bằng atomic move và đối chiếu nguồn trước/sau.
6. Đọc lại trạng thái sau khi chuẩn bị nguồn. Kiểm tra trạng thái pause cùng transaction ghi marker `APPLYING`.
7. Chỉ sau khi marker được lưu mới gọi `engine.update` với đường dẫn snapshot. Không báo thành công từ một receipt do engine trả về.
8. Đọc lại danh sách engine và phiên bản/chữ ký thực tế. Khi khớp, ghi thành công; nếu kết quả không rõ, giữ `REVIEW_REQUIRED` và snapshot, không tự áp dụng lại.

### Yêu cầu đối với adapter engine tương lai

- `update` phải đồng bộ hoặc đợi đến khi engine hoàn tất import bộ APK. Engine phải giữ bản mã ứng dụng của nó, không phụ thuộc vào snapshot tạm sẽ được dọn sau thành công.
- Không được bỏ qua split hoặc thay import snapshot bằng cài theo package gốc đang thay đổi mà vẫn báo đã áp dụng nguồn đã pin.
- Adapter phải phối hợp việc mở/chạy app với cập nhật. Kiểm tra trước/sau trong runner không tự tạo một giao dịch nguyên tử với việc người dùng mở app bên ngoài.
- Lỗi IPC phải báo lỗi; không trả danh sách rỗng như thể đã xóa hết bản clone.
- Không dùng receipt thành công thay cho readback; không coi metadata phiên bản khớp là bằng chứng phiên đăng nhập/dữ liệu tài khoản còn nguyên.

SDK SpaceCore trong APK probe hiện **chưa** đáp ứng/kiểm chứng toàn bộ các hợp đồng này. Vì vậy nó chưa được nối vào runner của app chính.

## Trạng thái và khôi phục

| Trường hợp | Xử lý |
| --- | --- |
| Yêu cầu trùng còn mở | Dùng lại tác vụ hiện có; không tạo nhiều lần áp dụng. |
| Đã có đúng phiên bản | Ghi `ALREADY_CURRENT`; không gọi engine cập nhật. |
| Pause trước apply | Lưu trạng thái hoãn/chờ; không áp dụng. |
| Nguồn biến mất/thay đổi | Lỗi trước apply; cần yêu cầu theo nguồn hiện tại. |
| Bản liên quan đang chạy/không rõ | Hoãn; chỉ đưa lại về chờ bằng thao tác kiểm tra lại. |
| Bị ngắt khi `PREPARING` | Có thể dọn bản tạm và chuẩn bị lại vì chưa ghi marker gọi engine. |
| Bị ngắt ở `APPLYING`/`VERIFYING` | Chuyển cần đối chiếu; không chạy lại mù quáng. |
| Readback sau gián đoạn xác nhận đúng phiên bản/chữ ký/ID | Ghi `RECOVERED_VERSION_VERIFIED`, không gọi cập nhật lần nữa. |
| Không xác định được kết quả | Giữ review, chặn tác vụ cùng package; không cho retry/cancel như một yêu cầu chưa chạy. |
| Yêu cầu package khác | Có thể tiếp tục nếu không bị pause và không có xung đột. |
| Hỏng schema/encoding/database | Không tự xóa để làm kiểm tra thành công. Báo lỗi lưu trữ. |

Không hứa exactly-once với một engine bên ngoài không có giao dịch chung. Cơ chế hiện tại tránh tự lặp thao tác khi không rõ kết quả; readback chỉ xác minh metadata phiên bản. Mất điện đột ngột, lỗi phần cứng và cập nhật ứng dụng thật vẫn cần kiểm chứng riêng.

## Giao diện và mức tích hợp hiện tại

- Tab Ứng dụng: quét/tìm ứng dụng gốc như trước.
- Tab Bản clone: hiển thị metadata đã lưu và sửa tên/nhóm; không seed bản clone giả. Ghi rõ chưa đối chiếu trạng thái chạy.
- Tab Cập nhật: lịch sử/trạng thái đã lưu, pause/cho phép hàng đợi, hủy yêu cầu chưa chạy và đưa yêu cầu hoãn về chờ. Không cho retry một tác vụ kết quả chưa rõ.
- Room Flow cập nhật giao diện từ dữ liệu đã ghi; có test nhận thay đổi hàng đợi/pause mà không polling.
- App chính vẫn dùng `UnavailableCloneEngine`. Không có scheduler/WorkManager tự gọi SDK, không có cập nhật ứng dụng thật, và chưa nối nút tạo/cập nhật clone vào engine đã kiểm chứng.

## Chạy kiểm tra

```bash
./scripts/gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Robolectric dùng API 26 và 34 với SQLite native. Các engine double chỉ ở `src/test`, không được đóng vào APK. Cache/lock của test JVM nằm trong workspace; không đổi biến môi trường HOME và không tắt kiểm tra tải artifact.

Kết quả của lượt hiện tại được ghi tại [CLOUD_VALIDATION.md](CLOUD_VALIDATION.md). Các kiểm tra điện thoại đã được hoãn, không bị đánh dấu đạt hoặc xóa khỏi yêu cầu nghiệm thu.
