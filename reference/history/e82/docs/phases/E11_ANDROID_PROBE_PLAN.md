# E11 — kế hoạch chi tiết trước code: Android guest-code probe (chỉ payload kiểm thử)

Ngày 28/10/2026. Kế hoạch tổng: [ROADMAP](../ROADMAP.md), [EXECUTION_PLAN](../EXECUTION_PLAN.md). Đầu vào: E9 signed private image loader, E10 activation ledger (chưa tích hợp provider chính). E10 JVM test đã chạy nhưng Gradle gate phải xác minh riêng qua CI; E11 không nhận E10 hoặc phase 1 đã nghiệm thu Android.

## Mục đích và giới hạn

Bổ sung đường chạy **thực sự trên Android** để kiểm tra chuỗi signed payload APK → private APK store → native runtime dedicated process → DexClassLoader → Resources. Nó phục vụ việc thu bằng chứng trên emulator/điện thoại cho phần E6/E9, KHÔNG phải clone Facebook hay engine Application/Activity/Binder. Không dùng tài khoản thật, không sao chép dữ liệu đăng nhập và không đánh dấu capability split cho SpaceCore.

## Lát cắt

1. Tạo một string resource định danh trong `probe-payload` do dự án kiểm soát; APK vẫn không xin quyền/Internet và không ghi dữ liệu thật.
2. Tạo `GuestCodeProbeService` trong `native-runtime-probe` với process `:fonesi_guest_loader`, `exported=false`. Không dùng process host để enable native hooks; mỗi attempt một process, kết thúc process sau khi ghi report.
3. Chỉ tìm payload `vn.fonesi.payload` theo package đã chốt. Dùng PackageManager lấy source base/split và signer, tạo `InstalledSource`; chữ ký và split được `PrivateApkStore.publish` audit lại. Khóa private code theo UUID, không lấy tệp gốc làm fallback khi loader lỗi.
4. Lấy `GuestImageRef` qua `PrivateGuestImageEvidence`. `GuestImageActivationLedger.stage` ghi pending cho một synthetic clone mới; không gọi `confirmFromRuntimeReadback` vì chưa có engine provider readback. Không báo image "active" chỉ từ DexClassLoader.
5. Native `configure(emptyBindings)` và `enable` chỉ trong process thử; sau đó `GuestCodeLoader.load` trên thread IO. Đọc class `vn.fonesi.payload.PayloadActivity` qua guest classloader, bảo đảm classloader riêng. Đọc string resource từ private APK và đối chiếu literal; không khởi tạo Activity/Application hoặc gọi giao diện guest.
6. Activity probe riêng, rõ nhãn thử kỹ thuật, kích hoạt thủ công; service ghi JSON không chứa bí mật (run UUID, pass/fail, milestone, phiên SDK/ABI, phủ định các khả năng chưa kiểm tra). Dùng request UUID chống đọc nhầm PASS cũ. Timeout/process chết phải FAIL, không biến việc dispatch thành thành công.
7. Chỉnh opt-in Gradle source/probe dependencies và manifest query đúng một package. Không thêm exported service, dangerous permissions, hoặc cài payload vào app chính. Không đổi SpaceCore/Android target 36.
8. Kiểm tra compile/lint APK/source bằng GitHub Actions. Nếu đủ môi trường Android emulator thì test run thật; nếu không, ghi `NOT RUN` và cung cấp lệnh ADB cho bước thiết bị sau. CI xanh không thay chứng minh Android execution.

## Tiêu chí

- **Mức source/build:** Gradle biên dịch và lint qua, không lỗi/skip test tùy tiện; manifest probe có service internal + process riêng và APK payload không quyền/Internet.
- **Mức Android:** chỉ đạt khi báo cáo từ process chạy thật, đúng run UUID, đã nạp class riêng và resource private từ signed APK; lỗi phải thể hiện đúng.
- **Chưa đạt sau E11:** guest Context/Application/Activity, Binder/service/provider routing, isolated split loader graph, dữ liệu tài khoản riêng, tương thích ứng dụng mạng xã hội, cập nhật giữ phiên, Android máy thật và phát hành. Tiếp tục lập plan E12 cho các mảnh đó, không hứa 100% từ probe.
