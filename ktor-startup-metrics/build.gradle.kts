dependencies {
    implementation(project(":metrics-core"))
    compileOnly(libs.ktor.server.core)

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${libs.versions.junit.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val ktorVersionUnderTest = providers.gradleProperty("ktorVersion")

configurations.configureEach {
    if (ktorVersionUnderTest.isPresent) {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.ktor") {
                useVersion(ktorVersionUnderTest.get())
                because("the startup plugin supports both Ktor 2 and Ktor 3")
            }
        }
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}
