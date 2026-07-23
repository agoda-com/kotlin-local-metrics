package io.agodadev.localmetrics.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

class BuildMetricsPluginFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `settings plugin supports the configuration cache`() {
        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            plugins {
                id("io.agodadev.kotlin-local-metrics.build")
            }

            rootProject.name = "build-metrics-fixture"
            """.trimIndent(),
        )
        Files.createFile(projectDirectory.resolve("build.gradle.kts"))

        val runner = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("help", "--configuration-cache")
            .withPluginClasspath()

        assertContains(runner.build().output, "Configuration cache entry stored.")
        assertContains(runner.build().output, "Configuration cache entry reused.")
    }
}
