pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Fonesi"
include(":app", ":core", ":apk-runtime")

// Source runtime is built separately until its complete provider passes validation.
val nativeRuntimeProbe = providers.gradleProperty("includeNativeRuntimeProbe").orNull == "true"
if (providers.gradleProperty("includeSourceEngine").orNull == "true" || nativeRuntimeProbe) include(":engine-source")
if (nativeRuntimeProbe) include(":native-runtime-probe")

// Engine experiments are opt-in; normal builds do not download or load a binary SDK.
if (providers.gradleProperty("includeEngineProbe").orNull == "true") {
    include(":engine-probe", ":probe-payload")
}
