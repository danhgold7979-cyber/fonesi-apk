plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "vn.fonesi.payload"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "vn.fonesi.payload"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-payload"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }
