plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ─── Release build (#50) ─────────────────────────────────────────────────────
// versionCode = number of commits, so every build from a newer commit installs over an older one.
val gitCommitCount: Int = providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.get().trim().toIntOrNull() ?: 1
val gitDescribe: String = providers.exec { commandLine("git", "describe", "--tags", "--always", "--dirty"); isIgnoreExitValue = true }
    .standardOutput.asText.get().trim().ifEmpty { "dev" }

// Release signing from environment variables (never commit a keystore). Without them the release
// APK is signed with the debug key, so `assembleRelease` still gives an APK that installs.
val releaseKeystore: String? = System.getenv("FARM_KEYSTORE_PATH")?.takeIf { file(it).exists() }

android {
    namespace = "com.farm.delivery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.farm.delivery"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.0.0 ($gitDescribe)"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FARM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FARM_KEY_ALIAS")
                keyPassword = System.getenv("FARM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
