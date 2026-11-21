package app.litterguard.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

private val javaVersion = JavaVersion.VERSION_17

internal fun Project.configureAndroidApplication(extension: ApplicationExtension) {
    extension.apply {
        compileSdk = libs.intVersion("compileSdk")
        compileSdkMinor = libs.intVersion("compileSdkMinor")
        defaultConfig {
            minSdk = libs.intVersion("minSdk")
            targetSdk = libs.intVersion("targetSdk")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        lint {
            abortOnError = true
            checkDependencies = true
        }
        testOptions.unitTests.isReturnDefaultValues = true
    }
}

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) {
    extension.apply {
        compileSdk = libs.intVersion("compileSdk")
        compileSdkMinor = libs.intVersion("compileSdkMinor")
        defaultConfig {
            minSdk = libs.intVersion("minSdk")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            consumerProguardFiles("consumer-rules.pro")
        }
        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        lint {
            abortOnError = true
        }
        testOptions.unitTests.isReturnDefaultValues = true
    }
}
