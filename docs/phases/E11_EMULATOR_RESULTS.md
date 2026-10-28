# E11 Android 34 runtime — kết quả kiểm chứng thực tế (28/10/2026)

**Kết luận có giới hạn: PASS_E11_SYNTHETIC_ONLY.** Không phải sản phẩm clone đa ứng dụng hoàn chỉnh. Kế hoạch [E11_EMULATOR_PLAN.md](E11_EMULATOR_PLAN.md) được commit trước code; nguồn nền là 8 phase/32 bước trong [ROADMAP](../ROADMAP.md), E10/E11 ở [STATUS](../STATUS.md).

## GitHub Actions và đầu vào

- CI [40640306977](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40640306977): **SUCCESS**; source SHA `bc02df3ec8430da2515f6686bc9e661fe3ba43f9`.
- Android tools từ bootstrap pinned (SDK 36, emulator 37.2.12), AOSP Android 34 x86_64, KVM enabled. AVD/ADB serial do runner tạo riêng; đã dừng emulator sau thử.
- Bản synthetic mới build/lint và apksigner verify trước cài trên cùng run. APK probe SHA-256 `f365a947b263ed41f84ec0b7d865f0a288cd019b1db9149c74a7f5838b85de9f`; payload SHA-256 `a9d684b07e453b9c0d0926856d791029c19895b6450fc9860715f6852eded977`.
- Artifact `fonesi-E11-rebuilt-android-probe` ID `12795780780` và `fonesi-E11-android-emulator-evidence` ID `12795029840` (hạn lưu CI ngày 04/11/2026). Bản APK probe **không phải** APK Fonesi phát hành.

## Biên nhận Android độc lập

Dữ liệu từ chính child process trong AVD (đã đối chiếu artifact JSON, không đọc từ báo cáo unit test):
- `complete=true`, `passed=true`, `run_id=369a3c2f-1f94-4a3c-afad-7129e931d9c6`, `api=34`, `abi=x86_64`.
- `signed_private_cluster=true`: private APK đã được đối chiếu chữ ký/hash và readback.
- `dex_class_loader=true`: nạp class trong payload bằng ClassLoader riêng từ code private.
- `private_resources=true`: tìm được resource marker do dự án tạo từ APK private.
- `activation_pending=true`: ledger không nhận code loading là runtime activation/account.
- `guest_context_executed=false`, `guest_activity_executed=false`, `binder_provider_tested=false`, `social_accounts_tested=false`.
- Runner `android_boot_verified=true`, `android_apks_installed=true`, `android_code_image_verified=true`, `emulator_stopped=true`.

## Lỗi đã xử lý và giới hạn nghiệm thu

Lượt AVD đầu [40639866184](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40639866184) boot/cài APK/pass receipt nhưng báo `IllegalArgumentException` và **FAIL** phần private code load. Trước lần thử lại, sửa riêng E11 synthetic service để chuẩn hóa đường dẫn `Context.getDataDir/getFilesDir` của Android, xác minh các đường dẫn vẫn trong vùng dữ liệu host rồi mới đưa vào `PrivateApkStore` (không nới guard symlink chung). Thêm `failure_stage` an toàn để chẩn đoán các lỗi sau. Rebuild binary từ source branch, verify signer và kiểm tra trong AVD mới: PASS. Không tự nhận đây là kết luận forensic tuyệt đối về nguyên nhân gốc của lần fail cũ; chỉ ghi đường sửa và bằng chứng kiểm chứng lại.

**Không được gọi là hoàn thiện phase 1/2/5/7 hoặc clone app thành công.** Vẫn thiếu guest Context/Application/Activity, lifecycle, Binder/service/provider/intent routing, isolatedSplits/resource classloader graph, native linker namespace, update/readback giữ phiên, giao diện với provider mới, Samsung ARM64/One UI và ma trận Facebook/Messenger/Zalo/TikTok/Threads/X/Shopee/YouTube, worker nền và bản ký release. E12 phải lập plan chi tiết từ master trước khi viết các phần này. Không chuyển dữ liệu SpaceCore cũ hoặc ghép app gốc làm guest.
