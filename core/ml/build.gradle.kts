// On-device ML adapters: LiteRT object/plate detectors and ML Kit text recognition.
plugins {
    alias(libs.plugins.litterguard.android.library)
    alias(libs.plugins.litterguard.hilt)
}

android {
    namespace = "app.litterguard.core.ml"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.litert)
    // Required for Accelerator.GPU; without it GPU requests silently run on the CPU (ADR-0004).
    implementation(libs.litert.gpu)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
