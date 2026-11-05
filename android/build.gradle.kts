plugins {
    // AGP 8.13.x officially supports compileSdk 36 without early AGP 9 migration.
    // Gradle 9.6 removed an internal API AGP 8.x uses, so the wrapper stays on 9.5.x
    // until the AGP 9 / built-in Kotlin migration happens.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
