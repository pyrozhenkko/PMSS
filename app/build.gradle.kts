import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp)     // Lab 5: Room code generation (replaces kapt)
}

// Lab 4: the Google Services plugin is applied only when google-services.json is present in app/.
// Without the file (local Firebase emulator) the app initializes Firebase manually.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Lab 3: the database URL is read from local.properties (the file is not committed to git)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val firebaseDbUrl: String = localProperties.getProperty("FIREBASE_DB_URL", "")
val firebaseDbNs: String = localProperties.getProperty("FIREBASE_DB_NS", "")

android {
    namespace = "com.example.lab1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.lab1"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_DB_URL", "\"$firebaseDbUrl\"")
        buildConfigField("String", "FIREBASE_DB_NS", "\"$firebaseDbNs\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // Lab 6: without compression the model file can be memory-mapped
        noCompress += "tflite"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mpandroidchart)
    // Lab 6: TensorFlow Lite (now LiteRT, the org.tensorflow.lite.Interpreter API is the same).
    // The version is pinned instead of "+" to keep the build reproducible; the 1.x branch, because
    // LiteRT 2.x is built with Kotlin 2.4 and is incompatible with Kotlin 2.2.10 of this project.
    implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}