# Bản tích hợp engine 0.5.0-candidate

Theo yêu cầu tiếp tục code trước khi đưa lên điện thoại: đã nối adapter với SDK thật trong một bản debug có bật chủ động. Đây không phải chứng nhận hoàn thiện. Không có runtime ARM/Samsung trong môi trường cloud; điều kiện nghiệm thu còn mở.

## Code đã nối

- Build mặc định `0.5.0-cloud` không chứa SDK hoặc quyền Internet. Build `-PengineCandidate=true` dùng cùng mã UI/Room, thêm SDK pin nguồn/hash và adapter SpaceCore thực tế. Bằng chứng bản hiện tại ở [PHASE6_VALIDATION.md](PHASE6_VALIDATION.md); báo cáo 0.4 là lịch sử.
- Khởi tạo chỉ sau khi người dùng bật trong Thiết lập và mở lại Fonesi. Giới hạn thử ARM64/API 26–34/trang 4 KB; có marker trước init để chặn lặp khởi động sau lỗi native. Không chặn theo model Samsung.
- Registry Room schema 3 lưu UUID instance, reservation user SDK, state và marker launch-pending. Giữ reservation cả sau thất bại/xóa để không tái sử dụng user ID. Khác namespace engine không sửa được registry của nhau.
- Tạo bản: lấy app gốc → snapshot riêng → xác minh một APK/signature/version → reserve user bền vững → SDK tạo user/nhập APK → đối chiếu package manager khách và bản APK trong kho riêng → lưu metadata. Không sao chép dữ liệu đăng nhập.
- E4 bổ sung apksig kiểm tra chữ ký mật mã/manifest snapshot trước import; private copy phải có cùng hash với input đã verify. Không coi việc đọc certificate metadata là cryptographic verification; không chạy lại toàn audit cho mỗi clone khi quét định kỳ. Chi tiết [APK_SET_VALIDATION.md](APK_SET_VALIDATION.md).
- Tạo thêm bản sạch, đổi tên/nhóm, mở, ghim, dừng và xóa có xác nhận đã nối vào UI. Xóa kiểm tra token hiện tại, trạng thái chạy và readback; không uninstall bản khác hoặc xóa/cài lại để giả cập nhật.
- Mở ghi `launchPending` trước SDK. Khi SDK chưa báo chạy, trạng thái là chưa biết, kể cả sau khởi động lại app. Lệnh dừng có xác nhận hoặc readback đang chạy mới xử lý marker; không dùng timeout để đoán rảnh.
- Đối chiếu phục hồi có thể bổ sung metadata cho guest được SDK đọc lại thực sự sau gián đoạn tạo. Không tự xóa metadata khi SDK tạm không đọc được.
- Create/update shared đọc đủ active registry thay vì chỉ danh sách guest đọc được; peer thiếu/đang xóa phải chặn cả nhóm. Sau snapshot create đọc lại peers và foreground/access trước bước SDK đầu; khi chưa vào SDK có thể retire reservation chưa dùng nhưng giữ ID, khi đã vào thì không uninstall để giả rollback.
- Cập nhật một bản/tất cả: planner kiểm tra app gốc → xếp nhóm theo package → nút Chạy hàng đợi xử lý từng tác vụ với OS lock/journal/snapshot/readback đã có. Candidate khai báo dùng chung mã; kiểm tra đủ các bản cùng package trước và sau một lần import. Nếu readback chỉ đổi một phần, chuyển kết quả cần đối chiếu, không import tiếp hoặc xóa dữ liệu làm rollback.
- Tạm dừng có thể ghi trong khi runner hoạt động, được kiểm tra ở ranh giới an toàn. Không khai báo scheduler nền tự chạy: giữ Fonesi mở khi thực thi; process death được journal xử lý ở lần đối chiếu sau.
- Có màn hình xin quyền camera/mic/thông báo qua Android. Quyền cấp cho host; chưa chứng nhận tách quyền của từng guest. Không xin root, SMS, danh bạ, cuộc gọi, vị trí, quản lý toàn bộ tệp hoặc QUERY_ALL_PACKAGES.
- Bản 0.5 thêm [khóa hệ thống](ACCESS_CONTROL.md), [backup tên/nhóm](CONFIGURATION.md), chọn nhiều/preview và [tự động khi đang mở](UPDATE_AUTOMATION.md). Các luồng có cloud tests, không thay bằng chứng hardware auth/SAF/guest thật. Không có worker nền độc lập.

## Giới hạn chặn nghiệm thu toàn bộ yêu cầu

1. **Split APK:** API SDK công khai chỉ nhập package hoặc một File. Bytecode của đường package chỉ lấy `applicationInfo.sourceDir`; chưa có API nhập bộ split từ snapshot. Candidate từ chối bộ nhiều APK. Nhiều app Play Store có split, vì vậy chưa đáp ứng đầy đủ Facebook/Zalo/TikTok/Threads/X/Shopee/YouTube như yêu cầu. Không bỏ split hoặc sửa chữ ký để che giới hạn. Cần engine hỗ trợ nhập/cập nhật đầy đủ bộ APK hoặc một tích hợp được chứng minh tương đương; yêu cầu đa app chưa bị bỏ khỏi kế hoạch.
2. **Android/native:** SDK pin chỉ được phép thử API 26–34, ARM64, 4 KB. Chưa hỗ trợ chứng minh Android mới hơn/16 KB; không có source native để rebuild SDK này. APK quản lý có minSdk 26 không có nghĩa engine chạy trên mọi máy Android 8+.
3. **Runtime:** target 36, init, UI guest, đăng nhập, Play Services, thông báo, camera/chọn ảnh, cách ly, giữ phiên qua cập nhật và độ ổn định chưa được thử trên máy thật. SDK có thể lỗi trước khi mở guest; build thành công không giải quyết được điểm này.
4. **Các phase khác:** khóa và backup cấu hình đã có code/test cloud; hardware auth/SAF thật chưa thử. Worker nền độc lập, tùy biến màu/huy hiệu, đo hiệu năng và ký/nâng cấp phát hành chưa hoàn tất. Không gọi đây là bản hoàn thiện để phát hành.

Không thể chứng nhận “code xong thành công toàn bộ” khi còn các mục này. Có thể đưa toàn bộ việc thử điện thoại về đợt sau, nhưng không thể dùng cloud để thay bằng chứng tương thích tài khoản/thiết bị. Đợt cuối có thể cần sửa code hoặc thay engine rồi thử tiếp.

## Build và artifact

```bash
./scripts/check.sh --with-engine-candidate
```

Lệnh chạy cloud tests/build/lint/audit trước; lưu APK cloud rồi build/audit candidate và lưu riêng:

- `app/build/outputs/cloud/app-debug.apk` — không SDK/Internet.
- `app/build/outputs/candidate/fonesi-0.5.0-candidate.apk` — có SDK thực tế, mặc định chưa chủ động init.
- `app/build/outputs/candidate/verification.json` — hash, quyền, test, cảnh báo và trạng thái runtime chưa xác minh.

Hai bản cùng application ID và chữ ký debug; không phải hai sản phẩm cài song song. Khi đổi build vẫn giữ metadata, nhưng build cloud không mở được guest của candidate. Không đưa keystore debug hoặc AAR vào Git, không coi khóa debug là khóa phát hành.

## Đợt thử điện thoại để dùng sau

Chưa yêu cầu người dùng thực hiện ngay. Khi chọn đợt thử cuối, ghi model/Android/ABI/page size/launcher, cài candidate và payload, dùng payload do dự án kiểm soát trước. Bật engine ở Thiết lập, mở lại và ghi kết quả init. Tạo hai payload clone, lưu hai nhãn khác nhau; kiểm tra mở/ghim/dừng/khởi động lại/xóa/khôi phục metadata và cập nhật phiên bản payload. Chỉ sau khi các phần đó đạt mới đăng nhập tài khoản thử do người dùng kiểm soát; không cung cấp OTP/mật khẩu trong chat. Các app split đang bị chặn không được ghi là đạt.

Thứ tự APK trong gói: cài `fonesi-0.5.0-candidate.apk` và `fonesi-payload-r1.apk` trước. Mở payload gốc, lưu GỐC; tạo hai bản A/B trong Fonesi và lưu nhãn riêng, ghi lại ID từng vùng. Để thử cập nhật, cài `fonesi-payload-r2.apk` **đè nâng cấp bản gốc, không gỡ bản cũ**; dừng A/B bằng Fonesi, xem preview/xác nhận rồi Chạy hàng đợi. Sau đó mở A/B và đối chiếu ID/nhãn vẫn riêng, version SDK báo là 2. Nếu SDK báo partial update/không xác minh được, giữ journal và ghi thất bại; không xóa/cài lại để làm dữ liệu trông đúng. Checklist hiện tại gồm khóa/config/auto tại [PHONE_TEST_GUIDE.md](PHONE_TEST_GUIDE.md).

Thực hiện các mốc phase 2/4/5/7 trong đợt thử đó. Ghi nhận từng kết quả thật và sửa/test lại phần thất bại; không cam kết không bị ban hoặc không bao giờ văng app.
