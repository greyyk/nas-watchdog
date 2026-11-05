plugins {
    // AGP 8.13.x officially supports compileSdk 36 without early AGP 9 migration.
    // AGP 9 requires Gradle 9.6+, so this stays on the latest 8.x line while Gradle
    // 9.8 satisfies AGP's Gradle internal API dependency.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
