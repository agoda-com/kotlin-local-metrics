package io.agodadev.localmetrics.gradle

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    fun post(endpoint: String, json: String, timeoutMillis: Int) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMillis.toLong()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .orTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .exceptionally { null }
        }
    }
}
