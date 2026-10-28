import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val candidate = providers.gradleProperty("engineCandidate").orNull == "true"
val sdkCommit = "3826a2fa1ac492fbbe7435ccb52074c1e2b702de"
val toolsRoot = providers.environmentVariable("FONESI_TOOLS_DIR").orElse("/workspace/.fonesi-tools")
val sdkFile = file("${toolsRoot.get()}/engine-sdk/spacecore/$sdkCommit/flib-release.aar")
val verifyCandidateSdk by tasks.registering {
    if (candidate) inputs.file(sdkFile)
    doLast {
        if (candidate) check(MessageDigest.getInstance("SHA-256").digest(sdkFile.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) } ==
            "4e45a474a5ab5764ad80818c2855c4fbd8540f0a540f975cf85a6f0dd8809b1f") { "Unverified candidate SDK" }
    }
}
android {
    namespace = "vn.fonesi.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "vn.fonesi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = if (candidate) "0.5.0-candidate" else "0.5.0-cloud"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").java.srcDir(if (candidate) "src/candidate/java" else "src/cloud/java")
    if (candidate) {
        sourceSets.getByName("debug").manifest.srcFile("src/candidate/AndroidManifest.xml")
        defaultConfig.ndk { abiFilters += "arm64-v8a" }
        packaging.jniLibs.useLegacyPackaging = true
    }
    sourceSets.getByName("test").resources.srcDir("schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            // Scope Robolectric's lock/cache to this test JVM; do not change the host HOME.
            val testUserDirectory = layout.buildDirectory.dir("robolectric-user").get().asFile
            it.doFirst { check(testUserDirectory.mkdirs() || testUserDirectory.isDirectory) }
            it.systemProperty("user.home", testUserDirectory.absolutePath)
            val tools = providers.environmentVariable("FONESI_TOOLS_DIR").orElse("/workspace/.fonesi-tools").get()
            it.systemProperty("maven.repo.local", "$tools/robolectric-m2")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort",
                "http.nonProxyHosts", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { value -> it.systemProperty(key, value) }
            }
        }
    }
}
kotlin { jvmToolchain(21) }
tasks.named("preBuild") { dependsOn(verifyCandidateSdk) }
androidComponents { beforeVariants(selector().withBuildType("release")) { if (candidate) it.enable = false } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":apk-runtime"))
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    if (candidate) {
        implementation(files(sdkFile))
        implementation("com.tencent:mmkv-static:1.2.10")
        implementation("com.google.code.gson:gson:2.9.1")
        implementation("com.google.android.material:material:1.12.0")
    }
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
}
