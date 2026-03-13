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
        // For RunAnywhere SDK if needed
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Jarvis Voice Assistant"
include(":app")

