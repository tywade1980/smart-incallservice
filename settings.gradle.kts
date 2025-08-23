pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Enables Gradle to automatically resolve and download Java toolchains (e.g., JDK 17)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
    }
}

// Ensure Kotlin compiler expected temp dir exists as early as possible
val kotlinCompilerFlagDirAtSettings = File(settingsDir, ".sqlite-tmp")
if (!kotlinCompilerFlagDirAtSettings.exists()) {
    kotlinCompilerFlagDirAtSettings.mkdirs()
}

rootProject.name = "AI Receptionist"
include(":app")