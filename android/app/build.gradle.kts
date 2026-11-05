import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.grey.naswatchdog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.grey.naswatchdog"
        minSdk = 26
        targetSdk = 36
        // Release CI supplies monotonically increasing code and tag-derived name.
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toIntOrNull() ?: 5
        versionName = providers.gradleProperty("releaseVersionName").orNull ?: "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Local release builds remain unsigned unless signing environment variables
    // are present. Keystore files/passwords are never passed as Gradle text or committed.
    // GitHub APK and Play upload identities are deliberately separate.
    val releaseStoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
    if (releaseStoreFile != null) {
        require(releaseStoreFile.isFile) { "Android release keystore was not found at $releaseStoreFile" }
        signingConfigs.create("release") {
            storeFile = releaseStoreFile
            storePassword = requireNotNull(System.getenv("ANDROID_KEYSTORE_PASSWORD")) {
                "Set ANDROID_KEYSTORE_PASSWORD for release signing"
            }
            keyAlias = requireNotNull(System.getenv("ANDROID_KEY_ALIAS")) {
                "Set ANDROID_KEY_ALIAS for release signing"
            }
            keyPassword = requireNotNull(System.getenv("ANDROID_KEY_PASSWORD")) {
                "Set ANDROID_KEY_PASSWORD for release signing"
            }
        }
    }

    val playStoreFile = System.getenv("ANDROID_PLAY_KEYSTORE_FILE")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
    if (playStoreFile != null) {
        require(playStoreFile.isFile) { "Play upload keystore was not found at $playStoreFile" }
        signingConfigs.create("play") {
            storeFile = playStoreFile
            storePassword = requireNotNull(System.getenv("ANDROID_PLAY_KEYSTORE_PASSWORD")) {
                "Set ANDROID_PLAY_KEYSTORE_PASSWORD for Play upload signing"
            }
            keyAlias = requireNotNull(System.getenv("ANDROID_PLAY_KEY_ALIAS")) {
                "Set ANDROID_PLAY_KEY_ALIAS for Play upload signing"
            }
            keyPassword = requireNotNull(System.getenv("ANDROID_PLAY_KEY_PASSWORD")) {
                "Set ANDROID_PLAY_KEY_PASSWORD for Play upload signing"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("play") {
            initWith(getByName("release"))
            // Never silently inherit the GitHub signing key into a Play upload.
            // Without its own key this intentionally produces an unsigned bundle.
            signingConfig = if (playStoreFile != null) signingConfigs.getByName("play") else null
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
