# Gradle build metrics plugin

Apply the settings-scoped plugin once from `settings.gradle.kts`:

```kotlin
plugins {
    id("io.agodadev.kotlin-local-metrics.build") version "<version>"
}
```

The plugin observes Gradle task completion through a shared build service and posts one
payload at the end of each local build. It records:

- task-execution wall-clock and summed task time;
- Kotlin, Java, and Kapt compilation time, including per-project aggregates;
- executed, up-to-date, cache-hit, and failed task counts;
- repository, branch, commit, host, operating system, IDE, and plugin version context.

Collection is skipped when `CI=true`, `GITLAB_CI`, or `CI_JOB_ID` is present. Metrics
are sent asynchronously with a two-second timeout, and collection failures never fail
the build.

The default endpoint is `http://compilation-metrics/gradle`. Override it with the
`BUILD_METRICS_ES_ENDPOINT` environment variable.
