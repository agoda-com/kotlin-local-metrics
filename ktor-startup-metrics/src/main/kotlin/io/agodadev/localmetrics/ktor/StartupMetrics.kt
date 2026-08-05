package io.agodadev.localmetrics.ktor

import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.server.application.hooks.ResponseSent
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records local Ktor startup time and time until the first completed response.
 *
 * The plugin is enabled only in Ktor development mode unless [StartupMetricsConfiguration.enabled]
 * explicitly overrides that behavior.
 */
public val StartupMetrics = createApplicationPlugin(
    name = "StartupMetrics",
    createConfiguration = ::StartupMetricsConfiguration,
) {
    val enabled = pluginConfig.enabled
        ?: (application.developmentMode ||
            System.getProperty(DEVELOPMENT_MODE_PROPERTY).toBoolean())

    if (enabled) {
        val endpoint = pluginConfig.endpoint
        val timeoutMillis = pluginConfig.timeoutMillis
        val metricsVersion = pluginConfig.metricsVersion ?: startupMetricsVersion()
        val projectName = pluginConfig.projectName ?: defaultProjectName()
        val workingDirectory = pluginConfig.workingDirectory
        val jvmStartedAtMillis = ManagementFactory.getRuntimeMXBean().startTime
        val firstResponseRecorded = AtomicBoolean()

        fun publish(type: String) {
            StartupMetricsPublisher.post(
                endpoint = endpoint,
                timeoutMillis = timeoutMillis,
                metricsVersion = metricsVersion,
                type = type,
                projectName = projectName,
                durationMillis = (System.currentTimeMillis() - jvmStartedAtMillis)
                    .coerceAtLeast(0),
                workingDirectory = workingDirectory,
            )
        }

        on(MonitoringEvent(ApplicationStarted)) {
            publish(KTOR_STARTUP_TYPE)
        }

        on(ResponseSent) {
            if (firstResponseRecorded.compareAndSet(false, true)) {
                publish(KTOR_RESPONSE_TYPE)
            }
        }
    }
}

/**
 * Configuration for [StartupMetrics].
 */
public class StartupMetricsConfiguration {
    /**
     * Enables or disables collection. `null` enables collection only in Ktor development mode.
     */
    public var enabled: Boolean? = null

    /**
     * Destination for both startup payloads.
     */
    public var endpoint: String =
        System.getenv(KTOR_ENDPOINT_ENVIRONMENT_VARIABLE)
            ?: System.getenv(BUILD_ENDPOINT_ENVIRONMENT_VARIABLE)
            ?: DEFAULT_ENDPOINT

    /**
     * Maximum HTTP connection and request duration.
     */
    public var timeoutMillis: Long = DEFAULT_HTTP_TIMEOUT_MILLIS

    /**
     * Application name stored in the metrics payload.
     */
    public var projectName: String? = null

    /**
     * Library version stored in the metrics payload.
     */
    public var metricsVersion: String? = null

    /**
     * Directory used to discover repository metadata.
     */
    public var workingDirectory: File = File(System.getProperty("user.dir"))
}

private fun startupMetricsVersion(): String =
    StartupMetricsConfiguration::class.java.`package`.implementationVersion ?: "development"

private fun defaultProjectName(): String =
    System.getProperty("sun.java.command")
        ?.substringBefore(' ')
        ?.substringAfterLast('.')
        ?.removeSuffix("Kt")
        ?.takeIf(String::isNotBlank)
        ?: "unknown"

internal const val KTOR_STARTUP_TYPE = ".KtorStartup"
internal const val KTOR_RESPONSE_TYPE = ".KtorResponse"
internal const val KTOR_ENDPOINT_ENVIRONMENT_VARIABLE = "KTOR_METRICS_ES_ENDPOINT"
internal const val BUILD_ENDPOINT_ENVIRONMENT_VARIABLE = "BUILD_METRICS_ES_ENDPOINT"
internal const val DEFAULT_ENDPOINT = "http://compilation-metrics/ktor"
internal const val DEFAULT_HTTP_TIMEOUT_MILLIS = 2_000L
private const val DEVELOPMENT_MODE_PROPERTY = "io.ktor.development"
