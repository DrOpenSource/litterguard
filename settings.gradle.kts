pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LitterGuard"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Pure-Kotlin (JVM) modules: all business logic lives here and is unit-tested on the JVM.
include(":core:model")
include(":core:domain")

// Android infrastructure modules: adapters that implement domain ports.
include(":core:camera")
include(":core:ml")
include(":core:data")
include(":core:alerts")
include(":core:monitoring")
include(":core:ui")

// Feature (screen) modules. Features never depend on each other.
include(":feature:setup")
include(":feature:monitor")
include(":feature:incidents")
