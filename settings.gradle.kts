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
include(":app", ":core")

// Source runtime is built separately until its complete provider passes validation.
if (providers.gradleProperty("includeSourceEngine").orNull == "true") include(":engine-source")

// Engine experiments are opt-in; normal builds do not download or load a binary SDK.
if (providers.gradleProperty("includeEngineProbe").orNull == "true") {
    include(":engine-probe", ":probe-payload")
}
