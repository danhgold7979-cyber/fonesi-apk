# Fresh Android manager scaffold

Independent native Java17 presentation project, not an engine or imported old app.
Distinct applicationId `vn.fonesi.rebuild.debug`; all unsupported actions disabled.
No requested permissions, storage migration, provider, Java21 kernel import or user-data access.

Only the inventory-gated hosted CI experiment is authorized for build. It requires
preinstalled API36/build-tools36.0.0/JDK17; no SDK installation or license action.
The root verified Gradle8.13 wrapper is reused with `-p rebuild-android`; root
historical Gradle settings are not evaluated. Debug APK is CI review only, not release.
Source tests and APK inspection do not substitute for device/UI/isolation acceptance.
