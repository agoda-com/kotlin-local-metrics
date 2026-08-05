# kotlin-local-metrics

Local build and application startup metrics for Kotlin projects.

## The problem: you can't improve an F5 Experience you can't see

The **[F5 Experience](https://beerandserversdontmix.com/2024/08/15/an-introduction-to-the-f5-experience/)**
is the idea that setting up and running a project should take exactly three steps:
clone the repo, open it in your IDE, press F5. Everything after that — the compile,
the startup, the first response, the test run — is the **inner loop** of development,
and the speed of that loop largely determines how productive and how happy your
engineers are.

The trouble is that the inner loop is invisible. It happens hundreds of times a day
on each engineer's machine, and none of it shows up anywhere. So when a build slowly
creeps from 20 seconds to two minutes, nobody notices until developers have already
started
[context switching away every time they hit F5](https://beerandserversdontmix.com/2024/08/15/the-f5-experience-speed/)
— going for coffee, checking Slack, losing their flow state. By then the damage is
done and there's no data to explain when or why it happened. As the blog series puts
it: **measure first.** You can't optimize an inner loop you've never measured, and
compile time alone doesn't tell the whole story — the full cycle from pressing F5 to
a workable application is what actually matters.

This project instruments that inner loop on the developer's own machine. It captures
local Gradle build metrics and Ktor application startup metrics and ships them to a
metrics backend, so a team can see its real F5 Experience — build times, compilation
times, startup times, and the context around them — and drive it down over time
instead of guessing. Collection is deliberately local-only, asynchronous, and
best-effort: it never fails or slows the build or app it's measuring, and it skips
CI entirely.

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

- every push builds and tests all modules;
- non-`main` pushes exercise a JReleaser dry-run;
- `main` pushes stage and publish Maven Central artifacts.
