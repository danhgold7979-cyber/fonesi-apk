# E4 — kiểm tra tính toàn vẹn APK và bộ split

Ngày thực hiện: 27/10/2026. Plan tổng thể/chi tiết và các điều chỉnh trước code ở [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Source trước E4 là a1d70d2; kiểm tra mới không thay kết luận engine/runtime trong [ENGINE_SPLIT_AUDIT.md](ENGINE_SPLIT_AUDIT.md).

## Thay đổi thực tế

`ApkSetAuditor` dùng Android apksig 8.11.1 để verify chữ ký mật mã cho API Android được chỉ định và đọc binary AndroidManifest.xml. Không tự viết thuật toán chữ ký hoặc coi certificate metadata là đủ. Dependency/parser internal được pin và ghi ở [NOTICE](../third_party/apksig/NOTICE.md).

Auditor yêu cầu:

- Tệp thường/canonical path duy nhất, không symlink file/thư mục; manifest tối đa 4 MiB, đúng một entry manifest. Đọc bounded trước khi thư viện verify có thể tự đọc manifest.
- Mỗi APK đều có chữ ký thật hợp lệ và cùng signer, package, version 64-bit với source. Hash kiểm tra trước/sau đọc và lại toàn tập; không trộn file thay đổi trong lúc audit.
- Đúng một base theo nội dung manifest, không theo tên/kích thước. ID split duy nhất; giữ tất cả feature/config được cung cấp.
- Feature/config dependency tồn tại và trỏ đúng feature; không cycle. Duyệt graph không đệ quy để tránh stack overflow với chuỗi dài. Không tự bỏ split thiếu/lỗi hoặc đoán chọn một ngôn ngữ/ABI theo tên file.
- Base có isSplitRequired không được nhận như standalone. Các requiredSplitTypes phải có trong tập splitTypes được cung cấp. VersionCodeMajor không bị cắt xuống int.

Candidate SpaceCore vẫn chỉ nhận một APK. Luồng create chạy preflight chỉ đọc trên snapshot trước reserve/createUser: archive, chữ ký thật và ABI sai phải bị từ chối trước native mutation. Adapter kiểm tra lại ngay trước import, hash input không đổi và private copy thuộc SDK có hash bằng chính input đã verify. Đây là ràng buộc chữ ký cho copy sau import. Các lần inspect định kỳ vẫn đối chiếu archive/package manager như trước; không audit lại toàn file cho từng clone trong nhóm shared hoặc dùng cache size/mtime làm bằng chứng chữ ký.

Auditor không cài/chạy guest, không chứng minh ABI/resources/classloader/Play Services hoặc account isolation, và không bật importsSplitApks cho SpaceCore. Dữ liệu audit không được lưu/xuất như tài khoản và không thêm permission/Internet vào cloud.

## Kiểm thử

Fixture generator chạy aapt2, keytool và apksigner thật; APK resource-only có binary manifest và chữ ký v2/v3. Khóa test sinh riêng trong ignored build output, không dùng keystore app hoặc phone payload. Test task khai báo các APK fixture là input; fixture/script đổi phải chạy lại. Gradle truyền SDK/JDK rõ nên local.properties được hỗ trợ mà không phải sửa HOME/ANDROID_HOME.

Các test gồm single APK tên bất kỳ; feature/config/dependency đầy đủ với input đảo thứ tự; version major; unsigned/tampered; signed XML text; manifest đã ký vượt budget; source/pkg/version/signer sai; thiếu/hai base; split ID/path trùng; thiếu feature/config target; dependency cycle; isSplitRequired/required types; symlink file/thư mục; missing/empty/non-APK và không sửa original.

Core bổ sung hai test protocol: preflight từ chối nguồn và mất foreground trong preflight đều phải không reserve/tạo user hoặc import, đồng thời dọn snapshot chưa dùng. Các backend double không phải SDK thật; preflight/crypto fixture và guard production được kiểm tra ở các tầng tương ứng.

Đây là test Java với APK ký thật. Suite Room/auth/queue/shortcut cũ vẫn kiểm tra bằng Robolectric/native SQLite API 26/34. Không có test nào ở đây chạy APK ARM, đăng nhập hoặc kiểm tra Samsung.

Lệnh cuối sau sửa preflight: `./scripts/check.sh --with-engine-candidate`, **exit 0**. Tổng **264 test**: 92 core + 172 app (156 Robolectric API 26/34 và 16 test verifier Java với APK thật). Core suite chạy lại sau thay đổi; app suite chạy ở cả cloud/candidate, không failure/error/skipped. Không cộng hai lần cùng test thành test độc lập. Mốc 262 test trước đó chưa có hai test preflight, không dùng hash mốc đó làm artifact cuối.

| Artifact | Bytes | Lint | SHA-256 |
| --- | ---: | --- | --- |
| 0.5.0-cloud | 11579172 | 0 lỗi, 5 cảnh báo | `b376c42ea89ab02c9a9220d5699e4fb1f9f27209ed130db6c428c74757192e53` |
| 0.5.0-candidate | 17659448 | 0 lỗi, 15 cảnh báo | `85559e75b8f139084819debe78719f207f745ba32f748cbb96d630ddec14bca9` |

Chữ ký debug candidate/payload: SHA-256 `54d546364fb56d92738044512be3f5152bb625a61a102e7bed7f05735d59c033`. Cùng versionCode 5; đây là revision E4 của 0.5.0, không phải release đã nghiệm thu. APK/bundle mốc 26/10 có hash khác.

Hai APK qua kiểm tra chữ ký/manifest thật. Cloud vẫn đúng 3 quyền normal/internal, không Internet/SDK; candidate đúng 11 quyền trong allowlist, chỉ hai activity host/shortcut public và 2.603 component exported còn lại có signature/system guard. Không thêm quyền hoặc suppress lint. Cảnh báo native 16 KB vẫn là giới hạn SDK pin chưa giải quyết.

Gói cuối: `app/build/outputs/fonesi-0.5.0-phone-test.zip`, **17.411.713 bytes**, SHA-256 `9db64d49722c4a115631f3022fd74ce2387081b2c791cc5714bda10f71bdee40`. Script bundle exit 0; đúng 7 entry allowlist (candidate, payload r1/r2, guide, hai notice, checksums), CRC và mọi hash khớp. Candidate trong ZIP đúng hash ở bảng; guide/notice khớp source. Không lẫn APK/khóa fixture hoặc artifact cũ. Source scan không thấy private key/GitHub credential hoặc APK/AAR/keystore trong tập source; script parse/link tài liệu/diff whitespace đạt.

## Các yêu cầu vẫn chưa hoàn thành

E3 tìm thêm source có .apks nhưng còn heuristic bỏ split/lỗi copy/fallback host và init/log/native chưa đạt audit. Renjana có fallback mở app gốc. Các engine này chưa được vendor/build/runtime trong sản phẩm. Chi tiết commit/file ở [ENGINE_SPLIT_AUDIT.md](ENGINE_SPLIT_AUDIT.md).

Engine import/update split end-to-end, Android mới/16 KB, runtime/cách ly/giữ phiên của các app mục tiêu còn mở. ADB không có thiết bị; không có /dev/kvm. Hardware auth, SAF/launcher thật, worker nền độc lập, tùy biến màu/huy hiệu và ký/nghiệm thu release chưa hoàn tất. Verifier là phần code thật hoàn thành thêm, không phải bằng chứng sản phẩm đạt 100%.
