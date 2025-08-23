// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    extra.apply {
        set("room_version", "2.6.1")
        set("lifecycle_version", "2.7.0")
        set("nav_version", "2.7.6")
        set("hilt_version", "2.48.1")
        set("retrofit_version", "2.9.0")
        set("okhttp_version", "4.12.0")
        set("coroutines_version", "1.7.3")
        set("work_version", "2.9.0")
    }
}

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.6" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Workaround for Kotlin compiler expecting '.sqlite-tmp' directory in project root
// Ensures the directory exists before any Kotlin compile task executes to avoid
// java.nio.file.NoSuchFileException on Windows environments.
val kotlinCompilerFlagDir = File(rootDir, ".sqlite-tmp")
// Create at configuration time as some Kotlin internals may access it early
if (!kotlinCompilerFlagDir.exists()) {
    kotlinCompilerFlagDir.mkdirs()
}

// Also ensure it's present right before Kotlin compile tasks run (defensive)
tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    doFirst {
        if (!kotlinCompilerFlagDir.exists()) {
            kotlinCompilerFlagDir.mkdirs()
        }
    }
}