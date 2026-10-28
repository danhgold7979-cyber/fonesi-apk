# E19 — Native SQLite + Room schema 4 + clone style integration results

Date: 2026-10-28. This follows the [pre-code E19 plan](E19_CLONE_STYLE_PLAN.md). Baseline is the successfully integrated E18 branch, not a claim that the full 8-phase product is finished.

## Actual GitHub Actions evidence

- E18 preceding cumulative app/source debug integration: [run 40647494054](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40647494054), **SUCCESS** at `b1215755d26c9098b5bd243f210e6f32c6b8002c` (299 Core/APK/App tests, audited cloud/SpaceCore candidate/native source ARM64, 7-entry phone-test ZIP).
- E19 style/Room 4 + full build: [run 40648215912](https://github.com/danhgold7979-cyber/fonesi-apk/actions/runs/40648215912), **SUCCESS** at `9e592f904efe412f47191ead9a84040fc627fdae`. The workflow reported **307 Core/APK/App tests PASS**, including new style policy and native SQLite migration checks; candidate build/lint/signature/permission audit, source ARM64/ELF/JNI, synthetic probe static audit, and exact seven-entry debug phone ZIP checksum verification also passed. None of these 307 are real guest clone/account tests.
- Candidate debug artifact from run 40648215912: `fonesi-E19-styled-candidate-0.5.0-DEBUG-NOT-RELEASE` (ID `12798796450`). Debug phone test bundle: `fonesi-E19-styled-phone-test-zip-DEBUG-NOT-RELEASE` (ID `12799320389`). Retention seven days.
- The **exact KSP-generated schema** from this green run was extracted, validated as version 4 and committed unchanged to `app/schemas/vn.fonesi.app.storage.FonesiDatabase/4.json` at `c7616fa`, identityHash `bab5e9209b974b568780f922ff01622d`. A final branch-head CI run is automatically triggered by this source-file addition and must remain green to treat the new HEAD as fully checked.

## Changes and guarantees

- Six audited accent keys, strictly bounded and normalized 1–2 Unicode-codepoint badge. No HTML, URLs, external icons, paths, passwords or keys stored.
- Room explicit migration 3→4 adds `accentKey` (default `teal`) and `badgeText` (default NULL) **without destroying older clone records, stable launch tokens, queue or SDK reservations**. Fail-closed unknown schema test moves from unknown v4 to unknown v5.
- Clone list shows a badge/accent, user may edit while unlocked; DAO updates only the matching engineKey/cloneId and no guest SDK function is called. A successful style write attempts pinned shortcut icon refresh. Hardware launchers are not yet exercised.
- Configuration export still intentionally includes names and groups only; no new data leaks or unsupported account backup claims.

## Remaining original product blockers

Candidate still imports **one complete ARM64 APK** only, with API26–34/4KB guard. Android 34 x86_64 synthetic code, Context, Application and Binder tests from E11–E17 are not proof that a real Facebook/Zalo/Threads/TikTok/etc. app guest can be installed, receive Play Services/notifications, securely isolate two accounts or update while preserving sessions. Physical Samsung ARM64 and other-vendor tests, full split/isolated resource/classloader components, lifecycle/permissions/binder mediation and signed release remain **NOT VERIFIED / NOT IMPLEMENTED**. Do not ship this debug candidate as a finished full tool.
