import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("android")
}

val sdkCommit = "3826a2fa1ac492fbbe7435ccb52074c1e2b702de"
val toolsRoot = providers.environmentVariable("FONESI_TOOLS_DIR").orElse("/workspace/.fonesi-tools")
val sdkFile = file("${toolsRoot.get()}/engine-sdk/spacecore/$sdkCommit/flib-release.aar")
val verifyEngineSdk by tasks.registering {
    inputs.file(sdkFile)
    doLast {
        check(sdkFile.isFile) { "Run python3 scripts/fetch_engine_sdk.py first" }
        val hash = MessageDigest.getInstance("SHA-256").digest(sdkFile.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == "4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f") {
            "SDK checksum mismatch; refusing to build"
        }
    }
}

android {
    namespace = "vn.fonesi.probe"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "vn.fonesi.probe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-engine-probe"
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs.useLegacyPackaging = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}
tasks.named("preBuild") { dependsOn(verifyEngineSdk) }
dependencies {
    implementation(project(":core"))
    implementation(files(sdkFile))
    implementation("com.tencent:mmkv-static:1.2.10")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.google.android.material:material:1.12.0")
}
