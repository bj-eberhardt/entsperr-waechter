import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.eberhardt.unlockcapture"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.eberhardt.unlockcapture"
        minSdk = 21
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeUi = "1.9.5"
    val composeMaterial3 = "1.3.2"

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUi")
    implementation("androidx.compose.material3:material3:$composeMaterial3")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

}
