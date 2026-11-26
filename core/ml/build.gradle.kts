// On-device ML adapters: LiteRT object/plate detectors and ML Kit text recognition.
import app.litterguard.buildlogic.FetchModelsTask
import app.litterguard.buildlogic.registerFetchModels

plugins {
    alias(libs.plugins.litterguard.android.library)
    alias(libs.plugins.litterguard.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.litterguard.core.ml"
}

// Models are never committed: fetch them, verify sha256 against models.json and package them as assets (ADR-0004).
val fetchModels = registerFetchModels()

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchModels, FetchModelsTask::outputDir)
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.litert)
    // Required for Accelerator.GPU; without it GPU requests silently run on the CPU (ADR-0004).
    implementation(libs.litert.gpu)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    // On-device golden test and latency benchmark; extra images and models are supplied locally, never committed.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
