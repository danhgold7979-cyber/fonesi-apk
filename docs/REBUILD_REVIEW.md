# Rà soát làm lại Fonesi APK — 30/10/2026

## Phạm vi và sửa nhận định ban đầu

Repo này là bộ quản lý nhiều vùng ứng dụng/tài khoản Android không root, không phải trình sửa APK. Dấu **đỏ chỉ dùng nhận diện luồng trao đổi APK**; không có yêu cầu đổi màu thương hiệu ứng dụng. Không đụng Auto/Edit.

Audit main ban đầu chỉ mô tả trạng thái lịch sử E9; **không đại diện toàn bộ dự án**. Sau khi rà mọi remote ref, nguồn tham chiếu mới nhất là `phase/e82-public-component-factory-feasibility-20261030@2fac016e3b28e4d9f21390c221cad1df7a382c86`, đã có E83/E84 và kế hoạch E85. `main@c055df600f87ee9ddf0cd5d6bfc67d80d145e79d` và `develop/guest-runtime-e10-20261028@a9aa5a8d53b0c82eabcc071fd0e0fa19fdcc612c` là tổ tiên của nhánh đó. Tên nhánh E82 không có nghĩa nội dung dừng ở E82.

Không chạy script/binary/APK, không thay mã hoặc xóa dữ liệu trong lượt audit này. Chỉ đọc Git/source/tài liệu và tạo hai tài liệu rebuild. Không tìm thấy AGENTS.md trong main hoặc cây nhánh E82. Các kết quả CI dưới đây là **bằng chứng được ghi trong repo**, chưa tải/kiểm độc lập artifact hoặc chạy lại tại checkout rebuild.

## Phục hồi trước khi xóa

- Full bundle do điều phối viên tạo: `/tmp/fonesi-apk-pre-rebuild-20261030.bundle`, 1,910,656 byte, 65 advertised refs.
- SHA-256 đo trực tiếp: `d0516e03126dd60b20e5dc954065dd694469d0d6b3689691cbc7e4717866404a`.
- Điều phối viên báo đã kiểm restore main khớp. Chưa xem đó là bằng chứng restore **tất cả** ref; cần so map ref→SHA giữa bundle và phục hồi riêng.
- Có 61 remote refs tính cả origin/HEAD; **14 đầu nhánh không phải tổ tiên E82**. Giữ nguyên mọi đầu nhánh, không dùng backup main làm bản phục hồi duy nhất.
- E82 so với main thay đổi 396 tệp, +35,710/-103 dòng. Không xóa theo kết luận main-only.
- Khuyến nghị baseline thiết kế là hợp nhất tri thức từ E82 + review riêng 14 nhánh rẽ, không merge mù. Backup all-ref bền vững, ref manifest và restore test phải có trước khi thay cây source; `/tmp` không phải lưu trữ dài hạn.

## Bằng chứng kỹ thuật mới nhất

Nguồn của các đường dẫn dưới đây là E82 SHA nói trên, **không phải các tệp main đang checkout**.

| Hạng mục | Bằng chứng và giới hạn |
|---|---|
| E83 typed split-provider contract | `docs/phases/E83_PRODUCTION_SPLIT_PROVIDER_CONTRACT_RESULTS.md`: exact source/test `58d52f4aa05c8c8d5256e7125b522815418b1e02`, workflow 41083456690, authorization 41083456286, artifact 12992820772. Ghi 521 test mỗi cloud/candidate, source JVM 63, native host 18, layout 7, payload 14; không cộng hai cấu hình thành test độc lập. |
| E82 Android execution | `docs/phases/E82_PUBLIC_COMPONENT_FACTORY_FEASIBILITY_RESULTS.md`: framework thực sự gọi onCreate/onResume qua AppComponentFactory, Context/Window khác null, nhưng là host stub, host package/process/UID 10142; không phải guest package/UID hoặc third-party acceptance. |
| E78–E81 synthetic execution | Có Application scoped data, process restart, private Activity construction/method proofs; không còn đúng khi nói toàn dự án chưa chạy gì trên Android. |
| E84 provider audit | `docs/phases/E84_ALTERNATIVE_SPLIT_PROVIDER_FEASIBILITY_RESULTS.md`: không provider nào đạt; ISEKHON có fake-signature fallback và update phá live state trước copy xong; commercial source cần quyền riêng; TeguFy chỉ base APK. Không tích hợp fork. |
| E85 | Chỉ có `docs/phases/E85_MANAGED_GUEST_ENGINE_SPLIT_ORCHESTRATION_PLAN.md`; chưa có bằng chứng implementation/PASS. |
| Sản phẩm | 0 third-party clone được nghiệm thu; Samsung/tài khoản thật/chữ ký release chưa đạt. 28% là heuristic cũ, không phải đo lường tiến độ có độ chính xác hai chữ số. |

E82 head khác exact source/test 58d52f4 ở 9 tệp tài liệu; không tự gọi reporting HEAD đã chạy CI. Ghi riêng source SHA, report SHA, artifact SHA và môi trường.

## Vấn đề và hướng sửa plan

1. **Baseline sai nếu chỉ main:** giữ mọi đầu nhánh; chuyển mọi phát biểu main-only thành lịch sử. Rebuild phải bảo toàn specification/test của schema 6, ownership/reference ledger, engine-scoped queue, source-authority quarantine, style/badge và background journal recovery đã tồn tại.
2. **Tài liệu current bị chồng lịch sử:** STATUS/PRODUCT_PROGRESS ghép nhiều đoạn đều tự xưng latest, có E80/E82/E83 cùng tồn tại. Tách một trang trạng thái hiện hành và archive theo SHA; không xóa bằng chứng lịch sử.
3. **Mâu thuẫn worker:** PRODUCT_PROGRESS còn nói no background journal worker; UPDATE_AUTOMATION nói không worker. Code `app/.../update/UpdateJournalRecoveryWorker.kt` đã có WorkManager chỉ sửa journal, không SDK. Sửa thuật ngữ thành “có metadata recovery nền; chưa có background guest import/update được nghiệm thu”.
4. **Threat model đã có, không tạo lại bản yếu hơn:** `docs/engine/THREAT_MODEL.md` E35 coi guest có thể hostile. Nó phân biệt HOST_UID_NAMESPACE/SEPARATE_OS_UID/SEPARATE_GUEST_OS. Native routing/same-UID synthetic không đủ chứng minh chống guest đọc dữ liệu nhau. Giữ hostile-app yêu cầu; ADR phải giải quyết khả thi trước provider production, không tự hạ xuống cooperative-only.
5. **Provider vẫn là critical path:** E83 là contract, E85 là orchestration dự kiến; không phải engine. E82 host lifecycle không giải quyết PackageManager/Binder/permission/service/provider/real-account isolation.
6. **Kế hoạch E85 baseline cũ:** đang trỏ 387a92d; cập nhật exact source/test cuối 58d52f4 khi chuyển thành work package rebuild. Bổ sung bảng failure boundary, identity/readback, snapshot lifetime, shared affected-set và independently observed provider state.
7. **Kiểm thử hiện tại nhiều lớp tốt nhưng chưa acceptance:** giữ signed fixtures, negative/race cases, SQLite migrations, source-bound emulator receipts và no-fake-capability guards. Test mới phải đo runtime/provider cần thiết, không chỉ thêm source-text checks để tăng số test.
8. **Migration quan trọng:** `FonesiDatabase.kt` mới là schema 6, migrations 1→6. Không thiết kế reset từ schema 3/main hoặc chuyển journal guest SpaceCore sang provider khác. Rebuild source không cho phép xóa tài khoản trên máy.
9. **Giấy phép/chi phí và kiến trúc:** user giao rebuild code/plan, không tự mua SDK, liên hệ vendor, đưa tài khoản vào fork hoặc đổi sang VM/root rồi gọi tương đương. Tách quyết định thật sự ngoài phạm vi; tiếp tục việc độc lập.
10. **Màu:** đỏ chỉ marker trao đổi; rút lại gợi ý biến theme xanh hiện tại thành lỗi requirement.

## Gate đề xuất trước triển khai lại

R0: bundle all-ref + restore ref-map + phân loại 14 nhánh rẽ. R1: requirement traceability từ PLAN/ROADMAP và E35 acceptance/threat model; mỗi yêu cầu có tầng chứng cứ. R2: baseline rebuild có test/protocol oracle phục hồi, toolchain đã inspect, schema migration policy. R3: engine feasibility proof có Android execution và đối chiếu trust boundary, không chỉ facade. R4: full cluster activation/update/recovery và lifecycle, không mất dữ liệu. R5: manager/shortcut/config/style/lock với tests. R6: mandatory app và phone matrix, synthetic trước tài khoản. R7: ký/nâng cấp release và tài liệu giới hạn.

Có thể làm các phần độc lập song song; không đóng gate runtime bằng UI/unit tests. Không bỏ app bắt buộc khỏi phạm vi hoặc hứa tương thích mọi Android. Không lấy việc user giao toàn quyền làm bằng chứng kỹ thuật đã đạt.

## Các đầu nhánh chưa được E82 bao hàm

Danh sách sau từ `git merge-base --is-ancestor`; không-ancestor không đồng nghĩa code mất, vì có thể đã reimplement/squash. `git cherry` cũng không chứng minh tương đương ngữ nghĩa. Phải đọc diff và test tương ứng trước quyết định tái dùng/loại.

### `phase/e11-emulator-proof-20261028`

Head `4097e934c74f82a950610fe225dc3387451a35cc`. Unique history:

- `4097e93 docs(E11): update live status with scoped Android x86_64 runtime PASS [skip ci]`
- `40ba620 docs(E11): record actual Android x86_64 private DEX and resource PASS [skip ci]`

### `phase/e12-synthetic-guest-execution-20261028`

Head `5e43dd4b49f13df3ed8566f3e3c4885d84891ddf`. Unique history:

- `5e43dd4 fix(E12): guard API 28 process-name lookup without altering Android minSdk or lint rules`
- `04fb8ba docs(E12): diagnose Android API 28 lint gate and plan explicit version guard`

### `phase/e44-durable-retained-source-quarantine-20261029`

Head `b5bd4d8c63f3ebf081f65ea095ef3bbdff93eb23`. Unique history:

- `b5bd4d8 test(E44): enforce Unit return for Room source-policy JUnit regression`
- `0f41474 ci(E44): verify durable private-source quarantine and existing provider guards`
- `5acd95f test(E44): prove durable source quarantine and rollback of retained owner marker`
- `ce64b30 fix(E44): block retained sources before legacy catalog/provider update`
- `31a1b0a feat(E44): persist immutable retained source authority with Room task ownership`
- `d4fac67 docs(E44): schedule journal source marker and fail-closed provider gate`
- `b4c895b docs(E44): plan durable retained-source execution quarantine before code`

### `phase/e46-foreground-source-authority-20261029`

Head `c30fe2b6dcd118231286c4f29577a8b35076ab28`. Unique history:

- `c30fe2b docs(E46): record verified 394-test cloud/candidate source authority result`
- `ef06a06 fix(E46): keep foreground original-source automation off retained and E41-owned update tasks`
- `9320c56 docs(E46): plan fail-closed foreground private-source guard before code`

### `phase/e47-original-source-confirmation-20261029`

Head `2fbc15676af5cbf1af3064395eae260e0c15f3ad`. Unique history:

- `2fbc156 docs(E47): record verified 402-test source authority confirmation result`
- `2ccffc5 fix(E47): reject original source queue consent across active private source ownership`
- `be37ef0 docs(E47): plan atomic original-source confirmation authority preflight`
- `ef06a06 fix(E46): keep foreground original-source automation off retained and E41-owned update tasks`
- `9320c56 docs(E46): plan fail-closed foreground private-source guard before code`

### `phase/e48-retained-recovery-quarantine-20261029`

Head `d992c6377c0a726a56fa6a8c9c8d180e244b9c57`. Unique history:

- `d992c63 docs(E48): record 395-test recovery quarantine acceptance and open blockers`

### `phase/e49-source-authority-gates-integration-20261029`

Head `f9e137140e6c60392977715f37a509e437e18b38`. Unique history:

- `f9e1371 docs(E49): record 407-test integrated source authority acceptance and product blockers`

### `phase/e50-preview-private-source-authority-20261029`

Head `718895ad30e8fa5974476523114d566ec729187e`. Unique history:

- `718895a docs(E50): record exact-source 415-test preview authority verification`

### `phase/e51-engine-scoped-queue-recovery-20261029`

Head `31df2324ac1dd3223c24f81f7c4936a05b178083`. Unique history:

- `31df232 fix(E51): scope shared queue execution and recovery by engine identity`
- `c790f21 docs(E51): plan engine-scoped queue and recovery before code`

### `phase/e55-pinned-sdk-capability-audit-20261029`

Head `a27dd4aa632c9bfefa48f0b736bca26908ef9e53`. Unique history:

- `a27dd4a E55: plan pinned SDK split-import capability investigation before code`

### `phase/e68-private-signed-component-catalog-20261029`

Head `f4ae0d7e9164342b2326b0cb72a410a41db3062d`. Unique history:

- `f4ae0d7 docs(E68): record exact-source 492-test PASS and unchanged product blockers`

### `phase/e69-source-loader-signed-component-readback-20261029`

Head `23206e05297ac5dbb316dff9dd34571c3ed9354b`. Unique history:

- `23206e0 docs(E69): record exact-source AAR and 492-test CI PASS; integrate verified E68 report`
- `f4ae0d7 docs(E68): record exact-source 492-test PASS and unchanged product blockers`

### `phase/e75-synthetic-private-activity-constructor-proof-20261029`

Head `c0a2dbb0a6a04f898a8e9ca6f53a041ddc667bc0`. Unique history:

- `c0a2dbb feat(E75): require strict owned-emulator unattached synthetic Activity receipt and SHA pins`
- `e13c8bf test(E75): pure Python regression suite for strict unattached Activity receipts`
- `5c23da9 feat(E75): fail-closed signed synthetic Activity constructor receipt contract`
- `ef4602e audit(E74/E75): keep E74 class-only and explicitly gate E75 constructor`
- `527c21b feat(E75): construct only signed synthetic PRIVATE Activity without attachment or lifecycle`
- `7601b2a docs(E75): register unattached synthetic Activity constructor gates`
- `64dbad0 docs(E75): plan controlled PRIVATE synthetic Activity constructor before code`

### `phase/e76-activity-preflight-no-class-init-20261030`

Head `8a53600ddeb107840ab7189413c5a5aa7250933e`. Unique history:

- `8a53600 docs(E76): record exact-source 510-test PASS and static-init boundary`

## Bước tiếp theo cụ thể

1. Điều phối viên xác minh all-ref restore, sao lưu ngoài /tmp và chốt manifest.
2. Review code-bearing divergent heads E12/E44/E46/E47/E51/alternate E75; giữ doc-only heads làm evidence archive. E12 một commit patch-equivalent đã thấy; không blanket-drop nhánh.
3. Chốt master/detailed rebuild plan từ latest evidence, cập nhật worker/schema/threat model và thay stale status index.
4. Chỉ sau R0/R1 mới thay source tree có kiểm soát; giữ old tests/specification làm regression oracle, không hạ assertion để pass.
5. Kiểm CI/artifact exact-source có quyền truy cập rồi chạy lại build/tests đã inspect; tách local fresh result khỏi historical receipt.

## Recovery verification bổ sung — 30/10/2026

Đã tạo bare repo rỗng `/workspace/shared/fonesi-apk-recovery-20261030/restore.git`, fetch bundle với `+refs/*:refs/*`, so map ref→object ID: **64/64 refs khớp**, không thiếu/thừa; HEAD thứ 65 trỏ main đúng. Bundle workspace SHA-256 giữ nguyên. Ref manifest lưu cạnh bundle. Gate phục hồi Git đạt; semantic reconciliation đang tiếp tục, không xóa code.
