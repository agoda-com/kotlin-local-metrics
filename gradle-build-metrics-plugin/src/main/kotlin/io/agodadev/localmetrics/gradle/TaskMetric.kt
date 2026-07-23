package io.agodadev.localmetrics.gradle

internal data class TaskMetric(
    val path: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val outcome: TaskOutcome,
    val isCompilation: Boolean,
) {
    val durationMillis: Long
        get() = (finishedAtMillis - startedAtMillis).coerceAtLeast(0)

    val projectPath: String
        get() = path.substringBeforeLast(":", missingDelimiterValue = ":").ifEmpty { ":" }
}

internal enum class TaskOutcome {
    EXECUTED,
    UP_TO_DATE,
    FROM_CACHE,
    SKIPPED,
    FAILED,
}

internal fun String.isCompilationTaskPath(): Boolean {
    val taskName = substringAfterLast(":")
    return taskName.startsWith("kapt", ignoreCase = true) ||
        (
            taskName.startsWith("compile", ignoreCase = true) &&
                (
                    taskName.contains("kotlin", ignoreCase = true) ||
                        taskName.contains("java", ignoreCase = true)
                )
        )
}
