pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-local-metrics"

include(
    "metrics-core",
    "gradle-build-metrics-plugin",
    "ktor-startup-metrics",
    "junit5-test-metrics",
)
