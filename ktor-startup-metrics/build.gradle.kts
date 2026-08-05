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
            // Ktor 3's bytecode references stdlib internals added after 2.0 (e.g.
            // kotlin.coroutines.jvm.internal.SpillingKt), so Ktor 3 test runs need a
            // newer stdlib than the 2.0.21 pinned for publication. Ktor 3 consumers
            // are on Kotlin 2.x anyway; this only affects the test classpath.
            if (ktorVersionUnderTest.get().substringBefore(".").toInt() >= 3 &&
                requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")
            ) {
                useVersion("2.1.21")
                because("Ktor 3 requires newer kotlin-stdlib internals")
            }
        }
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}
