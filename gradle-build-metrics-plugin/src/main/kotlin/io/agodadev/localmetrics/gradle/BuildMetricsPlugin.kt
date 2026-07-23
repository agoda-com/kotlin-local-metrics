package io.agodadev.localmetrics.gradle

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * Entry point for the settings-scoped build metrics plugin.
 *
 * Metric collection will be added after the project and publishing scaffold is in place.
 */
public class BuildMetricsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) = Unit
}
