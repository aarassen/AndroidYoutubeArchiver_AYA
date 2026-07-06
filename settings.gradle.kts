pluginManagement {
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
        // Chaquopy (embedded Python) Gradle plugin.
        maven { url = uri("https://chaquo.com/maven") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Chaquopy (embedded Python) plugin + runtime artifacts.
        maven { url = uri("https://chaquo.com/maven") }
    }
}

rootProject.name = "YoutubeArchiver"
include(":app")
