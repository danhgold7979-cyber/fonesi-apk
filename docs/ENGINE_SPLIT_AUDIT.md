# E1/E2 — đánh giá split APK và khả năng thay engine

Ngày đối chiếu: 26/10/2026. Đã đọc lại yêu cầu/roadmap và ghi plan E1/E2 trong [EXECUTION_PLAN.md](EXECUTION_PLAN.md) trước khảo sát. Đây là đọc source/bytecode và Git refs, chưa thực thi guest hoặc build một fork mới. Không có source/binary/keystore của các fork được nhập vào sản phẩm trong lượt này.

## SDK SpaceCore đang pin

Nguồn [FSpaceCore/SpaceCore](https://github.com/FSpaceCore/SpaceCore/tree/3826a2fa1ac492fbbe7435ccb52074c1e2b702de), commit `3826a2fa1ac492fbbe7435ccb52074c1e2b702de`; `git ls-remote` vẫn cùng revision tại thời điểm kiểm tra. AAR SHA-256 `4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f`, giấy phép được ghi tại [NOTICE](../third_party/spacecore/NOTICE.md).

Đọc public API và bytecode `classes.jar` bằng javap:

- `FCore.installPackageAsUser(String,int)` lấy host `ApplicationInfo.sourceDir`; không truyền `splitSourceDirs` thành bộ import.
- Overload File dùng `installByStorage`; đường lưu của class obfuscated `j6` dựng `data/app/<package>/base.apk`.
- Tham chiếu split tìm được trong mirror framework không tạo thành protocol import/update/readback một bộ APK. Không tìm được API công khai đủ để đưa snapshot base+split vào engine.
- Native thư viện pin chỉ có ARM, 4 KB LOAD alignment; không có source native tương ứng để rebuild 16 KB. Guard thử ARM64/API 26–34/4 KB vẫn giữ, host target 36 không hạ.

**Kết quả E1:** chưa đạt split. Adapter tiếp tục từ chối bộ APK, khai báo `importsSplitApks=false`; UI/planner/runner không bỏ split hoặc merge/re-sign để báo đã clone. Không suy ra SDK không bao giờ có thể hỗ trợ split; kết luận áp cho đường API/SDK pin đã kiểm tra.

## Nhánh NewBlackbox có source

Nguồn [ALEX5402/NewBlackbox](https://github.com/ALEX5402/NewBlackbox/tree/89b59836c66f173756a4ae258cf379a957649820), commit `89b59836c66f173756a4ae258cf379a957649820`. Các đường dưới đây tính từ root repo đó, có thể đọc lại bằng permalink commit:

| Đường code | Quan sát cụ thể | Hệ quả |
| --- | --- | --- |
| `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/installer/CopyExecutor.java:20,28,41` | Copy native libs và APK từ một `baseCodePath`. | Chưa có code-store cho cả cluster. |
| `…/core/system/pm/BPackage.java:47,136,226,716` | State/parser-copy/Parcel chỉ giữ base path ở các điểm này. | Copy thêm split ở adapter không đủ giữ metadata sau restart. |
| `…/core/system/pm/PackageManagerCompat.java:371` | Resource loader thêm một asset path base. | Resources của split chưa có đường đầy đủ. |
| `…/fake/frameworks/BPackageManager.java:663,681,830` | Các fallback đặt UID 0 hoặc splitNames rỗng. | Không dùng fallback này làm bằng chứng package thật đã hợp lệ. |
| `…/utils/NativeUtils.java:28,73` | Dùng CPU_ABI; bỏ copy lib nếu trùng kích thước. | Không chứng minh chọn ABI/cập nhật lib đúng; trùng size không chứng minh trùng nội dung. |
| `…/BlackBoxCore.java:89–95,1634,1656` | Cài nhiều crash handler, StackTraceFilter/SocialMediaAppCrashPrevention; có nhánh disableResourceLoading. | Không thể đưa thẳng baseline vào sản phẩm và coi crash bị nuốt là ổn định. |
| `…/utils/SimpleCrashFix.java:50–85` và `…/utils/LogSender.java` | Handler gọi sendLogs; có đường gửi log ngoài khi có cấu hình đích. | Chưa đủ kiểm soát log riêng tư theo yêu cầu Fonesi. Không khẳng định đã gửi dữ liệu của người dùng; code chưa được chạy. |
| `Bcore/src/main/cpp/Android.mk:5,38,40` | Link archive Dobby prebuilt; build VirtualSpoof/AntiDetection. | Root Apache-2.0 không thay hồ sơ nguồn/license của mọi binary. Chưa có provenance đầy đủ và audit để vendor. |

Repo có Java/native source nhưng chưa vượt cổng E2. Không chạy Gradle/build scripts của fork trong lượt này; không ghi “build thất bại” khi chưa chạy. Không nhập spoof/log/crash fallback rồi mới tìm cách chứng minh an toàn sau.

Tag `2.0-r1beta`, commit `25f73f8b41551c65dca21cdddfe216f38c49012d`, cũng đã đọc qua Git. Tree tag không có thư mục `Bcore`, trong khi settings vẫn include và app có hướng dẫn lấy AAR ngoài; dependency project Bcore bị comment. Tag này không cung cấp baseline engine source đầy đủ có thể thay thế ngay. Nhánh [nnjun/BlackBox](https://github.com/nnjun/BlackBox) revision `141851e35382b9d8ad5117cccc278d63a00d66e4` không có engine source tại checkout khảo sát. Nhánh [tokiii/BlackBox](https://github.com/tokiii/BlackBox) revision `eba9d8734a85d13f147da0bde022b8f23baf1ced` cũng chưa cho thấy một đường split đầy đủ trong phần source đã đọc; chưa build/runtime, không kết luận tương thích từ README.

## Điều kiện để triển khai provider thay thế

1. Có baseline source/native/dependency và giấy phép/provenance đủ, init/log/network/quyền được kiểm soát; không có spoof hoặc crash handler che lỗi ngoài yêu cầu.
2. Plan patch cluster parser/chữ ký/dependency split, BPackage Parcel/settings, private immutable code-store/atomic publish, native ABI/libs, ApplicationInfo/PackageInfo, resources/classloader và readback đầy đủ. Dùng chữ ký gốc, không re-sign APK khách.
3. Provider build opt-in/namespace mới, không nhận journal/guest cũ như dữ liệu riêng của mình. Build/audit manifest/native alignment trước khi thử.
4. Bộ split do dự án kiểm soát để kiểm tra package/version/signers/paths/update giữ data; sau đó chạy payload trên thiết bị trước tài khoản. File-copy tests không chứng minh Android đã thực thi guest.

Các mục này vẫn là công việc kỹ thuật chưa hoàn thành, không chỉ là “chờ test điện thoại”. Đợt kiểm tra thiết bị có thể hoãn, nhưng không thể tuyên bố toàn bộ code sản phẩm xong khi split/Android mới còn chưa giải quyết.
