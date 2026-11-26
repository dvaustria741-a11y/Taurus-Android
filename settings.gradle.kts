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
        maven("https://jitpack.io")
        // XposedBridge stub — compile-only, provided by LSPosed at runtime
    }
}

rootProject.name = "Taurus"
include(":app")
include(":xposed")
