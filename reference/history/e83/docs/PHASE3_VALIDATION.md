# Phase 3, bước 15–16 — kết quả phần cloud

Ngày 26/10/2026. Bản `0.3.0-cloud`, versionCode `3`. Kiểm thử điện thoại vẫn hoãn theo yêu cầu; chưa có engine sản phẩm trong app chính. Bản này chưa tạo, mở hay cập nhật được ứng dụng clone thật.

## Kiểm tra đã đạt

Lệnh cuối: `./scripts/check.sh`, exit code `0`.

| Nhóm | Kết quả | Phạm vi |
| --- | --- | --- |
| Core | 46 test, 0 thất bại/lỗi/bỏ qua | Planner, queue, snapshot, khóa và recovery đã có. |
| Lưu trữ Android mô phỏng | 24 test, 0 thất bại/lỗi/bỏ qua | 12 tình huống trên API 26 và 34, SQLite native. |
| Shortcut/lifecycle/migration mô phỏng | 30 test, 0 thất bại/lỗi/bỏ qua | 15 tình huống trên API 26 và 34, SQLite native/OS lock/Android shortcut shadows. |
| Tổng | **100 test đạt** | Không chạy SDK ảo hóa hoặc ứng dụng mạng xã hội. |
| App chính | Build debug và lint thành công | 0 lỗi lint, 4 cảnh báo. |
| APK cuối | Chữ ký, package/SDK, quyền và component đạt kiểm tra | Không Internet, không chứa SDK engine thử. |

Một số task Gradle tái sử dụng đầu ra up-to-date nếu đầu vào không đổi. Không diễn giải việc đó thành chạy mới từng test ở mọi lần gọi script.

Các hành vi mới có bằng chứng cloud:

- URI chuẩn chỉ chứa UUID, dùng component tường minh; extras không thay được engine/ID/package.
- Hai bản cùng package dispatch đúng ID đã chọn; inspect để ghim không gọi launch.
- Đổi tên/đóng mở DB giữ định danh shortcut; xóa rồi tạo lại cùng engine ID không làm UUID cũ trỏ sang bản mới.
- Chặn engine không sẵn sàng/đổi namespace, ID trùng, package thay đổi, thiếu metadata và journal không đọc được; không tự xóa dữ liệu hoặc fallback sang app gốc.
- Marker prepare/apply/verify/review cùng package chặn mở/tạo/xóa. Tác vụ mới đang chờ và package không liên quan không tự chặn mở.
- Luồng mở và vòng đời không lấy được OS lock khi updater giữ khóa; khi gọi engine launch vẫn đang giữ khóa. Exception từ SDK không thành công giả hoặc được tự retry.
- Pin gửi Intent đúng đích, label Unicode và định danh riêng; không xuất hàng loạt dynamic shortcut.
- Gửi đồng bộ tên shortcut; ID đã xóa bị hệ mô phỏng từ chối ghim lại vì disabled, các bản khác vẫn được nhận. Cờ `isEnabled` của shadow có giới hạn được giải thích tại [SHORTCUTS.md](SHORTCUTS.md).
- Dựng DB schema 1 từ JSON đã lưu, chèn metadata Unicode/queue/pause, mở bằng schema 2 và đối chiếu dữ liệu sau migration/reopen. UUID duy nhất và unique index được kiểm tra.

Engine double chỉ ở `src/test`; không được liên kết làm engine sản phẩm. Shortcut manager được Robolectric mô phỏng; không chứng minh launcher thật đã tạo/đổi/vô hiệu hóa icon. Không có kiểm thử giao diện Compose hoặc SDK guest trên thiết bị trong mốc này.

## APK đã kiểm tra

- Đường dẫn: `app/build/outputs/apk/debug/app-debug.apk`.
- Package: `vn.fonesi.app`, minSdk `26`, targetSdk `36`.
- Kích thước: `9929115` byte.
- SHA-256: `301497aa5b3737da56274e5f6f199bff48875da9b8c3470fc4a0c3d503561b05`.
- Chữ ký debug đã xác minh; không phải gói phát hành.
- Quyền duy nhất trong APK: `vn.fonesi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Component public chỉ gồm `MainActivity` và `shortcuts.CloneLaunchActivity`; `ProfileInstallReceiver` được giữ dưới quyền hệ thống `DUMP`. Activity nhận shortcut không có intent-filter, không giữ history/recents, task affinity rỗng.

Report máy đọc: `app/build/reports/cloud-verification.json`; XML/HTML test và lint ở thư mục `app/build/` và `core/build/`.

Bốn cảnh báo lint còn lại: `CustomSplashScreen` tại activity định tuyến, hai thông báo dependency có bản mới và gợi ý KTX bitmap có từ trước. Không suppress cảnh báo hoặc hạ target để vượt kiểm tra. Activity định tuyến thực hiện đối chiếu/mở và hiển thị lỗi; kiểm tra APK độc lập vẫn giới hạn component/intent như trên.

Hai module probe không thay đổi trong mốc này và không được chạy lại trên thiết bị. Các kết quả probe trước đây nằm trong [PHASE1_VALIDATION.md](PHASE1_VALIDATION.md), không phải chứng nhận engine cho bản mới.

## Còn thiếu để nghiệm thu

Adapter engine sản phẩm, dữ liệu/account isolation thực tế, ghim/mở/cold start trên One UI và launcher khác, giữ phiên đăng nhập qua cập nhật, thông báo và sinh trắc học vẫn chưa được xác minh. Các điều kiện runtime của phase 1–3 còn mở; không đóng toàn phase chỉ từ 100 test cloud. Checklist runtime để dùng sau có ở [SHORTCUTS.md](SHORTCUTS.md).
