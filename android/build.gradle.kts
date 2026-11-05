plugins {
    // AGP 8.13.x officially supports compileSdk 36 without early AGP 9 migration.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
