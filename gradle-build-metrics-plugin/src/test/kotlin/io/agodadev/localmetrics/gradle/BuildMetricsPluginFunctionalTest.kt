package io.agodadev.localmetrics.gradle

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
            .withEnvironment(localEnvironment("http://127.0.0.1:9/gradle"))
            .withGradleVersion("8.14.3")
            .withPluginClasspath()

        assertContains(runner.build().output, "Configuration cache entry stored.")
        assertContains(runner.build().output, "Configuration cache entry reused.")
    }

    @Test
    fun `settings plugin publishes local compilation metrics`() {
        val requestBody = AtomicReference<String>()
        val requestReceived = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/gradle") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            requestReceived.countDown()
        }
        server.start()

        try {
            projectDirectory.resolve("settings.gradle.kts").writeText(
                """
                plugins {
                    id("io.agodadev.kotlin-local-metrics.build")
                }

                rootProject.name = "build-metrics-fixture"
                """.trimIndent(),
            )
            projectDirectory.resolve("build.gradle.kts").writeText(
                """
                tasks.register("compileKotlin") {
                    doLast {
                        Thread.sleep(25)
                    }
                }
                """.trimIndent(),
            )

            GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("compileKotlin")
                .withEnvironment(
                    localEnvironment("http://127.0.0.1:${server.address.port}/gradle"),
                )
                .withPluginClasspath()
                .build()

            assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "metrics POST was not received")
            val json = assertNotNull(requestBody.get())
            assertContains(json, "\"type\":\"Gradle\"")
            assertContains(json, "\"compileTaskCount\":1")
            assertContains(json, "\"projectPath\":\":\"")
            assertTrue(
                Regex(""""compileTimeMs":([1-9][0-9]*)""").containsMatchIn(json),
                "compileTimeMs should include executed compilation work: $json",
            )
        } finally {
            server.stop(0)
        }
    }

    private fun localEnvironment(endpoint: String): Map<String, String> =
        System.getenv()
            .filterKeys { it !in setOf("CI", "GITLAB_CI", "CI_JOB_ID") }
            .toMutableMap()
            .apply {
                put("JAVA_HOME", System.getProperty("java.home"))
                put("BUILD_METRICS_ES_ENDPOINT", endpoint)
            }
}
