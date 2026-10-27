# E6/E7/E8 — kho code và native component, 27/10/2026

Mốc này bổ sung code thật và kiểm tra trên cloud; **sản phẩm clone đa ứng dụng chưa hoàn thiện**. Candidate vẫn dùng SpaceCore với guard một APK/ARM64/API 26–34/4 KB. Module source mới chưa là provider chạy app, chưa bật importsSplitApks, chưa chuyển dữ liệu guest hiện có. Plan trước code và các lỗi/sửa có trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md).

## Phần đã triển khai

E7 chuyển nguyên verifier/chữ ký và 16 test APK thật sang module JVM `apk-runtime`. App candidate dùng verifier chung; engine-source cũng dependency module này, không dependency ngược vào app. APK test/key chỉ sinh trong ignored build output, không đưa vào Git hoặc bundle. Fixture SDK resolve khi task chạy, hỗ trợ ANDROID_HOME/ANDROID_SDK_ROOT/local.properties; build thường không include engine-source/native probe.

`PrivateApkStore` audit toàn bộ bộ APK, đóng băng danh sách nguồn, copy/hash/fsync và read-only từng file, audit lại các bản private, ghi index có giới hạn, atomic publish theo UUID và mở lại để xác minh. Nhận base/feature/config theo manifest, không theo tên nguồn. Readback sau restart kiểm tra exact file set, index/UUID/package/version/signers/hash và chữ ký mật mã. Thiếu/thừa/sửa file hoặc symlink phải thất bại; không lấy APK gốc làm fallback. Phiên bản cũ/mới cùng tồn tại, không overwrite hoặc tự đổi active version. Index tối đa 256 KiB/512 thành viên mỗi bộ APK, không phải giới hạn số clone.

E8 `NativeLibraryPreparer` mở lại cluster private đã audit, duyệt cả base/split, chọn đúng ABI ARM64/x86_64, stream giải nén vào staging có hash/fsync. Trùng tên native library trong namespace phẳng chỉ chấp nhận bytes giống nhau; nội dung khác bị từ chối để tránh chọn tùy ý. Chặn archive traversal, sai ELF64/DYN/machine, program headers sai/overflow, p_align/page congruence, W+X và executable stack. ELF 4 KB không được báo phù hợp 16 KB. Publish immutable/UUID và kiểm tra receipt/file set/bytes/ELF lại. Không sửa APK, ký lại ứng dụng thật, ghi account data hoặc kích hoạt phiên bản. Ngân sách giải nén là 512 MiB mỗi library/2 GiB mỗi image; không đặt trần số clone.

Native component có chẩn đoán JNI bằng tên symbol/hook tĩnh khi lỗi, không log đường tài khoản/address. Hook state không đổi tài khoản trong một process. Dobby source/patch/NDK/CMake giữ nguyên pin E5. Có license Dobby/TINYSTL/Fonesi/apksig trong artifact. Thành phần này không phải sandbox UID/kernel, không mediate direct syscalls và chưa có linker namespace riêng cho feature xung đột.

## Kiểm tra thực sự đã chạy

```bash
./scripts/check.sh --with-engine-candidate
python3 scripts/check_source_engine.py
./scripts/gradle.sh -PincludeNativeRuntimeProbe=true -PsourceEngineAbi=x86_64 \
  :native-runtime-probe:assembleDebug :native-runtime-probe:lintDebug --console=plain
python3 scripts/run_native_probe.py --audit-only
python3 scripts/build_phone_bundle.py
```

App/shared: **283 test** = 92 core + 35 APK JVM (16 verifier + 19 private store) + 156 Android mô phỏng API 26/34. Cloud/candidate đều build/lint/audit đạt; không cộng hai cấu hình thành test độc lập. Sau đó engine source đạt **18 test JVM** với APK ký thật và ELF do NDK clang ARM64/x86_64 build: tổng **301 test**, 0 failure/error/skipped. Hai test cuối kiểm tra program-header congruence và overflow, không giả một header để nhận đã chạy native. Fixture overflow được căn 16 KB để không bị từ chối do một lỗi alignment khác.

Host C++ integration vẫn thực sự patch libc trong hai subprocess Linux, giữ marker/output riêng và dữ liệu gốc không đổi. Đây là bằng chứng Linux; các test Kotlin kiểm tra filesystem/crypto/extraction trên JVM. Chúng không chạy guest APK hoặc SQLite trên Android thật. Core dry-run không có biến SDK đạt; dependency graph app thường không có engine-source. Python parse, link tài liệu, diff whitespace, artifact/source scan và exact bundle checksum được kiểm tra riêng.

| Artifact cuối | Bytes | SHA-256 | Lint |
| --- | ---: | --- | --- |
| 0.5.0-cloud | 11595556 | `12daa65dcdf0b0be83d4139e04101ecae8410458624490eccf2fcf211ff20fce` | 0 lỗi/5 cảnh báo |
| 0.5.0-candidate | 17670768 | `c6e46e50423809dac89df06d0803a725808c6d32fcd70b02369533b6e3944d95` | 0 lỗi/15 cảnh báo |
| AAR source ARM64 | 186073 | `7615fef754dfdc4e2679e15b661f4c5fa486919a1f9f43225d2a7bd79c9157e3` | 0 lỗi/1 cảnh báo |
| Native probe x86_64 | 7795301 | `a5ceeac8942c2d225d0b9ad8a6d07353c476ecc1e970538b34092cd5583aab63` | 0 lỗi/5 cảnh báo |

AAR ARM64 đã audit được giữ riêng ở `engine-source/build/reports/engine-source-arm64-verified.aar`; tên output AAR thông thường có thể bị build x86_64 thay thế. ELF ARM64 packaged 476248 bytes, SHA-256 `8c9c98f27f85801eb99c5860d5953cac0e04c0f63ed9f7f4526c73a791b606ec`. ELF x86_64 trong probe SHA-256 `214ba0d319ea8665617e50116a172a03bbfbe9911512257524dacbcd60078960`. Cả hai có ba LOAD align 16384; audit engine giữ đúng bốn export JNI và không quyền/component. Probe không quyền/Internet, có một launcher và hai service không exported trong hai process riêng.

Bundle `app/build/outputs/fonesi-0.5.0-phone-test.zip`: **17421534 bytes**, SHA-256 `469a0d79d941f708e02b220de42b59e14540faa8370509881ef3e61d93065114`. Kiểm tra 7 entry exact allowlist, CRC/SHA mỗi entry, candidate/guide/notice khớp source. Không có native probe, fixture/key hoặc artifact cũ. Debug signer giữ nguyên, chưa là khóa release. Hash E4/E5 trong các báo cáo cũ là mốc lịch sử.

## E6 — kết quả Android chưa đạt

Đã cài SDK Emulator 37.2.12 và ảnh API 34 x86_64 Google APIs, sau đó AOSP default. Không có /dev/kvm; đã dùng software emulation, không hạ target 36 hoặc dùng điện thoại. Mỗi AVD do dự án tạo, serial/port/PID/AVD ownership rõ. Không chạm thiết bị hay dữ liệu người dùng.

| Lượt | Kết quả quan sát | Có chạy probe không? |
| --- | --- | --- |
| Google APIs cold boot | Hết 480 giây khi init/scan/dexopt ứng dụng hệ thống. | Chưa cài/chạy. |
| Cùng AVD warm restart | Hết 480 giây, chưa boot_completed. | Chưa cài/chạy. |
| AOSP 34, màn hình 480×800 | Hết 480 giây; tiếp tục trong lúc làm phần độc lập, sau đó thấy package/activity services được publish. | Chưa có JNI/file/SQLite result. |
| Reuse AOSP có thời hạn | Guard thấy framework service không ready; crash buffer có `DeadSystemException: The system died`. | Chưa cài/chạy, Android execution NOT VERIFIED. |

Framework service xuất hiện lại sau đó không thay kết quả attempt đã thất bại. Lệnh SIGTERM không làm QEMU thoát; đã xác minh cmdline/AVD/process group rồi SIGKILL đúng nhóm máy ảo của dự án và kiểm tra PID không còn chạy. Không tạo thêm lượt thử vô hạn. Chưa xác định nguyên nhân gốc framework chết, không kết luận đây là lỗi Fonesi hoặc bằng chứng các app social không tương thích.

Runner dùng APK input riêng đã đóng băng trước static audit/install, hash của đúng input, nonce UUID cho mỗi run; không nhận PASS cũ từ lần chạy trước. Chỉ app report có complete/passed và run_id khớp mới là kết quả native. Bản probe hiện tại chỉ có kết quả static, không có Android native pass; Samsung ARM64/One UI vẫn chưa thử. Nhật ký/status local nằm trong `native-runtime-probe/build/reports/runtime/`.

## Việc còn thiếu để thành engine sản phẩm

Native extraction và kho split chưa tạo được môi trường Android cho app. Còn phải triển khai/kiểm tra ApplicationInfo/PackageInfo, resources và classloader toàn bộ split, Application/Activity/service/provider/Binder routing, process allocation/binding, native linker dependency/namespace, active-version/update/readback/khôi phục giữ dữ liệu và adapter source namespace riêng. Chỉ sau đó mới có thể bật capability và đưa qua thử payload/tài khoản. Không chuyển journal/guest SpaceCore qua provider mới hoặc fallback mở app gốc.

Ngoài engine còn có các mốc [STATUS](STATUS.md): worker nền độc lập, màu/huy hiệu, phát hành/ký nâng cấp và nghiệm thu nhiều thiết bị. Chưa đạt các yêu cầu này; đây không phải bản sản phẩm 100% chỉ chờ cài điện thoại.
