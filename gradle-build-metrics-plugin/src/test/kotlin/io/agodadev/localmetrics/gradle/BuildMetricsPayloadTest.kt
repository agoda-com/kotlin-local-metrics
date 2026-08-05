package io.agodadev.localmetrics.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildMetricsPayloadTest {
    @Test
    fun `recognizes Kotlin Java and Kapt compilation task paths`() {
        assertTrue(":compileKotlin".isCompilationTaskPath())
        assertTrue(":app:compileTestJava".isCompilationTaskPath())
        assertTrue(":app:compileDebugJavaWithJavac".isCompilationTaskPath())
        assertTrue(":app:kaptGenerateStubsKotlin".isCompilationTaskPath())

        assertFalse(":compileGroovy".isCompilationTaskPath())
        assertFalse(":test".isCompilationTaskPath())
    }

    @Test
    fun `aggregates execution and per-project compilation metrics`() {
        val payload = BuildMetricsPayloadFactory.create(
            metricsVersion = "1.2.3",
            projectName = "sample",
            requestedTasks = listOf("build"),
            taskMetrics = listOf(
                task(":app:compileKotlin", 100, 300, TaskOutcome.EXECUTED),
                task(":app:compileJava", 310, 330, TaskOutcome.UP_TO_DATE),
                task(":lib:kaptKotlin", 250, 400, TaskOutcome.FAILED),
                task(":test", 400, 450, TaskOutcome.EXECUTED),
            ),
            gitContext = GitContext(
                repository = "https://example.test/group/sample.git",
                repositoryName = "sample",
                branch = "feature/metrics",
                commitSha = "abc123",
            ),
        )

        assertEquals("350", payload.timeTaken)
        assertEquals(420, payload.taskTimeMs)
        assertEquals(2, payload.executedTaskCount)
        assertEquals(1, payload.upToDateTaskCount)
        assertEquals(1, payload.failedTaskCount)
        assertEquals(3, payload.compileTaskCount)
        assertEquals(350, payload.compileTimeMs)
        assertEquals("incremental", payload.buildKind)
        assertEquals(
            listOf(
                ProjectCompilationMetrics(":app", 1, 200),
                ProjectCompilationMetrics(":lib", 1, 150),
            ),
            payload.projects,
        )
    }

    @Test
    fun `encodes strings and nullable git fields as valid JSON values`() {
        val payload = BuildMetricsPayloadFactory.create(
            metricsVersion = "dev\"build",
            projectName = "sample\nproject",
            requestedTasks = emptyList(),
            taskMetrics = listOf(task(":help", 0, 1, TaskOutcome.EXECUTED)),
            gitContext = GitContext(null, null, null, null),
        )

        val json = BuildMetricsJson.encode(payload)

        assertContains(json, "\"metricsVersion\":\"dev\\\"build\"")
        assertContains(json, "\"projectName\":\"sample\\nproject\"")
        assertContains(json, "\"branch\":null")
        assertContains(json, "\"repository\":null")
    }

    private fun task(
        path: String,
        startedAtMillis: Long,
        finishedAtMillis: Long,
        outcome: TaskOutcome,
    ): TaskMetric = TaskMetric(
        path = path,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        outcome = outcome,
        isCompilation = path.isCompilationTaskPath(),
    )
}
