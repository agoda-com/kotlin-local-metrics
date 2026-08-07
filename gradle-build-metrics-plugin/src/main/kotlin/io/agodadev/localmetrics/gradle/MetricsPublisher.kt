package io.agodadev.localmetrics.gradle

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object MetricsPublisher {
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kotlin-local-build-metrics-http").apply {
            isDaemon = true
        }
    }

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .executor(executor)
        .build()

    /**
     * Sends [json] and, when [buffer] is present, either buffers it for a later build (the
     * endpoint was unreachable) or flushes previously buffered payloads (the send succeeded).
     *
     * Returns immediately: the returned future exists for tests, callers on the build's
     * critical path must not wait on it.
     */
    fun post(
        endpoint: String,
        payloadId: String,
        json: String,
        timeoutMillis: Int,
        buffer: UnsentMetricsBuffer? = null,
    ): CompletableFuture<Unit> = runCatching {
        send(endpoint, json, timeoutMillis).thenCompose { outcome ->
            when (outcome) {
                SendOutcome.SUCCEEDED -> flush(endpoint, timeoutMillis, buffer)
                SendOutcome.UNREACHABLE -> {
                    buffer?.save(payloadId, json)
                    completed()
                }
                SendOutcome.REJECTED -> completed()
            }
        }
    }.getOrElse { completed() }

    private fun send(
        endpoint: String,
        json: String,
        timeoutMillis: Int,
    ): CompletableFuture<SendOutcome> {
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofMillis(timeoutMillis.toLong()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .orTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .handle { response, failure ->
                when {
                    failure != null -> if (failure.isConnectivityFailure()) {
                        SendOutcome.UNREACHABLE
                    } else {
                        SendOutcome.REJECTED
                    }
                    response.statusCode() in 200..299 -> SendOutcome.SUCCEEDED
                    else -> SendOutcome.REJECTED
                }
            }
    }

    /**
     * Drains the buffer one payload at a time, stopping as soon as the endpoint goes
     * unreachable again so a flaky connection cannot turn into a burst of doomed requests.
     */
    private fun flush(
        endpoint: String,
        timeoutMillis: Int,
        buffer: UnsentMetricsBuffer?,
    ): CompletableFuture<Unit> {
        if (buffer == null) {
            return completed()
        }

        return buffer.list()
            .fold(CompletableFuture.completedFuture(true)) { chain, entry ->
                chain.thenCompose { reachable ->
                    val json = if (reachable) buffer.read(entry) else null
                    when {
                        !reachable -> CompletableFuture.completedFuture(false)
                        // An unreadable entry can never be sent; drop it rather than retry forever.
                        json == null -> {
                            buffer.delete(entry)
                            CompletableFuture.completedFuture(true)
                        }
                        else -> send(endpoint, json, timeoutMillis).thenApply { outcome ->
                            if (outcome != SendOutcome.UNREACHABLE) {
                                // Sent, or refused by a reachable endpoint — either way it is done.
                                buffer.delete(entry)
                            }
                            outcome != SendOutcome.UNREACHABLE
                        }
                    }
                }
            }
            .thenApply { }
    }

    private fun completed(): CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)

    private fun Throwable.isConnectivityFailure(): Boolean {
        var failure: Throwable? = this
        val seen = mutableSetOf<Throwable>()
        while (failure != null && seen.add(failure)) {
            if (failure is IOException || failure is TimeoutException) {
                return true
            }
            failure = failure.cause
        }
        return false
    }

    private enum class SendOutcome {
        /** The endpoint accepted the payload. */
        SUCCEEDED,

        /** The endpoint could not be reached; the payload is worth retrying later. */
        UNREACHABLE,

        /** The endpoint was reached but did not accept the payload; retrying will not help. */
        REJECTED,
    }
}
