plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-parcelize")
}

android {
    namespace = "com.aireceptionist.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aireceptionist.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Room schema export directory (configured in top-level kapt block below)
        
        buildConfigField("String", "API_BASE_URL", "\"https://api.aireceptionist.com\"")
        buildConfigField("boolean", "DEBUG_MODE", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG_MODE", "false")
            // Ensure native libraries are stripped in release builds
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // Keep symbols in debug for better debugging experience
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
        dataBinding = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Ensure AGP attempts to strip JNI libraries by default
            keepDebugSymbols.clear()
            useLegacyPackaging = false
        }
    }
}

// Ensure Kotlin and AGP use a JDK 17 toolchain that includes jlink
kotlin {
    jvmToolchain(17)
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Lifecycle and ViewModel
    val lifecycleVersion = rootProject.extra["lifecycle_version"]
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")
    
    // Navigation
    val navVersion = rootProject.extra["nav_version"]
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")
    
    // Room Database
    val roomVersion = rootProject.extra["room_version"]
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    
    // Dependency Injection - Hilt
    val hiltVersion = rootProject.extra["hilt_version"]
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    kapt("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    
    // Network - Retrofit & OkHttp
    val retrofitVersion = rootProject.extra["retrofit_version"]
    val okhttpVersion = rootProject.extra["okhttp_version"]
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    
    // Coroutines
    val coroutinesVersion = rootProject.extra["coroutines_version"]
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    
    // Work Manager
    val workVersion = rootProject.extra["work_version"]
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    
    // JSON Processing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Permissions
    implementation("pub.devrel:easypermissions:3.0.0")
    
    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    
    // Audio Processing - Using standard Android MediaRecorder and AudioManager
    // implementation("com.arthenica:mobile-ffmpeg-audio:4.4.LTS") // Commented out - using native Android audio
    
    // On-Device LLM - ONNX Runtime for Phi-3.5-mini model (updated to latest)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    
    // TensorFlow Lite for additional ML tasks (use versions available on Maven Central)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    
    // ONNX Runtime Extensions for tokenization and text processing
    // Note: Updated to use the correct Maven Central coordinates and latest version
    implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:0.12.4")
    
    // Google ML Kit - Updated to latest version
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    // ML Kit Language Identification used by SpeechRecognitionAgent
    implementation("com.google.mlkit:language-id:17.0.6")
    // Speech recognition will be handled by SpeechRecognizer (built into Android)
    // Face detection commented out for now
    // implementation("com.google.mlkit:face-detection:16.1.5")
    
    // Firebase (Optional for cloud features) - Commented out for initial build
    // implementation(platform("com.google.firebase:firebase-bom:32.4.0"))
    // implementation("com.google.firebase:firebase-analytics-ktx")
    // implementation("com.google.firebase:firebase-firestore-ktx")
    // implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("com.google.firebase:firebase-functions-ktx")
    
    // WebRTC for VoIP (commented out due to compatibility issues)
    // implementation("org.webrtc:google-webrtc:1.0.32006")
    
    // JWT for API authentication - Using available version
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // Event Bus
    implementation("org.greenrobot:eventbus:3.3.1")
    
    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

// --- Alias tasks to avoid ambiguity when tools expect ':app:compileJava' ---
// These map to the Android variant-specific Java compile tasks.
tasks.register("compileJava") {
    // Default to Debug variant; adjust if your CI expects Release
    dependsOn("compileDebugJavaWithJavac")
}

tasks.register("compileReleaseJava") {
    dependsOn("compileReleaseJavaWithJavac")
}


// Top-level Kapt configuration for Room
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

// Ensure sqlite-jdbc used by Room verifier writes to a writable temp dir (Windows-safe)
val sqliteTmpDir = File(buildDir, "tmp/sqlite")

tasks.register("prepareSqliteTmpDir") {
    doLast {
        if (!sqliteTmpDir.exists()) {
            sqliteTmpDir.mkdirs()
        }
    }
}

// Pass JVM args to Kapt worker so sqlite-jdbc uses our temp dir
@Suppress("UnstableApiUsage")
tasks.withType(org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask::class.java).configureEach {
    dependsOn("prepareSqliteTmpDir")
    kaptProcessJvmArgs.add("-Dorg.sqlite.tmpdir=${sqliteTmpDir.absolutePath}")
    kaptProcessJvmArgs.add("-Djava.io.tmpdir=${sqliteTmpDir.absolutePath}")
}

// Workaround for Kotlin compiler expecting '.sqlite-tmp' under the module directory
val moduleKotlinCompilerFlagDir = File(projectDir, ".sqlite-tmp")
// Create at configuration time to avoid early access failures by Kotlin
if (!moduleKotlinCompilerFlagDir.exists()) {
    moduleKotlinCompilerFlagDir.mkdirs()
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    doFirst {
        if (!moduleKotlinCompilerFlagDir.exists()) {
            moduleKotlinCompilerFlagDir.mkdirs()
        }
    }
}
