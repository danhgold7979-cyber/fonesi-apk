# P0 next slice proposal — root-approved cloud foundation entry

Scope: independent native Java/View foundation and a pure non-executing update-preview model; no provider implementation, no old source/tests. Keep main stable. Existing phase-00 branch only. Full P0 acceptance still requires actual compile/package receipts and baseline APK; no count closed merely from host tests.

Inputs: frozen d87dfa7 master/tasks/362 assertions, approved Java/View SQLite decision, provisional min26/compile36/target36, preserved green branding, no provider. Toolchain candidate Gradle8.13 / AGP8.13.2 / build-tools35.0.0 / JDK17 with exact official hashes. Local JDK21 receipts explicitly labeled; absent SDK prevents local COMPILE.

Outputs: independently written settings/build/manifest/View activity, disabled guest controls with truthful reason, no fake sample clones, safe visible unread-catalog state; pure Java preview decision tested independently; new phase-branch workflow and exact-source packaging manifest checks, developer reproducibility guide. No AndroidX/network/guest libraries needed for initial Views. SQLite active write/migration deferred to separately reviewed P3 contract, not invented here.

Interfaces: preview model returns display-only reasons for unavailable provider/source uncertainty/version comparison. It must not return executable handles, auth grants, READY state or success receipts. A hypothetical source observation never authenticates itself. UI has no launch-original or web fallback, no credential entry, no provider purchase links. All mutation controls disabled until eligible provider AND accepted auth AND source/scope/readback gates.

Error/cancel/recovery: invalid/missing observations produce unavailable/error rather than guessed eligibility; repeated clicks on disabled controls have no effects; Activity recreation constructs same inert state; no persistent user data touched. Build tooling stops on missing SDK or unverified binaries, never accepts new SDK license automatically. No debug APK delivered as working clone manager.

Tests: host positive disabled-state/unread-catalog and display-preview behavior; negative unknown/stale source/provider never runnable; boundary immutable values/null/unsupported operations; no source secret logging. Compile/lint/package signed manifest check verifies own package/min/target/exported activity/no guest permissions/backup-disabled; exact SHA and artifact hash recorded. Android lifecycle/UI rendering remains NOT_RUN unless framework actually executed. Check old implementation/oracle paths absent.

Entry: root accepted documentary checkpoint d87dfa73fae1bce6ab8188d71f910a6e9d49e85d on 2026-11-01 and approved this bounded T03 cloud source slice. This does not close T01 cleanup until root reports remote archival/retirement. SDK/CI and provider gates remain distinct. Parent owns publication and exact-SHA CI monitoring approval.
