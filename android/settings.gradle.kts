pluginManagement {
    // build-logic supplies the convention plugins consumed by every module.
    // Enabled in T0.2 once build-logic exists.
    // includeBuild("build-logic")
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

rootProject.name = "invoice-wallet-android"

// Type-safe project accessors (e.g. projects.core.model) instead of stringly-typed paths.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// --- Modules (uncommented as they are created) ---
// T0.3
// include(":app")
// T0.4
// include(":core:testing")
// Iteration 1
// include(":core:model")
// include(":core:database")
