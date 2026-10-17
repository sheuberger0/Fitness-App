// Top-level Build-Datei: Hier werden nur die Plugins deklariert,
// angewendet werden sie im Modul app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
