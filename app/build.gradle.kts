plugins {
    alias(libs.plugins.litterguard.android.application)
    alias(libs.plugins.litterguard.android.compose)
    alias(libs.plugins.litterguard.hilt)
}

android {
    namespace = "app.litterguard"

    defaultConfig {
        applicationId = "app.litterguard"
        versionCode = 1
        versionName = "0.1.0"
        // The reference phone and every realistic target are 64-bit ARM; other ABIs only bloat the APK.
        ndk { abiFilters += "arm64-v8a" }
    }

    // LiteRT memory-maps models straight from the APK, which needs them stored uncompressed.
    androidResources { noCompress += "tflite" }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.camera)
    implementation(projects.core.ml)
    implementation(projects.core.data)
    implementation(projects.core.alerts)
    implementation(projects.core.monitoring)
    implementation(projects.core.ui)
    implementation(projects.feature.setup)
    implementation(projects.feature.monitor)
    implementation(projects.feature.incidents)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.timber)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
