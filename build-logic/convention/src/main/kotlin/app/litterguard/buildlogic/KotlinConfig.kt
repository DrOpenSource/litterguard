package app.litterguard.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared Kotlin compiler settings for every module (JVM and Android).
 * Warnings become errors when `-PwarningsAsErrors=true` is passed (CI does this).
 */
internal fun Project.configureKotlinCompiler() {
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

/** JUnit Jupiter for all local (JVM) unit tests. Instrumented tests use AndroidJUnit4. */
internal fun Project.configureUnitTests() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed") }
    }
    dependencies {
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
}
