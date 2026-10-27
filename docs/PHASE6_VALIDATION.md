# Bằng chứng cloud 0.5.0 — S1/S2/U1/Q1 và rà cuối R1

**Mốc lịch sử nguồn a1d70d2, ngày 26/10/2026.** APK/bundle E4 tạo sau có cùng versionName 0.5.0 nhưng hash khác; xem [APK_SET_VALIDATION.md](APK_SET_VALIDATION.md) cho kết quả hiện tại, không dùng checksum mốc này để xác minh gói E4.

Kiểm tra ngày 26/10/2026. Plan tổng thể/chi tiết và các điều chỉnh được ghi trước code trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Báo cáo này thay mốc 0.4 làm bằng chứng bản hiện tại; các báo cáo trước được giữ nguyên là lịch sử.

## Lệnh và kết quả

```bash
./scripts/check.sh --with-engine-candidate
python3 scripts/build_phone_bundle.py
```

Lệnh kiểm tra hai cấu hình exit 0. Tổng **246 test**: **90 core + 156 Android mô phỏng**, không failure/error/skipped. App suite được chạy ở cả cloud và candidate; không cộng hai lượt cùng suite thành số test độc lập. Robolectric dùng API 26/34 với SQLite native, không có emulator ARM/điện thoại hay SDK guest được chạy.

| Suite | Số test |
| --- | ---: |
| Core access session | 10 |
| Core list/filter/selection | 6 |
| Core managed guest protocol | 22 |
| Core probe policy | 4 |
| Core update planner | 17 |
| Core execution policy | 4 |
| Core filesystem snapshots | 5 |
| Core update runner | 22 |
| Android configuration | 28 |
| Android access policy/broker | 14 |
| Android shortcut integration | 32 |
| Android instance registry | 8 |
| Android storage/migration | 24 |
| Android foreground auto updater | 20 |
| Android update preview/confirm | 30 |

Các test mới kiểm tra callback auth cũ/background/expiry, policy AtomicFile hỏng, pending SAF không tự IO, JSON nghiêm ngặt/2 MiB/Unicode/allowlist, mapping sai và rollback transaction, preview shared scope/stale source/token/capability và atomic enqueue, pause/dedup/locks, không retry REVIEW, mất foreground trước apply và hoàn tất verify sau SDK entry. Test planner 1.000 peer đếm phép so sánh signer để phát hiện quét bậc hai, không dùng ngưỡng thời gian. Đây không phải benchmark máy thật.

Rà cuối bổ sung guard cho peer registry thiếu readback/chưa có metadata/đang xóa, peer chạy trong lúc prepare, authorization trước native mutation và reservation chưa dùng giữ ID không tái sử dụng. Không có destructive rollback, false success, đổi assertion để bỏ yêu cầu hoặc bỏ split khỏi source.

## Artifact đã audit

| Bản | Bytes | Test/lint/APK audit |
| --- | ---: | --- |
| `0.5.0-cloud` | 11349737 | 246 test; 0 lỗi lint; 5 cảnh báo; manifest/chữ ký đạt |
| `0.5.0-candidate` | 17455297 | 246 test; 0 lỗi lint; 15 cảnh báo; manifest/chữ ký/native ABI đạt |

- Cloud SHA-256: `92e6d28188e17001d96cfd17f33f14a958d8af2a22e4c28ef29a0894abb72208`.
- Candidate SHA-256: `6819a3dc11c80ca3f23c50396dc3a7143a0a5b27b82827bd307375e45be3ef09`.
- Candidate/payload signer SHA-256: `54d546364fb56d92738044512be3f5152bb625a61a102e7bed7f05735d59c033` — khóa debug hiện tại, không phải khóa phát hành.

Cloud APK: `app/build/outputs/cloud/app-debug.apk`. Candidate: `app/build/outputs/candidate/fonesi-0.5.0-candidate.apk`. Cùng application ID `vn.fonesi.app`, versionCode 5; không cài song song. Debug signing key không đưa vào Git và có thể khác khi build ở môi trường khác; muốn cài đè phải kiểm tra cùng signer, không gỡ để giả đạt giữ dữ liệu.

Cloud có đúng 3 permission: USE_BIOMETRIC, USE_FINGERPRINT và permission dynamic receiver do AndroidX tạo; không Internet/SDK. Candidate có đúng 11: hai biometric, Internet/network state, camera/mic, foreground-service/special-use, notification, dynamic receiver và INTERNAL_SDK. Không thêm root/all-files/Usage Access/QUERY_ALL_PACKAGES. Chỉ MainActivity/CloneLaunchActivity là public không guard; route explicit không intent-filter, giữ history để nhận credential result nhưng ẩn recents/taskAffinity rỗng. Có 2603 component exported còn lại được bảo vệ bằng signature/system permission như allowlist. Archive/native SDK ARM64 đúng build; không suy ra guest chạy được từ việc thư viện có trong ZIP.

Lint còn cảnh báo CustomSplashScreen, dependency mới, ChromeOS ABI và UseKtx; candidate thêm ManifestOrder và 6 Aligned16KB của native SDK. Cảnh báo 16 KB là giới hạn thật của binary, không suppress hoặc giảm target để bỏ qua. Cảnh báo Context leak mới đã xử lý bằng constructor Application; không giữ Activity trong singleton.

## Gói chuẩn bị cho đợt thiết bị sau

Script chỉ đóng gói allowlist candidate 0.5.0, payload r1/r2, hướng dẫn, SDK notice và checksums; không quét tất cả APK trong thư mục đã có mốc cũ. Payload có versionCode 1/2, không quyền và cùng signer với candidate. Sau tạo gói phải kiểm tra danh sách ZIP chính xác và SHA-256 từng entry theo checksums; kết quả được ghi ở cuối báo cáo.

APK/AAR/build logs/keystore không đưa vào Git. Chỉ source, script, schema và tài liệu được commit. Không cần cài điện thoại ngay; hướng dẫn được giữ tại [PHONE_TEST_GUIDE.md](PHONE_TEST_GUIDE.md).

## Phần chưa được kiểm chứng hoặc chưa hoàn thành

- **Engine split còn là việc code chưa giải quyết:** SDK pin không có đường import/update đầy đủ bộ APK; nhánh source E2 chưa đủ điều kiện tích hợp. Chi tiết [ENGINE_SPLIT_AUDIT.md](ENGINE_SPLIT_AUDIT.md). Không đổi thành “chỉ chờ thử máy”.
- SDK init/target 36/Android mới/16 KB, runtime ARM, cách ly, đăng nhập, Play Services, thông báo, media/camera và update giữ phiên chưa được thử. Payload/Facebook/Zalo/TikTok/Threads/X/Shopee/YouTube không được ghi hỗ trợ qua các suite trên.
- Prompt sinh trắc/PIN, SAF provider thật/persistable permission/readback, One UI shortcut/cold start, dung lượng/pin/độ ổn định đa hãng chưa nghiệm thu. Không có kết quả cài nâng cấp thật từ 0.4 sang 0.5.
- Auto hiện tại foreground/unlocked, không có worker nền độc lập. Tùy biến màu/huy hiệu riêng và ký/nâng cấp release còn thiếu.

S1/S2/U1/Q1 đạt phần cloud có mô tả giới hạn. Không đóng toàn phase 1/2/5/6/7 hoặc tuyên bố sản phẩm hoàn thiện 100%. Giữ nguyên các yêu cầu bắt buộc trong [STATUS.md](STATUS.md).

## Kết quả đối chiếu ZIP cuối

`fonesi-0.5.0-phone-test.zip`: 17203326 bytes; SHA-256 `e9d4ea4c806867e778be26d7f46fbb1c051257c7454dd8a38a607d7449574e11`. Chính xác 6 entry theo allowlist, không entry trùng hoặc artifact 0.4; CRC và mọi SHA-256 khớp. Guide/NOTICE trong ZIP khớp source hiện tại. Script đóng gói exit 0; không chạy engine/điện thoại.
