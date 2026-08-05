package io.agodadev.localmetrics.ktor

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StartupMetricsPayloadTest {
    @Test
    fun `creates DevFeedback-compatible startup payload`() {
        val payload = StartupMetricsPayloadFactory.create(
            metricsVersion = "1.2.3",
            type = KTOR_STARTUP_TYPE,
            projectName = "sample-service",
            durationMillis = 4321,
            gitContext = GitContext(
                repository = "https://example.test/group/sample-service.git",
                repositoryName = "sample-service",
                branch = "feature/startup-metrics",
                commitSha = "abc123",
            ),
            environment = mapOf(
                "HOSTNAME" to "developer-machine",
                "DEVFEEDBACK_TAG_TEAM" to "supply",
                "devfeedback_tag_Region" to "apac",
                "DEVFEEDBACK_TAG_EMPTY" to "",
            ),
        )

        assertEquals("4321", payload.timeTaken)
        assertEquals(".KtorStartup", payload.type)
        assertEquals("sample-service", payload.projectName)
        assertEquals("developer-machine", payload.hostname)
        assertEquals(
            mapOf("team" to "supply", "region" to "apac"),
            payload.tags,
        )
    }

    @Test
    fun `encodes strings tags and nullable git fields as JSON`() {
        val payload = StartupMetricsPayloadFactory.create(
            metricsVersion = "dev\"build",
            type = KTOR_RESPONSE_TYPE,
            projectName = "sample\nservice",
            durationMillis = 1,
            gitContext = GitContext(null, null, null, null),
            environment = mapOf(
                "HOSTNAME" to "host",
                "DEVFEEDBACK_TAG_LABEL" to "a\"b",
            ),
        )

        val json = StartupMetricsJson.encode(payload)

        assertContains(json, "\"metricsVersion\":\"dev\\\"build\"")
        assertContains(json, "\"projectName\":\"sample\\nservice\"")
        assertContains(json, "\"branch\":null")
        assertContains(json, "\"repository\":null")
        assertContains(json, "\"tags\":{\"label\":\"a\\\"b\"}")
    }
}
