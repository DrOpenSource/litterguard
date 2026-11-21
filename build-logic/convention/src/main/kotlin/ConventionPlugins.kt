import app.litterguard.buildlogic.configureAndroidApplication
import app.litterguard.buildlogic.configureAndroidLibrary
import app.litterguard.buildlogic.configureKotlinCompiler
import app.litterguard.buildlogic.configureQuality
import app.litterguard.buildlogic.configureUnitTests
import app.litterguard.buildlogic.lib
import app.litterguard.buildlogic.libs
import app.litterguard.buildlogic.version
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType

/** `:app` — the single Android application module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> { configureAndroidApplication(this) }
        configureKotlinCompiler()
        configureUnitTests()
        configureQuality()
    }
}

/** Android infrastructure modules under `:core:*`. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> { configureAndroidLibrary(this) }
        configureKotlinCompiler()
        configureUnitTests()
        configureQuality()
    }
}

/** Pure Kotlin modules (`:core:model`, `:core:domain`). No Android APIs allowed. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        // Lets Android Lint analyse pure-Kotlin modules too (the domain logic lives here).
        pluginManager.apply("com.android.lint")
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(libs.version("jvmTarget")))
        }
        configureKotlinCompiler()
        configureUnitTests()
        configureQuality()
    }
}

/** Enables Jetpack Compose on an application or library module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType<ApplicationExtension>()?.buildFeatures?.compose = true
        extensions.findByType<LibraryExtension>()?.buildFeatures?.compose = true
        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            add("implementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-graphics"))
            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        }
    }
}

/** Hilt + KSP for Android modules that provide or inject dependencies. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}

/** `:feature:*` — a Compose screen module wired with Hilt. Features never depend on each other. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("litterguard.android.library")
        pluginManager.apply("litterguard.android.compose")
        pluginManager.apply("litterguard.hilt")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
        }
    }
}
