plugins { id("com.android.library") }
val toolsRoot = providers.environmentVariable("FONESI_TOOLS_DIR").orElse("/workspace/.fonesi-tools")
val verifyNativeInputs by tasks.registering(Exec::class) {
    commandLine("python3", rootProject.file("scripts/prepare_source_engine.py"))
}
val generatedLicenses = layout.buildDirectory.dir("generated/native-licenses")
val stageNativeLicenses by tasks.registering {
    dependsOn(verifyNativeInputs)
    val dependency = file("${toolsRoot.get()}/source-engine/dobby-5dfc8546954ce3b3198132ab13fddb89ee92cdd7")
    inputs.files(dependency.resolve("LICENSE"), dependency.resolve("external/TINYSTL/vector.h"), rootProject.file("third_party/source-engine/NOTICE.md"))
    outputs.dir(generatedLicenses)
    doLast {
        val directory = generatedLicenses.get().asFile.resolve("licenses")
        check(directory.mkdirs() || directory.isDirectory)
        dependency.resolve("LICENSE").copyTo(directory.resolve("Dobby.txt"), overwrite = true)
        directory.resolve("TINYSTL.txt").writeText(dependency.resolve("external/TINYSTL/vector.h").readText().substringBefore("*/") + "*/\n")
        rootProject.file("third_party/source-engine/NOTICE.md").copyTo(directory.resolve("Fonesi-source-runtime.md"), overwrite = true)
    }
}
android {
    namespace = "vn.fonesi.engine"
    compileSdk = 36
    ndkVersion = "29.0.13846066"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
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
