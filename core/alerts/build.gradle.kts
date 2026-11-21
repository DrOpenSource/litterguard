// Outbound effects: siren audio + torch deterrence, Telegram/webhook delivery via WorkManager,
// encrypted secret storage (bot token).
plugins {
    alias(libs.plugins.litterguard.android.library)
    alias(libs.plugins.litterguard.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.litterguard.core.alerts"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(libs.okhttp.mockwebserver)
}
