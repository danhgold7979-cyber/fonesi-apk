plugins {
    id("com.android.application")
    kotlin("android")
}
val payloadRevision = providers.gradleProperty("payloadTestRevision").orNull?.toInt() ?: 1
require(payloadRevision > 0)
android {
    namespace = "vn.fonesi.payload"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "vn.fonesi.payload"
        minSdk = 26
        targetSdk = 36
        versionCode = payloadRevision
        versionName = "0.1.0-payload-r$payloadRevision"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }

dependencies { testImplementation("junit:junit:4.13.2") }
