plugins { id("com.android.application") }
android {
    namespace = "vn.fonesi.nativeprobe"
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
    implementation(project(":engine-source"))
    implementation(project(":apk-runtime"))
    implementation(project(":core"))
}
