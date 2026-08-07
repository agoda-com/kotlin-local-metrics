package io.agodadev.localmetrics.gradle

import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult

public abstract class BuildMetricsService :
    BuildService<BuildMetricsService.Parameters>,
    OperationCompletionListener,
    AutoCloseable {

    public interface Parameters : BuildServiceParameters {
        public val endpoint: Property<String>
        public val metricsVersion: Property<String>
        public val projectName: Property<String>
        public val rootDirectory: DirectoryProperty
        public val requestedTasks: ListProperty<String>
        public val timeoutMillis: Property<Int>

        /** Where payloads are parked while the metrics endpoint is unreachable. */
        public val bufferDirectory: DirectoryProperty
    }

    private val taskMetrics = ConcurrentHashMap<String, TaskMetric>()

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) {
            return
        }

        val result = event.result
        val outcome = when (result) {
            is TaskFailureResult -> TaskOutcome.FAILED
            is TaskSkippedResult -> TaskOutcome.SKIPPED
            is TaskSuccessResult -> when {
                result.isFromCache -> TaskOutcome.FROM_CACHE
                result.isUpToDate -> TaskOutcome.UP_TO_DATE
                else -> TaskOutcome.EXECUTED
            }
            else -> TaskOutcome.EXECUTED
        }

        val taskPath = event.descriptor.taskPath
        taskMetrics[taskPath] = TaskMetric(
            path = taskPath,
            startedAtMillis = result.startTime,
            finishedAtMillis = result.endTime,
            outcome = outcome,
            isCompilation = taskPath.isCompilationTaskPath(),
        )
    }

    override fun close() {
        if (taskMetrics.isEmpty()) {
            return
        }

        runCatching {
            val rootDirectory = parameters.rootDirectory.get().asFile
            val gitContext = GitContextReader.read(rootDirectory)
            val payload = BuildMetricsPayloadFactory.create(
                metricsVersion = parameters.metricsVersion.get(),
                projectName = parameters.projectName.get(),
                requestedTasks = parameters.requestedTasks.get(),
                taskMetrics = taskMetrics.values.toList(),
                gitContext = gitContext,
            )

            MetricsPublisher.post(
                endpoint = parameters.endpoint.get(),
                payloadId = payload.id,
                json = BuildMetricsJson.encode(payload),
                timeoutMillis = parameters.timeoutMillis.get(),
                buffer = parameters.bufferDirectory.orNull?.asFile?.let(::UnsentMetricsBuffer),
            )
        }
    }
}
