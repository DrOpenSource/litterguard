// Business logic: ROI geometry, tracking, dumping state machine, plate sanitizing, and the
// ports (interfaces) that Android modules implement. Pure Kotlin so it is JVM-unit-testable.
plugins {
    alias(libs.plugins.litterguard.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}
