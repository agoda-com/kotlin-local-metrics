package io.agodadev.localmetrics.gradle

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class MetricsPublisherTest {
    @TempDir
    lateinit var bufferDirectory: Path

    @Test
    fun `buffers the payload when the endpoint is unreachable`() {
        val buffer = buffer()

        post(unreachableEndpoint(), "payload-1", """{"id":"payload-1"}""", buffer)

        assertEquals(listOf("payload-1.json"), buffer.list().map(File::getName))
        assertEquals("""{"id":"payload-1"}""", buffer.read(buffer.list().single()))
    }

    @Test
    fun `keeps earlier payloads buffered while the endpoint stays unreachable`() {
        val buffer = buffer()
        buffer.save("payload-1", """{"id":"payload-1"}""")

        post(unreachableEndpoint(), "payload-2", """{"id":"payload-2"}""", buffer)

        assertEquals(
            setOf("payload-1.json", "payload-2.json"),
            buffer.list().map(File::getName).toSet(),
        )
    }

    @Test
    fun `flushes and deletes buffered payloads once a send succeeds`() {
        val buffer = buffer()
        buffer.save("payload-1", """{"id":"payload-1"}""")
        buffer.save("payload-2", """{"id":"payload-2"}""")

        val received = withEndpoint(status = 204) { endpoint ->
            post(endpoint, "payload-3", """{"id":"payload-3"}""", buffer)
        }

        assertEquals(
            setOf("""{"id":"payload-1"}""", """{"id":"payload-2"}""", """{"id":"payload-3"}"""),
            received.toSet(),
        )
        assertTrue(buffer.list().isEmpty(), "buffer should be drained after a successful send")
    }

    @Test
    fun `does not buffer or flush when a reachable endpoint rejects the payload`() {
        val buffer = buffer()
        buffer.save("payload-1", """{"id":"payload-1"}""")

        val received = withEndpoint(status = 500) { endpoint ->
            post(endpoint, "payload-2", """{"id":"payload-2"}""", buffer)
        }

        assertEquals(listOf("""{"id":"payload-2"}"""), received)
        assertEquals(listOf("payload-1.json"), buffer.list().map(File::getName))
    }

    @Test
    fun `sends without a buffer when none is configured`() {
        val received = withEndpoint(status = 204) { endpoint ->
            post(endpoint, "payload-1", """{"id":"payload-1"}""", buffer = null)
        }

        assertEquals(listOf("""{"id":"payload-1"}"""), received)
    }

    @Test
    fun `never throws on a malformed endpoint`() {
        val buffer = buffer()

        post("not a url", "payload-1", """{"id":"payload-1"}""", buffer)

        assertTrue(buffer.list().isEmpty())
    }

    private fun post(endpoint: String, payloadId: String, json: String, buffer: UnsentMetricsBuffer?) {
        MetricsPublisher.post(
            endpoint = endpoint,
            payloadId = payloadId,
            json = json,
            timeoutMillis = TIMEOUT_MILLIS,
            buffer = buffer,
        ).join()
    }

    /** Runs [block] against a local endpoint returning [status], and returns the bodies it received. */
    private fun withEndpoint(status: Int, block: (String) -> Unit): List<String> {
        val bodies = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/gradle") { exchange ->
            bodies.add(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()

        try {
            block("http://127.0.0.1:${server.address.port}/gradle")
        } finally {
            server.stop(0)
        }

        return bodies.toList()
    }

    /** A loopback port nothing is listening on, so a connect fails immediately and deterministically. */
    private fun unreachableEndpoint(): String {
        val port = ServerSocket(0).use { it.localPort }
        return "http://127.0.0.1:$port/gradle"
    }

    private fun buffer(): UnsentMetricsBuffer = UnsentMetricsBuffer(bufferDirectory.toFile())

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
    }
}
