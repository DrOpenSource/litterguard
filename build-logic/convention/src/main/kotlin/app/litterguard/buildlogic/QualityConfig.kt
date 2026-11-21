package app.litterguard.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Formatting (Spotless + ktlint + Compose rules) and static analysis (detekt) for every module.
 * Run `./gradlew spotlessApply` to auto-format and `./gradlew detekt` to analyse.
 */
internal fun Project.configureQuality() {
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    val ktlintVersion = libs.version("ktlint")
    val composeRules = "io.nlopez.compose.rules:ktlint:${libs.version("composeRules")}"

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }
}
