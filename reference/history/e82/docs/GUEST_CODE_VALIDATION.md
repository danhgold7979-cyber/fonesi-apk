# E9 — code image loader, 27/10/2026

Đã triển khai và build component nạp mã/tài nguyên private; **chưa có kết quả thực thi ART/Resources trên Android và chưa có engine sản phẩm hoàn chỉnh**. Plan trước code ở [EXECUTION_PLAN.md](EXECUTION_PLAN.md), mốc trước ở [E6/E7/E8](SOURCE_ENGINE_VALIDATION.md). Candidate vẫn dùng SpaceCore, chưa import split; không nối source provider hoặc chuyển guest/journal cũ.

## Thay đổi

- Verifier đọc `android:isolatedSplits` từ binary manifest đã ký. Có fixture/test thật cho cờ này; kiểm tra chữ ký, identity/dependency và copy/readback giữ nguyên. Đây là dữ liệu cho loader, không tự thành capability engine.
- `GuestCodeLayout` mở lại cluster private đã xác minh, bảo toàn đủ base/feature/config, ghép splitNames/sourceDirs cùng thứ tự tên, yêu cầu tất cả APK trong data root host. Chặn package host/framework và cờ isolatedSplits khi chưa có loader graph phù hợp. Không flatten giả hoặc bỏ split để báo thành công.
- `GuestCodeLoader` chỉ cho dedicated process, native ENABLED, process 64-bit và thread IO. Một attempt trong process; thất bại không tự reset để đổi tài khoản. ABI/page size lấy từ Android, native path từ preparer, DEX cache trong vùng private. DexClassLoader dùng toàn code private và parent framework, tránh trộn thư viện của app host vào guest.
- Resources dùng ApplicationInfo đọc từ base private rồi gán đủ base/split source paths/UID host, qua API công khai PackageManager.getResourcesForApplication. Không createPackageContext hoặc lấy code/resources từ app gốc đang cài. Đọc lại cluster/native image sau tạo loader/resources, trước khi trả handle.
- Loader không gọi Application.onCreate/Activity, không trả một guest Context hoặc PackageInfo đầy đủ, không gắn custom resource classloader, không đăng ký Binder/components/intent. Public Resources SDK không cung cấp constructor classloader cần thiết; phần context/lifecycle sau phải giải quyết. Không nhận component hiện tại là chạy được Facebook/Zalo hoặc các tài khoản riêng.

## Kiểm tra

```bash
./scripts/check.sh --with-engine-candidate
python3 scripts/check_source_engine.py
./scripts/gradle.sh -PincludeNativeRuntimeProbe=true -PsourceEngineAbi=x86_64 \
  :native-runtime-probe:assembleDebug :native-runtime-probe:lintDebug --console=plain
python3 scripts/run_native_probe.py --audit-only
python3 scripts/build_phone_bundle.py
```

Các lệnh exit 0. **309 test**, 0 failure/error/skipped: 92 core + 36 APK JVM (17 verifier, 19 store) + 156 Android mô phỏng + 25 source JVM (18 native extraction, 7 layout). Các test layout dùng APK binary ký thật/private store, kiểm tra đủ/thứ tự/private path/isolation/corruption/host collision; không mock DexClassLoader hoặc ResourcesManager để nhận Android đã chạy. Không cộng app cloud/candidate hoặc các nhóm con source thành test độc lập lần nữa.

Build/lint/audit cloud/candidate/source/probe đạt. Nguồn compile đầu lỗi nullable splitSourceDirs đã sửa bằng snapshot local; không suppress lỗi. C++ host libc integration vẫn chạy trong hai subprocess Linux, không thay bằng chứng Android. APK actual đã kiểm tra có các class source NativeRuntime/GuestCodeLoader/GuestCodeLayout trong probe và không có trong hai bản app. Engine source vẫn opt-in, không quyền/component; probe 0 quyền/Internet, một launcher và hai service nội bộ.

| Artifact hiện tại | Bytes | SHA-256 | Lint |
| --- | ---: | --- | --- |
| 0.5.0-cloud | 11595556 | `70701d71705c10ebe22bf1207a3fa81bf14defd945348f81a0d2bc7cb87d7f2e` | 0 lỗi/5 cảnh báo |
| 0.5.0-candidate | 17671028 | `ce38d8b17e4968876c51535d8fadbea8c93b613d02a022fca2296c648c541faf` | 0 lỗi/15 cảnh báo |
| AAR source ARM64 | 201797 | `b00464a4d84e5d652eb47a93cd50c90292e276c16f2bcb3df740d42fdbfacab0` | 0 lỗi/1 cảnh báo |
| Native probe x86_64 | 7795301 | `e25541b2997588dcaa21ec5ab6fe5a8ad6cc5532d0e344aa190f099b6c074616` | 0 lỗi/5 cảnh báo |

AAR ARM64 lưu riêng trong `engine-source/build/reports/engine-source-arm64-verified.aar`; output AAR thông thường sau build probe là x86_64. ELF ARM64/x86_64 giữ đúng hash E8, LOAD align 16384 và bốn JNI exports. Source checker báo riêng tổng source JVM và hai nhóm con để không nhận 25 test đều là native hoặc cộng trùng.

Bundle `app/build/outputs/fonesi-0.5.0-phone-test.zip`: **17422110 bytes**, SHA-256 `2535032b0c736c71c67df3f362cbddca973f5041f97389585a899131b8662fbf`. Đúng 7 entry/CRC/checksums; candidate/guide khớp source. Không có probe/fixture/khóa test trong bundle. VersionName/signer debug giữ nguyên; không dùng hash E8 để kiểm tra bản này.

## Phần chưa đạt

Không boot lại emulator trong E9. Kết quả E6 vẫn NOT RUN do framework máy ảo không ổn định, xem log/kết quả cụ thể trong báo cáo E8. Chưa chạy JNI/file/SQLite, ART, ResourcesManager hoặc guest Application/Activity trên Android; chưa có kết quả Samsung ARM64/One UI.

Còn phải làm guest Context/package metadata đầy đủ, custom resource/isolated feature classloader, Application/Activity/service/provider/Binder routing, native linker namespace/dependency, process allocation/binding, active-version/update/recovery giữ dữ liệu và adapter source với namespace riêng. Những phần này cần chạy/kiểm tra Android thực tế trước khi nối vào sản phẩm. Không được biến việc dựng DexClassLoader trong source hoặc 309 test cloud thành kết luận clone/social/account đã thành công.
