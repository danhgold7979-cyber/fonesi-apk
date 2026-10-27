import java.util.Properties

plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
val apkFixtures = layout.buildDirectory.dir("apk-audit-fixtures")
val fixtureSdk = providers.environmentVariable("ANDROID_HOME").orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
    .orElse(providers.provider {
        val local = rootProject.file("local.properties")
        val properties = Properties()
        if (local.isFile) local.inputStream().use { properties.load(it) }
        properties.getProperty("sdk.dir") ?: throw GradleException("Set ANDROID_HOME or sdk.dir for APK fixture generation")
    })
val generateApkFixtures by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("scripts/build_apk_audit_fixtures.py"))
    outputs.dir(apkFixtures)
    inputs.property("fixtureSdk", fixtureSdk)
    doFirst {
        commandLine("python3", rootProject.file("scripts/build_apk_audit_fixtures.py"), "--output", apkFixtures.get().asFile,
            "--sdk", fixtureSdk.get(), "--java-home", System.getProperty("java.home"))
    }
}
tasks.test {
    dependsOn(generateApkFixtures)
    inputs.files(fileTree(apkFixtures) { include("*.apk") }).withPropertyName("apkIntegrityFixtures")
    systemProperty("fonesi.apkFixtures", apkFixtures.get().asFile.absolutePath)
    maxHeapSize = "1g"
}
dependencies {
    implementation(project(":core"))
    implementation("com.android.tools.build:apksig:8.11.1")
    testImplementation("junit:junit:4.13.2")
}
