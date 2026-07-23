package io.agodadev.localmetrics.ktor

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object StartupMetricsPublisher {
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kotlin-local-ktor-metrics-http").apply {
            isDaemon = true
        }
    }

    fun post(
        endpoint: String,
        timeoutMillis: Long,
        metricsVersion: String,
        type: String,
        projectName: String,
        durationMillis: Long,
        workingDirectory: File,
    ) {
        runCatching {
            executor.execute {
                runCatching {
                    val payload = StartupMetricsPayloadFactory.create(
                        metricsVersion = metricsVersion,
                        type = type,
                        projectName = projectName,
                        durationMillis = durationMillis,
                        gitContext = GitContextReader.read(workingDirectory),
                    )
                    val timeout = Duration.ofMillis(timeoutMillis)
                    val client = HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .executor(executor)
                        .build()
                    val request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                StartupMetricsJson.encode(payload),
                            ),
                        )
                        .build()

                    client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                        .exceptionally { null }
                }
            }
        }
    }
}
