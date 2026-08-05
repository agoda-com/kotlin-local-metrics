import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

val releaseVersion = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT")
val moduleDescriptions = mapOf(
    "metrics-core" to "Shared payload models and metrics transport.",
    "gradle-build-metrics-plugin" to "A Gradle settings plugin for local build metrics.",
    "ktor-startup-metrics" to "A Ktor application plugin for local startup metrics.",
)

allprojects {
    group = "io.agodadev"
    version = releaseVersion.get()
}

subprojects {
    description = moduleDescriptions.getValue(name)

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        withJavadocJar()
        withSourcesJar()
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        javaLauncher.set(
            project.extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(17))
            },
        )
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    extensions.configure<PublishingExtension> {
        if (project.name != "gradle-build-metrics-plugin") {
            publications.create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }

        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/agoda-com/kotlin-local-metrics")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("dicko2")
                        name.set("Joel Dickson")
                        email.set("maven@agoda.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/agoda-com/kotlin-local-metrics.git")
                    developerConnection.set("scm:git:ssh://github.com/agoda-com/kotlin-local-metrics.git")
                    url.set("https://github.com/agoda-com/kotlin-local-metrics/tree/main")
                }
            }
        }

        repositories {
            maven {
                name = "staging"
                url = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
            }
        }
    }
}
