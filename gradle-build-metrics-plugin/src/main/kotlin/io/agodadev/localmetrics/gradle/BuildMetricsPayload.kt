package io.agodadev.localmetrics.gradle

import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

internal data class BuildMetricsPayload(
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
    val isDebuggerAttached: Boolean,
    val ide: String,
    val buildKind: String,
    val requestedTasks: List<String>,
    val taskCount: Int,
    val executedTaskCount: Int,
    val upToDateTaskCount: Int,
    val fromCacheTaskCount: Int,
    val failedTaskCount: Int,
    val compileTaskCount: Int,
    val compileTimeMs: Long,
    val taskTimeMs: Long,
    val projects: List<ProjectCompilationMetrics>,
)

internal data class ProjectCompilationMetrics(
    val projectPath: String,
    val compileTaskCount: Int,
    val compileTimeMs: Long,
)

internal object BuildMetricsPayloadFactory {
    fun create(
        metricsVersion: String,
        projectName: String,
        requestedTasks: List<String>,
        taskMetrics: List<TaskMetric>,
        gitContext: GitContext,
    ): BuildMetricsPayload {
        val compilationTasks = taskMetrics.filter(TaskMetric::isCompilation)
        val completedCompilationTasks = compilationTasks.filter { it.didWork() }
        val taskExecutionStart = taskMetrics.minOf(TaskMetric::startedAtMillis)
        val taskExecutionEnd = taskMetrics.maxOf(TaskMetric::finishedAtMillis)
        val upToDateTaskCount = taskMetrics.count { it.outcome == TaskOutcome.UP_TO_DATE }
        val fromCacheTaskCount = taskMetrics.count { it.outcome == TaskOutcome.FROM_CACHE }

        return BuildMetricsPayload(
            id = UUID.randomUUID().toString(),
            metricsVersion = metricsVersion,
            userName = System.getProperty("user.name").orEmpty(),
            cpuCount = Runtime.getRuntime().availableProcessors(),
            hostname = hostName(),
            platform = System.getProperty("os.name").orEmpty(),
            os = listOfNotNull(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
            ).joinToString(" "),
            timeTaken = (taskExecutionEnd - taskExecutionStart).coerceAtLeast(0).toString(),
            branch = gitContext.branch,
            commitSha = gitContext.commitSha,
            type = "Gradle",
            projectName = gitContext.repositoryName ?: projectName,
            repository = gitContext.repository,
            repositoryName = gitContext.repositoryName,
            date = Instant.now().toString(),
            isDebuggerAttached = ManagementFactory.getRuntimeMXBean().inputArguments
                .any { it.contains("jdwp", ignoreCase = true) },
            ide = if (System.getProperty("idea.active").toBoolean()) "IntelliJ IDEA" else "CLI",
            buildKind = if (upToDateTaskCount + fromCacheTaskCount > 0) "incremental" else "clean",
            requestedTasks = requestedTasks,
            taskCount = taskMetrics.size,
            executedTaskCount = taskMetrics.count { it.outcome == TaskOutcome.EXECUTED },
            upToDateTaskCount = upToDateTaskCount,
            fromCacheTaskCount = fromCacheTaskCount,
            failedTaskCount = taskMetrics.count { it.outcome == TaskOutcome.FAILED },
            compileTaskCount = compilationTasks.size,
            compileTimeMs = completedCompilationTasks.sumOf(TaskMetric::durationMillis),
            taskTimeMs = taskMetrics.sumOf(TaskMetric::durationMillis),
            projects = completedCompilationTasks
                .groupBy(TaskMetric::projectPath)
                .map { (path, tasks) ->
                    ProjectCompilationMetrics(
                        projectPath = path,
                        compileTaskCount = tasks.size,
                        compileTimeMs = tasks.sumOf(TaskMetric::durationMillis),
                    )
                }
                .sortedBy(ProjectCompilationMetrics::projectPath),
        )
    }

    private fun TaskMetric.didWork(): Boolean =
        outcome == TaskOutcome.EXECUTED || outcome == TaskOutcome.FAILED

    private fun hostName(): String =
        System.getenv("HOSTNAME")
            ?: System.getenv("COMPUTERNAME")
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("")
}
