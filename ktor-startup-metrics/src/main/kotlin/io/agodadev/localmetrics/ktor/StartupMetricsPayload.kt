package io.agodadev.localmetrics.ktor

import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

internal data class StartupMetricsPayload(
    val id: String,
    val metricsVersion: String,
    val userName: String,
    val cpuCount: Int,
    val hostname: String,
    val platform: String,
    val os: String,
    val timeTaken: String,
    val branch: String?,
    val commitSha: String?,
    val type: String,
    val projectName: String,
    val repository: String?,
    val repositoryName: String?,
    val date: String,
    val tags: Map<String, String>,
    val isDebuggerAttached: Boolean,
)

internal object StartupMetricsPayloadFactory {
    fun create(
        metricsVersion: String,
        type: String,
        projectName: String,
        durationMillis: Long,
        gitContext: GitContext,
        environment: Map<String, String> = System.getenv(),
    ): StartupMetricsPayload = StartupMetricsPayload(
        id = UUID.randomUUID().toString(),
        metricsVersion = metricsVersion,
        userName = System.getProperty("user.name").orEmpty(),
        cpuCount = Runtime.getRuntime().availableProcessors(),
        hostname = hostName(environment),
        platform = System.getProperty("os.name").orEmpty(),
        os = listOfNotNull(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
        ).joinToString(" "),
        timeTaken = durationMillis.coerceAtLeast(0).toString(),
        branch = gitContext.branch,
        commitSha = gitContext.commitSha,
        type = type,
        projectName = projectName,
        repository = gitContext.repository,
        repositoryName = gitContext.repositoryName,
        date = Instant.now().toString(),
        tags = environment.entries
            .asSequence()
            .filter { (key, value) ->
                key.startsWith(DEVFEEDBACK_TAG_PREFIX, ignoreCase = true) &&
                    value.isNotBlank()
            }
            .associate { (key, value) ->
                key.substring(DEVFEEDBACK_TAG_PREFIX.length).lowercase() to value
            },
        isDebuggerAttached = ManagementFactory.getRuntimeMXBean().inputArguments
            .any { it.contains("jdwp", ignoreCase = true) },
    )

    private fun hostName(environment: Map<String, String>): String =
        environment["HOSTNAME"]
            ?: environment["COMPUTERNAME"]
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("")
}

private const val DEVFEEDBACK_TAG_PREFIX = "DEVFEEDBACK_TAG_"
