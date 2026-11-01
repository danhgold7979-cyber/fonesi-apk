# Xuất/nhập tên và nhóm — 0.5.0

Plan S2 được ghi trước code trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Đây là backup metadata quản lý, không sao lưu tài khoản, APK, dữ liệu guest hoặc chuyển phiên đăng nhập sang máy khác.

## Hợp đồng tệp

```json
{"format":"vn.fonesi.configuration","version":1,"clones":[{"ref":1,"package":"vn.fonesi.payload","label":"Bản A","group":"Thử nghiệm"}]}
```

`ref` là số nguyên dương duy nhất trong tệp, không phải ID guest. `group` có thể null. Label dài 1–80 ký tự UTF-16, nhóm 1–40; không nhận khoảng trắng ở biên, control character hoặc surrogate hỏng. Mã gói có dạng hợp lệ, tối đa 255 ký tự. Codec giữ nguyên Unicode, không ép kiểu số/chuỗi.

Đọc UTF-8 nghiêm ngặt tối đa 2 MiB; từ chối key/ref trùng, field thiếu/lạ, version/format lạ, JSON/trailing content sai, Unicode hỏng hoặc dữ liệu vượt ngân sách. Encode kiểm tra toàn bộ và giới hạn output trước khi mở tệp đích. Không có đường dẫn, launch token, ID/namespace engine, signer, hàng đợi, lựa chọn tự động, chính sách khóa, PIN hoặc session trong format. Field kiểu `session`/`lock` bị từ chối.

## Luồng SAF và xác nhận

Android CreateDocument/OpenDocument chọn tệp, không xin quyền storage rộng. Callback chỉ stage URI/action trong SavedStateHandle, kể cả lúc UI đang khóa; không đọc/ghi tự động sau picker hoặc process restart. Sau khi mở khóa, người dùng xác nhận xử lý. Chỉ nhận content URI của DocumentsContract/provider khác UID Fonesi; chặn provider SDK/host nội bộ. Giữ quyền URI persistable khi provider cho phép; provider có thể thu hồi quyền hoặc không hỗ trợ đọc lại.

Xuất chỉ đọc metadata quản lý, có thể dùng khi engine chưa sẵn sàng. Sau ghi phải mở/parse/đối chiếu lại mới báo đã xác minh. Nếu không đọc lại được, UI báo kết quả ghi chưa xác minh; lỗi provider có thể để lại tệp chưa đầy đủ. Không xóa tệp hoặc dữ liệu gốc để xử lý lỗi.

Nhập có preview theo trang; người dùng chọn rõ từng ref → bản nhận hiện có cùng package/provider. Không tự ghép theo tên/thứ tự hoặc tạo guest từ file. Có thể chọn một phần; không được chọn hai ref vào cùng target. Bản trùng tên cần đổi tên để phân biệt trước khi map.

Apply lấy cùng operation lock với vòng đời/update, kiểm tra auth và SDK readback ID/package/version/signers, rồi đổi tên/nhóm trong một Room transaction. Target mất/đổi engine/package hoặc auth mất phải không ghi một phần. Shortcut đồng bộ sau commit; lỗi launcher không làm metadata đã ghi thành chưa ghi. Các policy auth/auto, journal và vùng tài khoản không đổi qua import.

## Kiểm tra và giới hạn

Robolectric API 26/34 dùng native SQLite kiểm tra codec, giới hạn, dữ liệu sai, mapping, transaction rollback, stale target, khóa và pending URI không tự IO. Việc chặn provider nội bộ có test staging; SAF provider thật/ghi đọc lại/quyền persistable/rotation trên OEM còn phải thử theo [PHONE_TEST_GUIDE.md](PHONE_TEST_GUIDE.md). Không coi test staging là bằng chứng đã xuất/nhập qua provider thật.
