# E10 — kế hoạch triển khai trước code: durable guest image activation

Ngày: 28/10/2026. Nhánh: `develop/guest-runtime-e10-20261028`. Baseline bất biến: `c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` trên `main`.

## Đối chiếu master plan và vị trí thực tế

Đã kiểm tra `docs/ROADMAP.md` (8 phase / 32 bước), `docs/EXECUTION_PLAN.md`, `docs/STATUS.md`, `docs/GUEST_CODE_VALIDATION.md` và các hợp đồng `PrivateApkStore`, `GuestCodeLoader`, `ManagedGuestEngine`. E9: 309 test được ghi nhận, build/lint app/probe đạt theo báo cáo; KHÔNG có bằng chứng Android guest execution, classloader/resources thực tế hoặc clone/tài khoản. E10 là lát cắt chuẩn bị engine source trong phase 1, 3, 4 và 5, không tuyên bố đóng bất cứ phase nào.

**Điểm thiếu cần xử lý:** E7 có private APK clusters bất biến; E8 có native libs; E9 có code image loader. Chưa có cơ chế bền vững gắn clone ID với active code image, đóng băng ứng viên cập nhật, phát hiện thay đổi sau restart, hoặc chống tự xác nhận lần update gián đoạn. Thêm cơ chế này độc lập trước khi nối guest lifecycle/Binder.

## Phạm vi E10

1. Viết `core/GuestImageActivationLedger.kt` không phụ thuộc Android: state từng clone UUID, revision, active image và pending image; không chứa cookie, password, user data, token. Các image chứa cluster UUID, package, version Long và tập SHA-256 signer.
2. `stage` yêu cầu expected revision (CAS), image evidence được xác minh lại; không overwrite bản pending; không đổi active, không hạ version, không đổi package/signer trên một clone đang hoạt động.
3. `confirm` chỉ cập nhật active khi revision/pending/verified image khớp với readback được caller cung cấp từ *engine runtime thực*, sau khi đối chiếu lại private APK store. Chỉ ghi "code image pointer confirmed", TUYỆT ĐỐI không ghi guest UI, account isolation hoặc package Android chạy thành công.
4. `abortBeforeSdk` chỉ là thao tác bền vững bỏ pending *khi caller chứng minh SDK chưa bắt đầu*; không tự xóa cluster, account hoặc dữ liệu guest. Khi không chứng minh được, giữ pending chờ reconcile, không auto retry.
5. Lưu atomically trong private root, file per clone, SHA-256 record, giới hạn metadata, chặn symlink/path traversal, OS file lock + khóa trong tiến trình, fsync; read lỗi phải fail closed, không tự xóa state hỏng.
6. Viết adapter `engine-source/GuestImageEvidence.kt` chuyển cluster từ `PrivateApkStore.open` (xác minh crypto, bytes và exact file set) sang image record. Không tích hợp source provider/capability split vào `app` hoặc thay SpaceCore.
7. Kiểm thử: create/stage/confirm, đổi phiên bản, signer mismatch/downgrade, stale revision, pending crash/reopen, invalid readback, tamper record, symlink root/file, two writers, cross-clone isolation; kiểm tra adapter với APK fixture ký thật. Ưu tiên JVM thật; Android runtime, dedicated guest processes, Binder/context/lifecycle vẫn NOT RUN.
8. Tạo CI source-safe cho nhánh phát triển (khi khả thi), giữ các kết quả GitHub Actions là bằng chứng riêng. Không nhận "build" nếu chưa có job green.

## Quy trình và cổng nghiệm thu

- Check master/phase plan → commit E10 plan → code/test → commit kết quả thực tế.
- E10 chỉ đạt **JVM/logic** nếu test chạy và có bằng chứng; build Gradle/source chỉ đạt khi thật sự được thực thi. Không sửa assertion/tắt lint/hạ target/tải SDK không kiểm checksum để đổi màu báo cáo.
- Nếu thiếu mạng/Android SDK ở runtime hiện tại, dùng compile/test Kotlin độc lập cho lớp thuần, ghi rõ kết quả; Android/Gradle chưa chạy giữ là pending, không suy ra từ kotlinc.
- Sau E10: E11 guest process/context/application lifecycles; E12 Binder/service/provider/intent; E13 split/isolated feature resources; E14 update/recovery/provider integration; E15 Android probe thật; E16 app matrix, UX còn thiếu, release/multi-device. Mỗi mốc phải có kế hoạch chi tiết mới và bằng chứng; các bước còn lại trong 8 phase / 32 task không bị bỏ.

## Điều kiện không được vượt qua bằng giả lập

Không mở app gốc thay clone; không lấy session đang có; không import binary/fork chưa audit; không gọi component code-image là guest đã chạy. `main` và guest cũ không bị thay đổi; không force-push, không merge hoặc phát hành bản không nghiệm thu.
