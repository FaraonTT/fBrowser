// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
// AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android is intentionally NOT applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
