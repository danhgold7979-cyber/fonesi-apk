# E5 — native runtime từ source, 27/10/2026

Hash/size dưới đây là mốc E5 lịch sử (commit 21e883c). E6/E8 có chẩn đoán JNI và native preparer mới; xem [báo cáo hiện tại](SOURCE_ENGINE_VALIDATION.md). Không đối chiếu output AAR hiện tại với hash E5.

Đã dựng module `engine-source` độc lập. Đây là native file-routing component do Fonesi viết, dùng Dobby build từ source; chưa phải engine chạy ứng dụng Android hoàn chỉnh và chưa nối vào APK app chính. Plan và các lỗi/sửa trước build được ghi trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md).

## Code và input

- `PathRules` normalize đường tuyệt đối, khớp prefix theo ranh giới thư mục, chọn rule dài nhất; giữ ngữ nghĩa dirfd/CWD của path tương đối. Không thay substring tùy ý hoặc log đường guest. Map bất biến theo tiến trình, target phải nằm trong vùng private do host sở hữu.
- `NativeHooks` thực sự patch libc qua Dobby: open/openat/fortify và các biến thể large-file, fopen, stat/access, mkdir/unlink/rmdir, rename/renameat, readlink và các thao tác đã liệt kê trong source. Varargs chỉ đọc mode khi O_CREAT/O_TMPFILE; mỗi path có lifetime riêng. Resolve symbol trước patch; lỗi không thành ENABLED và không được hot-swap account trong tiến trình.
- JNI có đúng configure/enable/state và JNI_OnLoad; load/configure không tự hook. Java bridge từ chối chạy ở host/main, chỉ nhận dedicated process `:fonesi_guest_…`. Chưa có Java guest loader/component/Binder provider trong app.
- Dobby commit `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`, exact source patch SHA-256 `c6d20fc803a3d58a73fe942a0e17a3132b574f6dcab84f3461e6003c8565f153`. Prepare script kiểm tra base commit và diff chính xác, từ chối thay đổi khác. Patch dùng zero-context trên base pin để không có whitespace giả do dòng context của unified diff.
- NDK `29.0.13846066`, CMake `3.22.1`, API native 26, ARM64. Không lấy archive Dobby, SDK/AAR/keystore của demo. Không compile VirtualSpoof/AntiDetection/crash/log code của BlackBox. [Attribution](../third_party/source-engine/NOTICE.md), Dobby Apache-2.0 và TINYSTL BSD được đóng gói trong AAR.

## Kiểm tra đã chạy thật

```bash
python3 scripts/prepare_source_engine.py --install-tools
python3 scripts/check_source_engine.py
```

Lệnh check exit 0. Host integration test build executable thật và patch libc thật trong hai subprocess Linux A/B: đọc marker khác nhau, tạo/ghi/đổi tên, stat/permissions, fopen/C++ file stream, openat/unlinkat tương đối, giữ dữ liệu gốc và hai output riêng. Test từ chối rule sai/trùng, substring/neighbour và đổi account khi đã enable. Không dùng backend mock để báo native hooks đã chạy.

Đây là **Linux x86_64 host test**, không phải APK guest, SQLite Android, Facebook hoặc tài khoản thật. Android ARM64 library chỉ đã compile/static-audit, chưa load trên Android.

Gradle `-PincludeSourceEngine=true :engine-source:assembleDebug :engine-source:lintDebug` đạt. AAR `engine-source/build/outputs/aar/engine-source-debug.aar`: **165.408 bytes**, SHA-256 `a907528534f4eada958787f33d08d1070b2be556f973c4bbd04282ae228041a8`.

Packaged ELF **474.968 bytes**, SHA-256 `ea7d191e5f4a81e55d03114ee3a02cbe96d18dcd54827e8768fbb186659ef7ba`; ARM64, cả ba LOAD segment align **16.384**, không W+X, có RELRO, stack không executable. Chỉ dependency `liblog.so`, `libdl.so`, `libm.so`, `libc.so`; không libc++_shared hoặc native binary ngoài nguồn. Exact bốn export JNI đã được audit bằng llvm-readelf.

Manifest AAR không quyền/component; đủ ba license/notice assets và không APK/archive/keystore thừa. Lint 0 lỗi, 1 cảnh báo ChromeOS ABI, không suppress. Build đầu thất bại vì lỗi source Dobby; patch sửa stale fields/include, vòng include, checked reads, ARM64 ELF relocation và cấu hình logging. Không đổi assertion hoặc dùng prebuilt để lấy kết quả xanh.

Build mặc định không include module này; dependency graph app đã kiểm tra không có `engine-source`. APK 0.5.0/E4 và 264 test cũ giữ nguyên phạm vi/bằng chứng; không chạy lại chỉ để tăng số lượt. Source runtime có báo cáo riêng dưới `engine-source/build/reports/source-engine-verification.json`.

## Điều kiện còn mở

Native component không phải kernel/UID sandbox và không mediate direct syscall; guest cùng UID vẫn là trust boundary cần ghi đúng. Android dynamic loader/Binder/activity/service/provider, bộ APK split/resources/native libs, transaction/update/readback, process lifecycle và dữ liệu tài khoản chưa được nghiệm thu. Không bật importsSplitApks hoặc đổi provider của guest SpaceCore từ việc build được thư viện này.

Bước tiếp theo là chạy JNI/file/SQLite trên Android trong cloud nếu software emulator khởi động được, rồi nối provider Java/cluster theo plan riêng. Emulator không thay thử Samsung ARM64/One UI hoặc ma trận social-app. Không gọi E5 là toàn sản phẩm hoàn thiện.
