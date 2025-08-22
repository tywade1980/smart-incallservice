// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    extra.apply {
        set("room_version", "2.5.0")
        set("lifecycle_version", "2.7.0")
        set("nav_version", "2.7.4")
        set("hilt_version", "2.48")
        set("retrofit_version", "2.9.0")
        set("okhttp_version", "4.12.0")
        set("coroutines_version", "1.7.3")
        set("work_version", "2.8.1")
    }
}

plugins {
    id("com.android.application") version "8.1.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.4" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}