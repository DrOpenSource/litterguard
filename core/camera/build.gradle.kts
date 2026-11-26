// CameraX adapter: throttled ImageAnalysis frames, speculative ImageCapture, torch control.
plugins {
    alias(libs.plugins.litterguard.android.library)
    alias(libs.plugins.litterguard.hilt)
}

android {
    namespace = "app.litterguard.core.camera"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
