// Legacy root build is retired. Historical source and tests remain reference-only.
// Use the explicitly scoped fresh manager build; do not silently build old modules.
throw GradleException("FONESI_LEGACY_ROOT_BUILD_DISABLED: use ./gradlew -p rebuild-android; legacy source is reference-only.")
