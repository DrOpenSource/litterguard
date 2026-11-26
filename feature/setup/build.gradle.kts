plugins {
    alias(libs.plugins.litterguard.android.feature)
}

android {
    namespace = "app.litterguard.feature.setup"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
