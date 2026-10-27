plugins { id("com.android.library"); kotlin("android") }
val toolsRoot = providers.environmentVariable("FONESI_TOOLS_DIR").orElse("/workspace/.fonesi-tools")
val sourceAbi = providers.gradleProperty("sourceEngineAbi").orElse("arm64-v8a").get()
require(sourceAbi in setOf("arm64-v8a", "x86_64")) { "Only explicitly supported source runtime build ABIs are allowed" }
val verifyNativeInputs by tasks.registering(Exec::class) {
    commandLine("python3", rootProject.file("scripts/prepare_source_engine.py"))
}
val generatedLicenses = layout.buildDirectory.dir("generated/native-licenses")
val stageNativeLicenses by tasks.registering {
    dependsOn(verifyNativeInputs)
    val dependency = file("${toolsRoot.get()}/source-engine/dobby-5dfc8546954ce3b3198132ab13fddb89ee92cdd7")
    inputs.files(dependency.resolve("LICENSE"), dependency.resolve("external/TINYSTL/vector.h"), rootProject.file("third_party/source-engine/NOTICE.md"), rootProject.file("third_party/apksig/NOTICE.md"))
    outputs.dir(generatedLicenses)
    doLast {
        val directory = generatedLicenses.get().asFile.resolve("licenses")
        check(directory.mkdirs() || directory.isDirectory)
        dependency.resolve("LICENSE").copyTo(directory.resolve("Dobby.txt"), overwrite = true)
        directory.resolve("TINYSTL.txt").writeText(dependency.resolve("external/TINYSTL/vector.h").readText().substringBefore("*/") + "*/\n")
        rootProject.file("third_party/source-engine/NOTICE.md").copyTo(directory.resolve("Fonesi-source-runtime.md"), overwrite = true)
        rootProject.file("third_party/apksig/NOTICE.md").copyTo(directory.resolve("apksig.md"), overwrite = true)
    }
}
android {
    namespace = "vn.fonesi.engine"
    compileSdk = 36
    ndkVersion = "29.0.13846066"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += sourceAbi }
        externalNativeBuild.cmake {
            arguments += "-DDOBBY_ROOT=${toolsRoot.get()}/source-engine/dobby-5dfc8546954ce3b3198132ab13fddb89ee92cdd7"
            arguments += "-DANDROID_STL=c++_static"
            targets += "fonesi_runtime"
        }
    }
    externalNativeBuild.cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    sourceSets.getByName("main").assets.srcDir(generatedLicenses)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
tasks.named("preBuild") { dependsOn(stageNativeLicenses) }
kotlin { jvmToolchain(21) }
val nativeFixtures = layout.buildDirectory.dir("native-apk-fixtures")
val generateNativeFixtures by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("scripts/build_native_apk_fixtures.py"), rootProject.file("scripts/build_apk_audit_fixtures.py"), rootProject.file("scripts/prepare_source_engine.py"))
    outputs.dir(nativeFixtures)
    commandLine("python3", rootProject.file("scripts/build_native_apk_fixtures.py"), "--output", nativeFixtures.get().asFile,
        "--sdk", "${toolsRoot.get()}/android-sdk", "--java-home", System.getProperty("java.home"))
}
tasks.withType<Test>().configureEach {
    dependsOn(generateNativeFixtures)
    inputs.files(fileTree(nativeFixtures) { include("*.apk") }).withPropertyName("nativeApkFixtures")
    systemProperty("fonesi.nativeFixtures", nativeFixtures.get().asFile.absolutePath)
}
dependencies {
    implementation(project(":apk-runtime"))
    implementation(project(":core"))
    testImplementation("com.android.tools.build:apksig:8.11.1")
    testImplementation("junit:junit:4.13.2")
}
