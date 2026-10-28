# E11-Android — kế hoạch kiểm chứng thực thi trên AVD riêng

Ngày 28/10/2026. Đầu vào: `main@c055df6`, [8-phase/32-step roadmap](../ROADMAP.md), [E10 plan/results](E10_ACTIVATION_PLAN.md), [E11 plan/results](E11_ANDROID_PROBE_PLAN.md). Cổng source/build **PASS** của nhánh E10/E11 tại `a9aa5a8d53b0c82eabcc071fd0e0fa19fdcc612c`, [GitHub Actions 40638158372](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40638158372). Core/app 299 tests, source-native build/host test/ARM64 JNI, E11 native probe và synthetic payload đều qua build/lint. **Android execution vẫn NOT RUN.**

## Phạm vi và ranh giới

Đây là kiểm chứng AVD x86_64 *không root*, không tài khoản mạng xã hội và không dữ liệu người dùng. Test đúng hai APK đã được build trên CI ở SHA `a9aa5a8`, giữ hash:
- `native-runtime-probe-debug.apk`: SHA-256 `4ab18e6408546fbc4effd1efad24c4fdf044212e4f6d26456089cf817fe5551c`.
- `probe-payload-debug.apk`: SHA-256 `c8296bb1d62af7186081e2180a22a721318ad4b0c3bc06101350aa69f0fbd114`.

Chỉ chạy trên Android emulator do workflow tạo, gắn serial cụ thể. Không quét/đụng thiết bị người dùng hoặc dùng dữ liệu của app thật. Không thay đổi `main` hay tích hợp provider SpaceCore.

## Lát cắt có thể kiểm tra

1. Ghi run UUID cố định vào Activity probe qua explicit intent `run_id`; Activity chuẩn hóa UUID, không tin biên nhận cũ và vẫn giữ timeout. Không xuất dữ liệu account.
2. Thêm `scripts/run_guest_code_emulator.py`, chạy bounded: pin SHA APK → tạo AOSP34 x86_64 AVD riêng → boot `sys.boot_completed`/ADB/package-service → cài đúng 2 APK theo serial → mở explicit `GuestCodeProbeActivity` và đợi kết quả đúng run UUID bằng `run-as`. Nếu crash/timeout/Android runtime load fail phải FAIL hoặc BLOCKED chính xác, không báo pass từ launch.
3. Ưu tiên KVM có trên GitHub-hosted Linux khi thực sự present; nếu không có KVM, ghi rõ và dừng nhanh hoặc software boot bounded, không retry vô hạn. AVD có ownership/PID/serial rõ, cleanup chỉ process thuộc runner.
4. Giữ kết quả report JSON, các bước lỗi, hash APK và trạng thái Android runtime. Lưu artifact test (không có dữ liệu riêng). Không đổi assertion nếu thực tế lỗi; code có thể phải sửa theo log.
5. Tạo workflow CI riêng trên nhánh này: tải đúng artifact được cấp từ run `40638158372` với `actions/download-artifact@v4`, verify hash, bootstrap SDK/emulator, chạy runner, upload evidence kể cả khi thất bại, job timeout rõ. Không tái biên dịch 8 phút/commit nếu không thay mã APK.
6. Ghi kết quả theo các tầng: **source/build** (đã PASS ở SHA a9aa), **boot/emulator** (chưa có), **native hooks/DEX/Resources runtime** (chưa có), **guest Activity/Binder/context/accounts** (chưa làm). Nếu runner thực sự chạy và report PASS sẽ chỉ đóng phần E11 code-image proof, không đóng phase 1/2 hay gọi engine clone xong.

## Cổng E11-Android

PASS khi đồng thời: AVD boot hoàn tất và service package/activity sẵn sàng, cài APK đúng hash, run UUID khớp, native hook state ENABLED, private APK signed+readback, DexClassLoader load class payload và resource marker đúng, activation ledger vẫn pending, tất cả denial flags giữ false. Nếu bất kỳ điều kiện nào chưa đạt, giữ FAIL/BLOCKED và lập plan sửa cụ thể. Mốc Samsung ARM64/One UI, lifecycle/Binder, account isolation, full split/isolatedSplits, cập nhật giữ phiên, phát hành Fonesi vẫn **OPEN**.

## Điều chỉnh trước khi code runner: sử dụng đúng artifact đã ký/test

Artifact CI `a9aa5a8` có Activity tạo UUID nội bộ; thêm intent `run_id` vào nhánh hiện tại sẽ không tồn tại trong byte APK đã đóng băng. Để không tuyên bố kiểm thử một source khác artifact, **không sửa Activity/probe APK ở lát cắt này**. AVD luôn tạo mới (không reuse), app chưa có bất cứ report nào; sau launch, runner đợi duy nhất một file `<uuid>.json` trong `files/guest-code-probe/` qua `run-as`, xác thực tên UUID khớp trường `run_id`, `complete=true` và đúng dữ liệu test. Trường hợp có 0/nhiều report, UUID lỗi, timeout hoặc APK hash sai đều FAIL. Lượt AVD này chỉ chạy một probe; receipt không thể tái sử dụng từ lần trước vì AVD chưa từng cài app.

Ý tưởng truyền UUID explicit vào Activity thuộc mốc build APK mới về sau; phải build/test lại trước khi dùng, không ghép với artifact cũ.

## Rà đường artifact và AVD trước lượt CI

GitHub upload-artifact có thể giữ cây thư mục tương đối khi tải hai APK từ các module khác nhau. Runner phải tìm **đúng một tệp theo basename** trong artifact đã tải, từ chối duplicate/symlink và so hash SHA-256 trước mọi lệnh Android. Không giả định ZIP phẳng. `avdmanager create avd` có thể hỏi profile tương tác; runner gửi `no` rõ ràng qua stdin và giữ timeout, không treo bước create trên runner CI.

## Bằng chứng lỗi Android có giới hạn

Nếu Android khởi động được nhưng runtime/biên nhận không đạt, chỉ thu logcat `AndroidRuntime:E`, `FonesiNativeProbe:E` và `libc:F` từ AVD mới tạo, giới hạn 64 KiB và không chứa dữ liệu tài khoản (AVD chỉ cài hai APK synthetic). Không lấy full logcat, cookie, thông báo người dùng hay thông tin mạng xã hội. Lỗi phải giữ nguyên trạng thái FAIL/BLOCKED và không tự gọi `confirmFromRuntimeReadback`.
