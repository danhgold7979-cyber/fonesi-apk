# E79 — AOSP signed synthetic scoped Application process-restart proof (technical results)

Date: 2026-10-30. Repository: [danhgold7979-cyber/fonesi-apk](https://github.com/danhgold7979-cyber/fonesi-apk). Original roadmap **Product Phase 1 steps 7–8**, controlled synthetic app proof only. Branch `phase/e79-synthetic-process-restart-durability-20261030`, [Draft PR #53](https://github.com/danhgold7979-cyber/fonesi-apk/pull/53) stacked on E78. E78 parent [CI #41000533354](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41000533354) SUCCESS.

## Source and exact CI acceptance

- **E79 source/test SHA:** `fb23878403418f8fbaa77fbe2b35a9b7ffa763bc` — [commit](https://github.com/danhgold7979-cyber/fonesi-apk/commit/fb23878403418f8fbaa77fbe2b35a9b7ffa763bc).
- **E79 exact-source CI:** [#41014237064](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41014237064), completed **SUCCESS**, 17 substantive steps. [Signed synthetic TEST-ONLY artifact #12960090780](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41014237064/artifacts/12960090780).
- Cloud: **510 tests** passed; candidate: **510 tests** passed; pinned JDK21/Gradle/SDK36, APK signing/permission audit, lint and source runtime AAR/native checks passed.
- Source-engine: **63 JVM tests** passed; strict signed private Activity/Application preflight executed. Python emulator/receipt/fingerprint: **21 tests** passed. Payload process-marker: **7 JVM tests**, 0 failure/error/skip.
- E40–E79 source audits passed. Standalone signed host and payload probe APKs built/linted; AOSP API 34 x86_64 emulator image installed and booted in software emulation.
- Earlier `79051ef4f6b3ef7ee0b7196859f420eb4ce25576` [CI #41002256866](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/41002256866) **FAILED** compiling `probe-payload/src/main/java/vn/fonesi/payload/SyntheticApplicationProcessMarker.java` because the enclosing class lacked its final `}`. The narrowly scoped `fb23878` fix added only that brace; full CI then PASSED. The failure is not retroactively marked PASS.

## Android emulator evidence (two distinct processes)

| Runtime receipt field | Write process | Verify after force-stop |
| --- | --- | --- |
| Host PID | 2567 | 3206 |
| Host UID | 10142 | 10142 |
| Synthetic Application count per scope | A=1, B=1 | A=2, B=2 |
| Shared `e79_private_cluster_id` | `3c795801-e617-452c-b60a-027249659733` | same |
| Signed PRIVATE code image | exact readback | same re-opened cluster |
| Process token | distinct first canonical UUID | distinct second canonical UUID |
| SharedPreferences/files/SQLite A/B | fresh scopes written/read | exact previous markers read |
| Cross-scope test | isolated | still isolated |
| Context reconstruction | passed | passed |

The runner issued Android `am force-stop` on the original host package and confirmed its dedicated process absent before relaunch. Both stage receipts have different run IDs and match scope UUIDs, PRIVATE APK cluster ID, nonsecret test marker values, source commit and APK SHA-256. No verify-stage reseeding or replacement of data was accepted.

- Source SHA in both receipts: `fb23878403418f8fbaa77fbe2b35a9b7ffa763bc`.
- Signed host APK SHA-256: `ea9e46398187e8f3c158b14f3348d3529d70917dcc9b8d01977163d5f95a8b8d`.
- Signed synthetic payload APK SHA-256: `2a3e8a9dfafd2928c74df1565edeef82a95a94f0c7caccd80b25084f812747e2`.
- Evidence result: `PASS_E79_SYNTHETIC_SCOPED_DATA_PROCESS_RESTART_ONLY`; both separate stage receipts and `e11-evidence.json` were uploaded.

## Strict negative claims / product acceptance

This **does not** establish a real guest Android lifecycle: `guest_application_registered_with_activity_thread=false`, `guest_application_registered_with_package_manager=false`, `full_guest_application_lifecycle_verified=false`, `actual_guest_activity_attached=false`, `real_guest_activity_registered=false`, `third_party_component_executed=false`, `kernel_uid_isolation=false`, `social_accounts_tested=false`, `production_clone_accepted=false`. Both synthetic scopes share the same host UID; this is logical namespace separation **not an OS security boundary**. Emulator software execution is not physical Samsung acceptance.

Candidate provider still requires one full APK: `RuntimeBindings.supportsSplits=false`, `SpaceCoreBackend` refuses multiple APK paths. No third-party real app, account, update or device has been accepted, and real guest Activity/Binder/PackageManager/permissions/UID/account mediation, full production split provider, phone compatibility and final signing remain open. **Product readiness is 28.00% under the unchanged weighted rubric, 0 real third-party clones accepted.**

The reporting/docs commit that introduces this file creates a new HEAD SHA. Do **not** call that SHA separately CI-verified before its own exact-source workflow passes. Preserve Draft PR #53, its E78 stacked base and `main`; do not merge/release on synthetic receipts alone.
