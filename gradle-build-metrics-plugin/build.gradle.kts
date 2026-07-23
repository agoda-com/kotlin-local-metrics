plugins {
    `java-gradle-plugin`
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${libs.versions.junit.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("buildMetrics") {
            id = "io.agodadev.kotlin-local-metrics.build"
            implementationClass = "io.agodadev.localmetrics.gradle.BuildMetricsPlugin"
            displayName = "Kotlin Local Build Metrics"
            description = project.description
        }
    }
}
