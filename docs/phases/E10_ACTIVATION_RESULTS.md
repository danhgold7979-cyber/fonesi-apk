# E10 — kiểm chứng durable guest-image activation

Ngày 28/10/2026. Kế hoạch: [E10_ACTIVATION_PLAN](E10_ACTIVATION_PLAN.md); baseline `main@c055df6` được giữ nguyên. Đây là công việc trong các phase 1/3/4/5, không thay tuyên bố hoàn tất 8 phase.

## Đầu ra mã

- `core/.../GuestImageActivationLedger.kt`: lưu active/pending code image theo clone UUID, CAS revision, chống đổi package/signer/hạ version, staging không làm thay đổi active, explicit runtime readback trước confirm, abort chỉ khi chắc chắn chưa vào SDK. Record có SHA-256 để phát hiện hỏng, ghi atomically, fsync, OS lock và kiểm tra symlink.
- `core/.../GuestImageActivationLedgerTest.kt`: 15 test mới về crash/reopen, concurrent writers, stale revisions, tamper, downgrade, signer/package mismatch, fail closed, caller mutable signer input, symlink và hai clone độc lập.
- `engine-source/.../PrivateGuestImageEvidence.kt`: đọc cluster private thông qua `PrivateApkStore.open` trước khi so package/version/signers; không fallback sang đường APK của app gốc.
- `engine-source/.../PrivateGuestImageEvidenceTest.kt`: 3 bài kiểm tra dùng signed APK fixtures thật về base/split, mismatch, và dữ liệu private bị sửa. Chỉ đánh dấu PASS nếu Gradle thực sự chạy.

## Bằng chứng hiện tại

- Kiểm tra Kotlin/JVM độc lập trong runtime công cụ: **15/15 test case đạt** (Kotlin compiler 1.9, Java 21); không gọi đây là toàn bộ bộ Gradle/JUnit của dự án.
- Adapter đã typecheck với stub đúng API trong JVM; đây chỉ là rà kiểu, không phải chạy Android hoặc signed-APK integration test.
- GitHub Actions được thêm để tự kiểm tra pinned Android toolchain, core/app, source-native và probe. Các lượt CI lỗi được điều tra, fix theo nguyên nhân; **kết luận cuối cần đọc trên branch head** chứ không dùng kết quả một run cũ.
- KHÔNG chạy Android guest, classloader/Resources, Binder, account hoặc version transition trên thiết bị từ E10.

## Cổng còn mở

E10 mới có cơ chế lưu state an toàn và đường xác minh nguồn. Chưa có một source guest provider hoàn chỉnh để cung cấp runtime readback. Vì thế `confirmFromRuntimeReadback` chỉ là contract và không được gọi trong app sản phẩm cho tới khi provider thực được xác minh. Không nâng capability split, không chạm dữ liệu guest SpaceCore. Tiếp theo: E11 Android code-image proof, E12 Context/Application/Activity/Binder và update/recovery integration; các nội dung này cần từng plan riêng.
