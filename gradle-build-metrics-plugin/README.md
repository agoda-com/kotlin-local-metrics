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

## Offline buffering

When the endpoint cannot be reached — the developer is offline, the VPN is down, DNS
fails — the payload is written to
`<gradle user home>/kotlin-local-metrics/unsent-build-metrics` instead of being dropped.
The next build whose send succeeds flushes those payloads and deletes each one as it
is accepted, so builds done offline are not lost.

The buffer is bounded: at most 200 payloads, and nothing older than seven days. Older
entries beyond either cap are discarded. A payload that a reachable endpoint refuses is
discarded rather than retried. All buffer I/O is off the build's critical path and, like
the rest of collection, can never fail the build.
