plugins { id("com.android.application") }
android {
    namespace = "vn.fonesi.nativeprobe"
    buildFeatures { aidl = true }
    compileSdk = 36
    defaultConfig {
        applicationId = "vn.fonesi.nativeprobe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-native-probe"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    testImplementation("junit:junit:4.13.2")
    implementation(project(":engine-source"))
    implementation(project(":apk-runtime"))
    implementation(project(":core"))
}
