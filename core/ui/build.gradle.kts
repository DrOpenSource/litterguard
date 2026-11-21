// Design system: theme, typography, shared Compose components (kiosk overlay, ROI canvas).
plugins {
    alias(libs.plugins.litterguard.android.library)
    alias(libs.plugins.litterguard.android.compose)
}

android {
    namespace = "app.litterguard.core.ui"
}

dependencies {
    api(projects.core.model)
}
