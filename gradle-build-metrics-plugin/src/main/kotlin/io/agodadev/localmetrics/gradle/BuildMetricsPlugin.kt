package io.agodadev.localmetrics.gradle

import javax.inject.Inject
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ProviderFactory

/**
 * Entry point for the settings-scoped build metrics plugin.
 */
public class BuildMetricsPlugin @Inject constructor(
    private val listenerRegistry: BuildEventsListenerRegistry,
    private val providers: ProviderFactory,
) : Plugin<Settings> {
    override fun apply(target: Settings) {
        if (isContinuousIntegration()) {
            return
        }

        val service = target.gradle.sharedServices.registerIfAbsent(
            SERVICE_NAME,
            BuildMetricsService::class.java,
        ) { serviceSpec ->
            serviceSpec.parameters.endpoint.set(
                providers.environmentVariable(ENDPOINT_ENVIRONMENT_VARIABLE)
                    .orElse(DEFAULT_ENDPOINT),
            )
            serviceSpec.parameters.metricsVersion.set(pluginVersion())
            serviceSpec.parameters.projectName.set(target.rootProject.name)
            serviceSpec.parameters.rootDirectory.set(target.rootDir)
            serviceSpec.parameters.requestedTasks.set(target.gradle.startParameter.taskNames)
            serviceSpec.parameters.timeoutMillis.set(HTTP_TIMEOUT_MILLIS)
        }

        listenerRegistry.onTaskCompletion(service)
    }

    private fun isContinuousIntegration(): Boolean =
        providers.environmentVariable("CI").orNull.equals("true", ignoreCase = true) ||
            providers.environmentVariable("GITLAB_CI").isPresent ||
            providers.environmentVariable("CI_JOB_ID").isPresent

    private fun pluginVersion(): String =
        BuildMetricsPlugin::class.java.`package`.implementationVersion ?: "development"

    private companion object {
        const val SERVICE_NAME = "kotlinLocalBuildMetrics"
        const val ENDPOINT_ENVIRONMENT_VARIABLE = "BUILD_METRICS_ES_ENDPOINT"
        const val DEFAULT_ENDPOINT = "http://compilation-metrics/gradle"
        const val HTTP_TIMEOUT_MILLIS = 2_000
    }
}
