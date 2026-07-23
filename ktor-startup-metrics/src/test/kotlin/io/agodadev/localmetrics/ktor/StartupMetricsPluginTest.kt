package io.agodadev.localmetrics.ktor

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupMetricsPluginTest {
    @Test
    fun `publishes startup and only the first response from the JVM baseline`() {
        val requestBodies = CopyOnWriteArrayList<String>()
        val requestsReceived = CountDownLatch(2)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ktor") { exchange ->
            requestBodies += exchange.requestBody.bufferedReader().use { it.readText() }
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            requestsReceived.countDown()
        }
        server.start()

        try {
            testApplication {
                application {
                    install(StartupMetrics) {
                        endpoint = "http://127.0.0.1:${server.address.port}/ktor"
                        projectName = "ktor-metrics-fixture"
                        metricsVersion = "test"
                    }
                    routing {
                        get("/") {
                            call.respondText("ok")
                        }
                    }
                }

                client.get("/")
                client.get("/")
            }

            assertTrue(
                requestsReceived.await(5, TimeUnit.SECONDS),
                "startup metrics POSTs were not received",
            )
            assertEquals(2, requestBodies.size)
            assertEquals(
                setOf(KTOR_STARTUP_TYPE, KTOR_RESPONSE_TYPE),
                requestBodies.mapNotNull(::metricType).toSet(),
            )
            requestBodies.forEach { json ->
                assertTrue(
                    Regex(""""timeTaken":"([1-9][0-9]*|0)"""").containsMatchIn(json),
                    "timeTaken should be a non-negative JVM-relative duration: $json",
                )
            }
        } finally {
            server.stop(0)
        }
    }

    private fun metricType(json: String): String? =
        Regex(""""type":"([^"]+)"""").find(json)?.groupValues?.get(1)
}
