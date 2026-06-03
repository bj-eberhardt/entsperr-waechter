import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull ?: System.getenv(name)

val releaseStoreFile = signingProperty("UC_STORE_FILE")
val releaseStorePassword = signingProperty("UC_STORE_PASSWORD")
val releaseKeyAlias = signingProperty("UC_KEY_ALIAS")
val releaseKeyPassword = signingProperty("UC_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

gradle.taskGraph.whenReady {
    val requestedReleaseTask = allTasks.any { task ->
        task.path.startsWith(":app:") && task.name.contains("Release", ignoreCase = true)
    }
    if (requestedReleaseTask && !hasReleaseSigning) {
        throw GradleException(
            "Missing release signing configuration. Set UC_STORE_FILE, UC_STORE_PASSWORD, UC_KEY_ALIAS and UC_KEY_PASSWORD via environment variables or Gradle properties."
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
