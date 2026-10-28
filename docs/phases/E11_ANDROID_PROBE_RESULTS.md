# E11 — Kết quả phát triển code-image probe

**Trạng thái:** IMPLEMENTED SOURCE / Android runtime NOT RUN / release NOT ACCEPTED.

## Mã đã được viết

- `probe-payload/src/main/res/values/guest_code_probe.xml`: resource marker cố định, chỉ dữ liệu synthetic.
- `native-runtime-probe/src/main/java/vn/fonesi/nativeprobe/GuestCodeProbeService.java`: nhận run UUID, tra nguồn duy nhất `vn.fonesi.payload`, lấy APK/signers từ Android PackageManager; private store phải verify toàn bộ cluster, SHA và chữ ký; gắn pending image bằng E10 ledger; chỉ trong process `:fonesi_guest_loader` mới cấu hình native routing bằng một cặp thư mục synthetic thuộc host, bật native, gọi `GuestCodeLoader`, đọc class payload từ DexClassLoader và string Resources riêng.
- `GuestCodeProbeActivity.java`: giao diện test kỹ thuật, đọc report đúng run UUID, timeout nếu child chết, không coi dispatch là pass.
- Manifest `queries` đúng một package; một Activity test explicit và service `exported=false`. Build probe độc lập app Fonesi chính; không thay SpaceCore/candidate.
- CI kiểm tra `native-runtime-probe` và `probe-payload` bằng Kotlin/Android toolchain pinned. Kết quả cuối của lượt này phải đọc trực tiếp trên GitHub Actions; trước khi job xong KHÔNG ghi compile/lint PASS.

## Cổng E11

- *Source review/local*: đã tạo mã và wired build/manifest, đang kiểm tra.
- *Gradle build/lint*: PENDING theo GitHub Actions branch.
- *Android hardware/emulator actual code loading*: **NOT RUN**; không có biên nhận run UUID thực từ Android.
- *Guest Activity, Application, binder, quyền, account isolation, social apps*: **NOT IMPLEMENTED / NOT RUN**.
- *APK Fonesi release*: **NOT BUILT/ACCEPTED** từ mốc này.

## Cách nghiệm thu sau khi có Android/ADB

1. Dùng gói probe/payload do dự án build, kiểm tra chữ ký/hash trước khi cài; không cài APK nguồn không rõ.
2. Cài `vn.fonesi.payload` và `vn.fonesi.nativeprobe` trên emulator hoặc máy test đã cho phép; không sử dụng tài khoản thật.
3. Mở explicit Activity: `adb shell am start -n vn.fonesi.nativeprobe/.GuestCodeProbeActivity`. Quan sát màn hình PASS/FAIL của đúng run UUID; lỗi hoặc timeout giữ FAIL.
4. PASS chỉ khi `dex_class_loader=true`, `private_resources=true`, `activation_pending=true`, `guest_context_executed=false`, `guest_activity_executed=false`. Nếu native/routing/resources lỗi, sửa code trước khi mở provider thật.
5. Đọc log/artifact test, không gửi password/OTP/cookie. Dùng test trên thiết bị Android ARM64/One UI riêng sau emulator trước bất kỳ account trial nào.

**Không** lấy PASS nạp class/resources làm bằng chứng toàn engine virtualization hay app social hoạt động. Mốc tiếp theo: guest Context/lifecycle và Binder routing theo kế hoạch riêng.
