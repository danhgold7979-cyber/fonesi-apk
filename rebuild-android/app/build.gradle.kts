plugins { id("com.android.application") }
android {
    namespace = "vn.fonesi.rebuild"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "vn.fonesi.rebuild.debug"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-scaffold"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true; warningsAsErrors = true }
}
