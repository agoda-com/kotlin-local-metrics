# kotlin-local-metrics

Local build and application startup metrics for Kotlin projects.

The Gradle build metrics plugin from
[issue #1](https://github.com/agoda-com/kotlin-local-metrics/issues/1) is implemented.

JUnit 5 test metrics are provided by
[java-local-metrics](https://github.com/agoda-com/java-local-metrics).

## Modules

- `metrics-core`: shared metrics payloads and transport
- `gradle-build-metrics-plugin`: settings-scoped local build metrics (implemented)
- `ktor-startup-metrics`: Ktor application startup metrics (implemented)

## Gradle build metrics

Apply the plugin once from `settings.gradle.kts`:

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
or delay the build.

The default endpoint is `http://compilation-metrics/gradle`. Override it with the
`BUILD_METRICS_ES_ENDPOINT` environment variable.

## Ktor startup metrics

Add the module to a Ktor server and install the application plugin:

```kotlin
dependencies {
    implementation("io.agodadev:ktor-startup-metrics:<version>")
}
```

```kotlin
fun Application.module() {
    install(StartupMetrics) {
        projectName = "my-service"
    }
}
```

By default the plugin runs only when Ktor development mode is enabled. It reports
`.KtorStartup` when the application starts and `.KtorResponse` after the first response
is sent. Both durations use JVM start time as their baseline, so they include classpath
loading and dependency-injection setup.

The plugin supports Ktor 2.3 and Ktor 3.x. Its default endpoint is
`http://compilation-metrics/ktor`. Override it with `KTOR_METRICS_ES_ENDPOINT`; the
shared `BUILD_METRICS_ES_ENDPOINT` override is also honored as a fallback. Publishing is
asynchronous, has a two-second timeout, and never fails an application startup or
request. `DEVFEEDBACK_TAG_*` environment variables are included in the payload; the
prefix is removed and tag names are lowercased.

Collection can be explicitly configured when needed:

```kotlin
install(StartupMetrics) {
    enabled = true
    endpoint = "http://localhost:8081/metrics"
    timeoutMillis = 1_000
    projectName = "my-service"
}
```

## Build

The project requires a Java 17 or newer runtime to run Gradle and produces Java 11
compatible artifacts.

```shell
./gradlew build
```

## Publishing

The GitHub Actions workflow follows the release setup from
[java-local-metrics](https://github.com/agoda-com/java-local-metrics):

- every push builds and tests all modules;
- non-`main` pushes exercise a JReleaser dry-run;
- `main` pushes stage and publish Maven Central artifacts.
