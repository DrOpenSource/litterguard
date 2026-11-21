// Runtime engine: foreground service, pipeline orchestration (frames -> domain -> effects),
// thermal watchdog and wake-lock management. Talks to other modules only via domain ports.
plugins {
    alias(libs.plugins.litterguard.android.library)
    alias(libs.plugins.litterguard.hilt)
}

android {
    namespace = "app.litterguard.core.monitoring"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
